#!/usr/bin/env python3
"""
Red Pitaya acquisition agent.

Run this on the Red Pitaya:

    python3 rp_prpd_agent.py --host 0.0.0.0 --port 9999

The PC GUI connects to this process, sends one JSON acquisition request, and
receives framed RAW int16 ADC data.
"""

from __future__ import annotations

import argparse
import json
import socket
import struct
import time
import zlib
from datetime import datetime

import numpy as np


ADC_BASE_RATE = 125_000_000.0
BOARD_MODEL = "STEMlab 125-14 Pro Z7020 Gen 2"
DEFAULT_PORT = 9999
FRAME_MAGIC = b"RPDF"
VERSION = 1
FRAME_HEADER = struct.Struct("<4sHHIHdIII")


class AgentError(RuntimeError):
    pass


def recv_json_line(sock: socket.socket) -> dict:
    line = bytearray()
    while True:
        part = sock.recv(1)
        if not part:
            raise ConnectionError("socket closed while receiving JSON")
        if part == b"\n":
            break
        line.extend(part)
        if len(line) > 1_000_000:
            raise AgentError("JSON request is too large")
    return json.loads(line.decode("utf-8"))


def send_json_line(sock: socket.socket, payload: dict) -> None:
    sock.sendall(json.dumps(payload, separators=(",", ":")).encode("utf-8") + b"\n")


def rp_channel(rp, channel_id: int):
    return getattr(rp, f"RP_CH_{channel_id}")


def trigger_channel(rp, channel_id: int):
    return getattr(rp, f"RP_T_CH_{channel_id}")


def gain_value(rp, gain: str):
    gain = gain.upper()
    candidates = {
        "LV": ["RP_LOW", "RP_LV"],
        "HV": ["RP_HIGH", "RP_HV"],
    }[gain]
    for name in candidates:
        if hasattr(rp, name):
            return getattr(rp, name)
    return gain


def trigger_value(rp, source: str):
    mapping = {
        "NOW": "RP_TRIG_SRC_NOW",
        "CH1_PE": "RP_TRIG_SRC_CHA_PE",
        "CH1_NE": "RP_TRIG_SRC_CHA_NE",
        "CH2_PE": "RP_TRIG_SRC_CHB_PE",
        "CH2_NE": "RP_TRIG_SRC_CHB_NE",
        "EXT_PE": "RP_TRIG_SRC_EXT_PE",
        "EXT_NE": "RP_TRIG_SRC_EXT_NE",
    }
    name = mapping[source]
    if not hasattr(rp, name):
        raise AgentError(f"trigger source is not supported by this RP API: {source}")
    return getattr(rp, name)


def validate_config(config: dict) -> dict:
    if config.get("command") != "acquire":
        raise AgentError("unsupported command")

    channels = [int(ch) for ch in config.get("channels", [])]
    if not channels or any(ch not in (1, 2) for ch in channels):
        raise AgentError("channels must be [1], [2], or [1, 2]")

    decimation = int(config["decimation"])
    if decimation < 1 or decimation > 65536:
        raise AgentError("decimation must be in range 1..65536")

    total_samples = int(config["total_samples"])
    frame_size = int(config["frame_size"])
    frame_count = int(config["frame_count"])
    if total_samples < 2 or frame_size < 1 or frame_count < 1:
        raise AgentError("sample counts must be positive")
    if total_samples % 2:
        total_samples += 1
    if frame_size % 2:
        frame_size += 1
    frame_count = (total_samples + frame_size - 1) // frame_size

    trigger = str(config.get("trigger_source", "NOW")).upper()
    allowed_triggers = {"NOW", "CH1_PE", "CH1_NE", "CH2_PE", "CH2_NE", "EXT_PE", "EXT_NE"}
    if trigger not in allowed_triggers:
        raise AgentError(f"unsupported trigger source: {trigger}")

    raw_gains = config.get("gains")
    if isinstance(raw_gains, dict):
        gains = {
            1: str(raw_gains.get("1", raw_gains.get(1, "LV"))).upper(),
            2: str(raw_gains.get("2", raw_gains.get(2, "LV"))).upper(),
        }
    else:
        gain = str(config.get("gain", "LV")).upper()
        gains = {1: gain, 2: gain}
    for ch, gain in gains.items():
        if gain not in {"LV", "HV"}:
            raise AgentError(f"gain for CH{ch} must be LV or HV")

    checked = dict(config)
    checked.update(
        {
            "channels": channels,
            "gain": gains[channels[0]],
            "gains": {str(ch): gains[ch] for ch in (1, 2)},
            "board_model": str(config.get("board_model") or BOARD_MODEL),
            "decimation": decimation,
            "trigger_source": trigger,
            "trigger_level": float(config.get("trigger_level", 0.0)),
            "trigger_delay": int(config.get("trigger_delay", 0)),
            "trigger_timeout_s": float(config.get("trigger_timeout_s", 10.0)),
            "averaging": bool(config.get("averaging", False)),
            "total_samples": total_samples,
            "frame_size": frame_size,
            "frame_count": frame_count,
            "sample_rate": ADC_BASE_RATE / decimation,
            "dtype": "int16",
            "units": "RAW",
        }
    )
    return checked


def check_status(name: str, result) -> None:
    if isinstance(result, (tuple, list)):
        code = result[0]
    else:
        code = result
    if code not in (0, None):
        raise AgentError(f"{name} failed with status {code}")


def configure_dma(rp, config: dict) -> tuple[dict, dict[int, int]]:
    memory = rp.rp_AcqAxiGetMemoryRegion()
    check_status("rp_AcqAxiGetMemoryRegion", memory)
    axi_start = int(memory[1])
    axi_size = int(memory[2])

    channels = list(config["channels"])
    bytes_per_sample = 2
    margin = 4096
    usable_bytes = max(0, axi_size - margin)
    per_channel_bytes = usable_bytes // len(channels)
    max_samples_per_channel = per_channel_bytes // bytes_per_sample
    total_samples = int(config["total_samples"])
    required_bytes = total_samples * len(channels) * bytes_per_sample
    dma_trigger_delay = int(config["trigger_delay"])
    if dma_trigger_delay == 0:
        dma_trigger_delay = total_samples

    if total_samples > max_samples_per_channel:
        raise AgentError(
            "requested acquisition does not fit AXI DMA memory: "
            f"required={required_bytes} bytes, available={usable_bytes} bytes, "
            f"max_samples_per_channel={max_samples_per_channel}"
        )

    check_status("rp_AcqAxiSetDecimationFactor", rp.rp_AcqAxiSetDecimationFactor(int(config["decimation"])))

    if hasattr(rp, "rp_AcqSetAveraging"):
        check_status("rp_AcqSetAveraging", rp.rp_AcqSetAveraging(bool(config["averaging"])))

    gains = config["gains"]
    for ch in channels:
        rch = rp_channel(rp, ch)
        if hasattr(rp, "rp_AcqSetGain"):
            check_status(f"rp_AcqSetGain CH{ch}", rp.rp_AcqSetGain(rch, gain_value(rp, gains[str(ch)])))
        if hasattr(rp, "rp_AcqAxiSetTriggerDelay"):
            check_status(
                f"rp_AcqAxiSetTriggerDelay CH{ch}",
                rp.rp_AcqAxiSetTriggerDelay(rch, dma_trigger_delay),
            )

    buffer_starts: dict[int, int] = {}
    for index, ch in enumerate(channels):
        rch = rp_channel(rp, ch)
        start = axi_start + index * per_channel_bytes
        buffer_starts[ch] = start
        check_status(f"rp_AcqAxiSetBufferSamples CH{ch}", rp.rp_AcqAxiSetBufferSamples(rch, start, total_samples))
        check_status(f"rp_AcqAxiEnable CH{ch}", rp.rp_AcqAxiEnable(rch, True))

    metadata = dict(config)
    metadata.update(
        {
            "agent": "redpitaya-python-api",
            "agent_board_default": BOARD_MODEL,
            "rp_timestamp": datetime.now().isoformat(timespec="seconds"),
            "axi_start": axi_start,
            "axi_size": axi_size,
            "axi_usable_bytes": usable_bytes,
            "axi_per_channel_bytes": per_channel_bytes,
            "buffer_starts": buffer_starts,
            "dma_trigger_delay": dma_trigger_delay,
        }
    )
    return metadata, buffer_starts


def wait_for_trigger_and_fill(rp, config: dict) -> None:
    source = trigger_value(rp, config["trigger_source"])

    if config["trigger_source"].startswith("CH1"):
        rp.rp_AcqSetTriggerLevel(trigger_channel(rp, 1), float(config["trigger_level"]))
    elif config["trigger_source"].startswith("CH2"):
        rp.rp_AcqSetTriggerLevel(trigger_channel(rp, 2), float(config["trigger_level"]))

    check_status("rp_AcqStart", rp.rp_AcqStart())
    time.sleep(0.05)
    check_status("rp_AcqSetTriggerSrc", rp.rp_AcqSetTriggerSrc(source))

    trigger_timeout_s = float(config["trigger_timeout_s"])
    acquisition_s = float(config["total_samples"]) / float(config["sample_rate"])
    deadline = time.monotonic() + trigger_timeout_s
    triggered_state = getattr(rp, "RP_TRIG_STATE_TRIGGERED", None)
    while True:
        if time.monotonic() > deadline:
            raise AgentError("trigger timeout")
        state = rp.rp_AcqGetTriggerState()[1]
        if triggered_state is None or state == triggered_state:
            break
        time.sleep(0.001)

    channels = [rp_channel(rp, int(ch)) for ch in config["channels"]]
    fill_deadline = time.monotonic() + max(trigger_timeout_s, acquisition_s * 3.0 + 1.0)
    while True:
        if time.monotonic() > fill_deadline:
            raise AgentError(
                "DMA fill timeout "
                f"(samples={config['total_samples']}, fs={config['sample_rate']:.3f} Hz, "
                f"expected_acquisition={acquisition_s:.3f} s)"
            )
        states = [bool(rp.rp_AcqAxiGetBufferFillState(ch)[1]) for ch in channels]
        if all(states):
            break
        time.sleep(0.001)

    check_status("rp_AcqStop", rp.rp_AcqStop())


def read_channel_chunk(rp, rp_ch, pos: int, size: int) -> np.ndarray:
    arr = np.zeros(size, dtype=np.int16)
    if hasattr(rp, "rp_AcqAxiGetDataRawNP"):
        check_status("rp_AcqAxiGetDataRawNP", rp.rp_AcqAxiGetDataRawNP(rp_ch, pos, arr))
        return arr

    buff = rp.i16Buffer(size)
    try:
        check_status("rp_AcqAxiGetDataRaw", rp.rp_AcqAxiGetDataRaw(rp_ch, pos, size, buff.cast()))
        for i in range(size):
            arr[i] = buff[i]
        return arr
    finally:
        # Older APIs expose i16Buffer without an explicit free function.
        pass


def send_frames(sock: socket.socket, rp, config: dict, buffer_starts: dict[int, int]) -> None:
    channels = list(config["channels"])
    rp_channels = [rp_channel(rp, ch) for ch in channels]
    pointers = {ch: int(rp.rp_AcqAxiGetWritePointer(rp_channel(rp, ch))[1]) for ch in channels}
    total_samples = int(config["total_samples"])
    frame_size = int(config["frame_size"])
    frame_count = int(config["frame_count"])
    fs = float(config["sample_rate"])
    buf_size_bytes = total_samples * 2

    sent = 0
    for seq in range(frame_count):
        n = min(frame_size, total_samples - sent)
        if n <= 0:
            break
        data = np.empty((n, len(channels)), dtype="<i2")
        for index, ch in enumerate(channels):
            start_addr = buffer_starts[ch]
            read_addr = pointers[ch] + sent * 2
            
            if read_addr >= start_addr + buf_size_bytes:
                read_addr -= buf_size_bytes
                
            bytes_to_end = start_addr + buf_size_bytes - read_addr
            samples_to_end = bytes_to_end // 2
            
            if samples_to_end >= n:
                arr = read_channel_chunk(rp, rp_channels[index], read_addr, n)
            else:
                arr = np.empty(n, dtype=np.int16)
                arr[:samples_to_end] = read_channel_chunk(rp, rp_channels[index], read_addr, samples_to_end)
                arr[samples_to_end:] = read_channel_chunk(rp, rp_channels[index], start_addr, n - samples_to_end)
                
            data[:, index] = arr.astype("<i2", copy=False)
        payload = data.tobytes(order="C")
        header = FRAME_HEADER.pack(
            FRAME_MAGIC,
            VERSION,
            FRAME_HEADER.size,
            seq,
            len(channels),
            fs,
            n,
            len(payload),
            zlib.crc32(payload) & 0xFFFFFFFF,
        )
        sock.sendall(header + payload)
        sent += n


def handle_client(conn: socket.socket, addr) -> None:
    import rp

    print(f"Client connected: {addr}")
    config = validate_config(recv_json_line(conn))
    initialized = False
    enabled_channels: list[int] = []
    try:
        check_status("rp_Init", rp.rp_Init())
        initialized = True
        metadata, _starts = configure_dma(rp, config)
        enabled_channels = list(config["channels"])
        wait_for_trigger_and_fill(rp, config)
        send_json_line(conn, {"ok": True, "metadata": metadata})
        send_frames(conn, rp, config, _starts)
        print("Acquisition sent")
    except Exception as exc:
        try:
            send_json_line(conn, {"ok": False, "error": str(exc)})
        except Exception:
            pass
        raise
    finally:
        for ch in enabled_channels:
            try:
                rp.rp_AcqAxiEnable(rp_channel(rp, ch), False)
            except Exception:
                pass
        if initialized:
            try:
                rp.rp_Release()
            except Exception:
                pass


def run_server(host: str, port: int) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind((host, port))
        server.listen(1)
        print(f"Red Pitaya PRPD agent listening on {host}:{port}")
        print(f"Board profile: {BOARD_MODEL}")
        while True:
            conn, addr = server.accept()
            with conn:
                try:
                    handle_client(conn, addr)
                except Exception as exc:
                    print(f"Client error: {exc}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Red Pitaya PRPD Data Acquisition Agent")
    parser.add_argument("--bind-address", default="0.0.0.0", 
                        help="IPv4 address to bind the listening socket to (default: 0.0.0.0 - all interfaces)")
    parser.add_argument("--listen-port", type=int, default=DEFAULT_PORT, 
                        help="TCP port to listen for incoming GUI connections")
    args = parser.parse_args()
    
    run_server(args.bind_address, args.listen_port)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
