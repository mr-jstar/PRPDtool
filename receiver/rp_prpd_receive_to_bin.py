#!/usr/bin/env python3
"""
PC GUI for framed Red Pitaya acquisitions.

Default mode starts a PySide6 GUI. Use --mock-agent to run a local test
server that behaves like the Red Pitaya agent without touching hardware.
"""

from __future__ import annotations

import argparse
import html
import json
import math
import os
import socket
import struct
import sys
import tempfile
import threading
import time
import zlib
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import BinaryIO, Iterable

import numpy as np


ADC_BASE_RATE = 125_000_000.0
BOARD_MODEL = "STEMlab 125-14 Pro Z7020 Gen 2"
DEFAULT_PORT = 9999
REPO_ROOT = Path(__file__).resolve().parents[1]
RECEIVED_DIR = REPO_ROOT / "data" / "received_bin"

FILE_MAGIC = b"RPPR"
FRAME_MAGIC = b"RPDF"
VERSION = 1

# file: magic, version, metadata_json_length
FILE_HEADER = struct.Struct("<4sHI")

# frame: magic, version, header_size, seq, channel_count, sample_rate,
#        sample_count_per_channel, payload_len, crc32
FRAME_HEADER = struct.Struct("<4sHHIHdIII")


class ProtocolError(RuntimeError):
    pass


def recv_exact(sock: socket.socket, size: int) -> bytes:
    chunks = bytearray()
    while len(chunks) < size:
        part = sock.recv(size - len(chunks))
        if not part:
            raise ConnectionError("socket closed while receiving data")
        chunks.extend(part)
    return bytes(chunks)


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
            raise ProtocolError("JSON response is too large")
    return json.loads(line.decode("utf-8"))


def send_json_line(sock: socket.socket, payload: dict) -> None:
    raw = json.dumps(payload, separators=(",", ":")).encode("utf-8") + b"\n"
    sock.sendall(raw)


def sample_rate_for_decimation(decimation: int) -> float:
    return ADC_BASE_RATE / decimation


def channel_ids(label: str) -> list[int]:
    if label == "IN1":
        return [1]
    if label == "IN2":
        return [2]
    return [1, 2]


def channel_suffix(channels: Iterable[int]) -> str:
    return "ch" + "".join(str(ch) for ch in channels)


def build_output_path(metadata: dict) -> Path:
    RECEIVED_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
    dec = int(metadata["decimation"])
    suffix = channel_suffix(metadata["channels"])
    return RECEIVED_DIR / f"rp_{stamp}_dec{dec}_raw_{suffix}.rppr.bin"


def write_file_header(f: BinaryIO, metadata: dict) -> None:
    raw = json.dumps(metadata, indent=2, sort_keys=True).encode("utf-8")
    f.write(FILE_HEADER.pack(FILE_MAGIC, VERSION, len(raw)))
    f.write(raw)


def read_file_header(f: BinaryIO) -> dict:
    raw_header = f.read(FILE_HEADER.size)
    if len(raw_header) != FILE_HEADER.size:
        raise ProtocolError("file is too short")
    magic, version, metadata_len = FILE_HEADER.unpack(raw_header)
    if magic != FILE_MAGIC:
        raise ProtocolError("not an RPPR file")
    if version != VERSION:
        raise ProtocolError(f"unsupported RPPR version: {version}")
    raw_metadata = f.read(metadata_len)
    if len(raw_metadata) != metadata_len:
        raise ProtocolError("truncated metadata")
    return json.loads(raw_metadata.decode("utf-8"))


@dataclass(frozen=True)
class FrameInfo:
    seq: int
    channel_count: int
    sample_rate: float
    sample_count: int
    payload_len: int
    crc32: int
    header_offset: int
    payload_offset: int


def read_frame_header(f: BinaryIO) -> FrameInfo | None:
    offset = f.tell()
    raw = f.read(FRAME_HEADER.size)
    if not raw:
        return None
    if len(raw) != FRAME_HEADER.size:
        raise ProtocolError("truncated frame header")

    magic, version, header_size, seq, channel_count, sample_rate, sample_count, payload_len, crc32 = FRAME_HEADER.unpack(raw)
    if magic != FRAME_MAGIC:
        raise ProtocolError(f"bad frame magic at offset {offset}")
    if version != VERSION:
        raise ProtocolError(f"unsupported frame version: {version}")
    if header_size != FRAME_HEADER.size:
        raise ProtocolError(f"bad frame header size: {header_size}")

    return FrameInfo(
        seq=seq,
        channel_count=channel_count,
        sample_rate=sample_rate,
        sample_count=sample_count,
        payload_len=payload_len,
        crc32=crc32,
        header_offset=offset,
        payload_offset=f.tell(),
    )


def iter_frames(path: Path) -> tuple[dict, list[FrameInfo]]:
    frames: list[FrameInfo] = []
    with path.open("rb") as f:
        metadata = read_file_header(f)
        while True:
            info = read_frame_header(f)
            if info is None:
                break
            f.seek(info.payload_len, os.SEEK_CUR)
            frames.append(info)
    return metadata, frames


def validate_frame_payload(info: FrameInfo, payload: bytes) -> None:
    expected = info.sample_count * info.channel_count * np.dtype("<i2").itemsize
    if info.payload_len != expected:
        raise ProtocolError(f"bad payload size in frame {info.seq}: {info.payload_len} != {expected}")
    crc = zlib.crc32(payload) & 0xFFFFFFFF
    if crc != info.crc32:
        raise ProtocolError(f"CRC mismatch in frame {info.seq}: {crc:08x} != {info.crc32:08x}")


def receive_frame(sock: socket.socket) -> tuple[FrameInfo, bytes]:
    raw_header = recv_exact(sock, FRAME_HEADER.size)
    magic, version, header_size, seq, channel_count, sample_rate, sample_count, payload_len, crc32 = FRAME_HEADER.unpack(raw_header)
    if magic != FRAME_MAGIC:
        raise ProtocolError("bad frame magic from agent")
    if version != VERSION:
        raise ProtocolError(f"unsupported frame version from agent: {version}")
    if header_size != FRAME_HEADER.size:
        raise ProtocolError(f"bad frame header size from agent: {header_size}")

    payload = recv_exact(sock, payload_len)
    info = FrameInfo(
        seq=seq,
        channel_count=channel_count,
        sample_rate=sample_rate,
        sample_count=sample_count,
        payload_len=payload_len,
        crc32=crc32,
        header_offset=-1,
        payload_offset=-1,
    )
    validate_frame_payload(info, payload)
    return info, payload


def write_frame(f: BinaryIO, info: FrameInfo, payload: bytes) -> None:
    header = FRAME_HEADER.pack(
        FRAME_MAGIC,
        VERSION,
        FRAME_HEADER.size,
        info.seq,
        info.channel_count,
        info.sample_rate,
        info.sample_count,
        len(payload),
        zlib.crc32(payload) & 0xFFFFFFFF,
    )
    f.write(header)
    f.write(payload)


def build_config_from_values(
    host: str,
    port: int,
    channels: list[int],
    gains: dict[int, str],
    decimation: int,
    averaging: bool,
    trigger_source: str,
    trigger_level: float,
    trigger_delay: int,
    acquisition_mode: str,
    duration_s: float,
    frame_size: int,
    frame_count: int,
    trigger_timeout_s: float,
) -> dict:
    if not channels:
        raise ValueError("wybierz co najmniej jeden kanał")
    if decimation < 1 or decimation > 65536:
        raise ValueError("decymacja musi być w zakresie 1..65536")
    if frame_size < 1:
        raise ValueError("rozmiar ramki musi być dodatni")
    if frame_count < 1:
        raise ValueError("liczba ramek musi być dodatnia")
    normalized_gains = {int(ch): str(gain).upper() for ch, gain in gains.items()}
    for ch in (1, 2):
        normalized_gains.setdefault(ch, "LV")
    for ch, gain in normalized_gains.items():
        if gain not in {"LV", "HV"}:
            raise ValueError(f"zakres IN{ch} musi mieć wartość LV albo HV")

    fs = sample_rate_for_decimation(decimation)
    if acquisition_mode == "duration":
        if duration_s <= 0:
            raise ValueError("czas akwizycji musi być dodatni")
        total_samples = max(1, int(round(duration_s * fs)))
        if total_samples % 2:
            total_samples += 1
        frame_count = int(math.ceil(total_samples / frame_size))
    else:
        total_samples = frame_size * frame_count
        if total_samples % 2:
            total_samples += 1
        duration_s = total_samples / fs

    if frame_size % 2:
        frame_size += 1
        frame_count = int(math.ceil(total_samples / frame_size))

    return {
        "command": "acquire",
        "client_host": host,
        "client_port": port,
        "channels": channels,
        "gains": {str(ch): normalized_gains[ch] for ch in (1, 2)},
        "gain": normalized_gains[channels[0]],
        "board_model": BOARD_MODEL,
        "decimation": decimation,
        "averaging": bool(averaging),
        "trigger_source": trigger_source,
        "trigger_level": trigger_level,
        "trigger_delay": trigger_delay,
        "trigger_timeout_s": trigger_timeout_s,
        "acquisition_mode": acquisition_mode,
        "duration_s": duration_s,
        "frame_size": frame_size,
        "frame_count": frame_count,
        "total_samples": total_samples,
        "sample_rate": fs,
        "dtype": "int16",
        "units": "RAW",
    }


def acquire_to_file(config: dict, progress_cb=None, status_cb=None) -> Path:
    host = config["client_host"]
    port = int(config["client_port"])
    if status_cb:
        status_cb(f"Łączenie z {host}:{port}")

    with socket.create_connection((host, port), timeout=10) as sock:
        sock.settimeout(None)
        send_json_line(sock, config)
        response = recv_json_line(sock)
        if not response.get("ok"):
            raise RuntimeError(response.get("error", "agent rejected acquisition"))

        metadata = dict(response.get("metadata") or {})
        metadata.update(
            {
                "host": host,
                "port": port,
                "pc_timestamp": datetime.now().isoformat(timespec="seconds"),
                "format": "RPPR",
                "format_version": VERSION,
            }
        )
        out_path = build_output_path(metadata)
        expected_frames = int(metadata["frame_count"])
        expected_total = int(metadata["total_samples"])
        received_samples = 0

        if status_cb:
            status_cb(f"Odbieranie ramek: {expected_frames}")
        with out_path.open("wb") as f:
            write_file_header(f, metadata)
            for frame_index in range(expected_frames):
                info, payload = receive_frame(sock)
                write_frame(f, info, payload)
                received_samples += info.sample_count
                if progress_cb:
                    progress_cb(frame_index + 1, expected_frames, received_samples, expected_total)

    if status_cb:
        status_cb(f"Zapisano {out_path}")
    return out_path


def load_rppr_for_plot(path: Path, max_points: int = 600_000) -> tuple[dict, np.ndarray, np.ndarray]:
    metadata, frames = iter_frames(path)
    if not frames:
        raise ProtocolError("file has no frames")

    channel_count = int(frames[0].channel_count)
    sample_rate = float(frames[0].sample_rate)
    total_samples = sum(frame.sample_count for frame in frames)
    stride = max(1, int(math.ceil(total_samples / max_points)))

    parts: list[np.ndarray] = []
    with path.open("rb") as f:
        for frame in frames:
            f.seek(frame.payload_offset)
            payload = f.read(frame.payload_len)
            validate_frame_payload(frame, payload)
            arr = np.frombuffer(payload, dtype="<i2").reshape(frame.sample_count, channel_count)
            parts.append(arr[::stride].copy())

    data = np.vstack(parts)
    t = (np.arange(data.shape[0], dtype=np.float64) * stride) / sample_rate
    metadata = dict(metadata)
    metadata["plot_stride"] = stride
    metadata["plot_points"] = int(data.shape[0])
    return metadata, t, data


def export_prpdtool_double(input_path: Path, output_path: Path, channel: int = 1) -> None:
    metadata, frames = iter_frames(input_path)
    if not frames:
        raise ProtocolError("file has no frames")

    channels = list(metadata.get("channels") or [1])
    if channel not in channels:
        raise ValueError(f"channel IN{channel} is not present in this file")
    channel_index = channels.index(channel)
    sample_rate = float(metadata["sample_rate"])
    sample_offset = 0

    with input_path.open("rb") as src, output_path.open("wb") as dst:
        for frame in frames:
            src.seek(frame.payload_offset)
            payload = src.read(frame.payload_len)
            validate_frame_payload(frame, payload)
            arr = np.frombuffer(payload, dtype="<i2").reshape(frame.sample_count, frame.channel_count)
            u = arr[:, channel_index].astype(np.float64)
            t = (np.arange(frame.sample_count, dtype=np.float64) + sample_offset) / sample_rate
            pairs = np.empty(frame.sample_count * 2, dtype="<f8")
            pairs[0::2] = t
            pairs[1::2] = u
            dst.write(pairs.tobytes())
            sample_offset += frame.sample_count


def run_mock_agent(host: str, port: int) -> None:
    print(f"Mock agent listening on {host}:{port}")
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind((host, port))
        server.listen(1)
        while True:
            conn, addr = server.accept()
            with conn:
                print("Client:", addr)
                try:
                    config = recv_json_line(conn)
                    metadata = dict(config)
                    metadata.update(
                        {
                            "agent": "mock",
                            "rp_timestamp": datetime.now().isoformat(timespec="seconds"),
                            "available_memory_bytes": 512 * 1024 * 1024,
                        }
                    )
                    send_json_line(conn, {"ok": True, "metadata": metadata})

                    channels = list(metadata["channels"])
                    channel_count = len(channels)
                    fs = float(metadata["sample_rate"])
                    total = int(metadata["total_samples"])
                    frame_size = int(metadata["frame_size"])
                    frame_count = int(metadata["frame_count"])

                    sent = 0
                    rng = np.random.default_rng()
                    for seq in range(frame_count):
                        n = min(frame_size, total - sent)
                        if n <= 0:
                            break
                        x = (np.arange(n, dtype=np.float64) + sent) / fs
                        data = np.zeros((n, channel_count), dtype="<i2")
                        for idx, ch in enumerate(channels):
                            wave = 12000.0 * np.sin(2.0 * np.pi * (1000.0 * ch) * x)
                            noise = rng.normal(0.0, 350.0, size=n)
                            impulses = np.zeros(n)
                            if n > 20:
                                impulses[:: max(20, n // 16)] = 18000.0
                            data[:, idx] = np.clip(wave + noise + impulses, -32768, 32767).astype("<i2")
                        payload = data.tobytes(order="C")
                        header = FRAME_HEADER.pack(
                            FRAME_MAGIC,
                            VERSION,
                            FRAME_HEADER.size,
                            seq,
                            channel_count,
                            fs,
                            n,
                            len(payload),
                            zlib.crc32(payload) & 0xFFFFFFFF,
                        )
                        conn.sendall(header + payload)
                        sent += n
                        time.sleep(0.02)
                except Exception as exc:
                    print("Mock agent error:", exc)


def launch_gui() -> int:
    try:
        from PySide6 import QtCore, QtGui, QtWidgets
        import pyqtgraph as pg
    except ImportError as exc:
        print("Brak zależności GUI:", exc)
        print("Utwórz venv i zainstaluj: pip install -r receiver/requirements.txt")
        return 2

    class AcquisitionWorker(QtCore.QObject):
        progress = QtCore.Signal(int, int, int, int)
        status = QtCore.Signal(str)
        finished = QtCore.Signal(str)
        failed = QtCore.Signal(str)

        def __init__(self, config: dict):
            super().__init__()
            self.config = config

        @QtCore.Slot()
        def run(self) -> None:
            try:
                path = acquire_to_file(self.config, self.progress.emit, self.status.emit)
                self.finished.emit(str(path))
            except Exception as exc:
                self.failed.emit(str(exc))

    class MainWindow(QtWidgets.QMainWindow):
        def __init__(self):
            super().__init__()
            self.setWindowTitle("Odbiornik PRPD Red Pitaya")
            self.resize(1320, 860)
            self.thread: QtCore.QThread | None = None
            self.worker: AcquisitionWorker | None = None
            self.current_path: Path | None = None
            self.current_metadata: dict | None = None
            self.current_t: np.ndarray | None = None
            self.current_data: np.ndarray | None = None
            self.trigger_calibrator_process: QtCore.QProcess | None = None
            self.trigger_calibrator_config_path: Path | None = None
            self.trigger_calibrator_result_path: Path | None = None

            root = QtWidgets.QWidget()
            self.setCentralWidget(root)
            layout = QtWidgets.QHBoxLayout(root)

            controls_scroll = QtWidgets.QScrollArea()
            controls_scroll.setWidgetResizable(True)
            controls_scroll.setMinimumWidth(430)
            controls_scroll.setMaximumWidth(520)
            controls = QtWidgets.QWidget()
            controls_layout = QtWidgets.QVBoxLayout(controls)
            controls_layout.setContentsMargins(10, 10, 10, 10)
            controls_layout.setSpacing(10)
            controls_scroll.setWidget(controls)

            self.host = QtWidgets.QLineEdit("rp-f0f84e.local")
            self.port = QtWidgets.QSpinBox()
            self.port.setRange(1, 65535)
            self.port.setValue(DEFAULT_PORT)
            self.channels = QtWidgets.QComboBox()
            self.channels.addItems(["IN1+IN2", "IN1", "IN2"])
            self.gain_ch1 = QtWidgets.QComboBox()
            self.gain_ch1.addItems(["LV", "HV"])
            self.gain_ch2 = QtWidgets.QComboBox()
            self.gain_ch2.addItems(["LV", "HV"])
            self.decimation = QtWidgets.QSpinBox()
            self.decimation.setRange(1, 65536)
            self.decimation.setValue(1)
            self.averaging = QtWidgets.QCheckBox("Włączone")
            self.trigger = QtWidgets.QComboBox()
            self.trigger.addItems(["NOW", "CH1_PE", "CH1_NE", "CH2_PE", "CH2_NE", "EXT_PE", "EXT_NE"])
            self.trigger_level = QtWidgets.QDoubleSpinBox()
            self.trigger_level.setRange(-20.0, 20.0)
            self.trigger_level.setDecimals(4)
            self.trigger_level.setSingleStep(0.01)
            self.auto_trigger_btn = QtWidgets.QPushButton("Wyznacz Trigger automatycznie")
            self.auto_trigger_btn.setMinimumHeight(34)
            self.auto_trigger_btn.setMinimumWidth(220)
            self.trigger_delay = QtWidgets.QSpinBox()
            self.trigger_delay.setRange(-100_000_000, 100_000_000)
            self.trigger_timeout = QtWidgets.QDoubleSpinBox()
            self.trigger_timeout.setRange(0.1, 600.0)
            self.trigger_timeout.setValue(10.0)
            self.trigger_timeout.setSuffix(" s")
            self.mode = QtWidgets.QComboBox()
            self.mode.addItem("czas trwania", "duration")
            self.mode.addItem("liczba ramek", "frames")
            self.duration = QtWidgets.QDoubleSpinBox()
            self.duration.setRange(0.000001, 3600.0)
            self.duration.setDecimals(6)
            self.duration.setValue(0.01)
            self.duration.setSuffix(" s")
            self.frame_size = QtWidgets.QSpinBox()
            self.frame_size.setRange(1, 50_000_000)
            self.frame_size.setValue(65_536)
            self.frame_count = QtWidgets.QSpinBox()
            self.frame_count.setRange(1, 1_000_000)
            self.frame_count.setValue(1)

            self.summary = QtWidgets.QLabel("")
            self.summary.setWordWrap(True)
            self.status = QtWidgets.QLabel("Gotowy")
            self.status.setWordWrap(True)
            self.progress = QtWidgets.QProgressBar()
            self.start_btn = QtWidgets.QPushButton("Rozpocznij akwizycję")

            help_texts = {
                "host": (
                    "Adres IP lub nazwa DNS płytki Red Pitaya.\n"
                    "\n"
                    "Na tej płytce musi działać agent rp_prpd_agent.py."
                ),
                "port": (
                    "Port TCP agenta uruchomionego na Red Pitaya.\n"
                    "\n"
                    "Musi być taki sam jak parametr --port agenta."
                ),
                "channels": (
                    "Kanały wejściowe ADC do akwizycji.\n"
                    "\n"
                    "Opcje:\n"
                    "- IN1\n"
                    "- IN2\n"
                    "- IN1+IN2"
                ),
                "gain_ch1": (
                    "Zakres wejściowy Red Pitaya dla kanału IN1.\n"
                    "\n"
                    "LV: dla małych sygnałów.\n"
                    "HV: dla większych napięć wejściowych.\n"
                    "\n"
                    f"Płytka: {BOARD_MODEL}."
                ),
                "gain_ch2": (
                    "Zakres wejściowy Red Pitaya dla kanału IN2.\n"
                    "\n"
                    "LV: dla małych sygnałów.\n"
                    "HV: dla większych napięć wejściowych.\n"
                    "\n"
                    f"Płytka: {BOARD_MODEL}."
                ),
                "decimation": (
                    "Decymacja zmniejsza efektywną częstotliwość próbkowania ADC.\n"
                    "\n"
                    "Wzór:\n"
                    "fs = 125 MS/s / decymacja\n"
                    "\n"
                    "Przykłady:\n"
                    "- 1 -> 125 MS/s\n"
                    "- 125 -> 1 MS/s\n"
                    "- 1024 -> około 122.07 kS/s\n"
                    "\n"
                    "Większa decymacja daje dłuższy możliwy czas akwizycji i mniejszy plik, "
                    "ale pogarsza rozdzielczość czasową impulsów."
                ),
                "averaging": (
                    "Uśrednianie sprzętowe Red Pitaya, jeśli jest obsługiwane przez użyte API.\n"
                ),
                "trigger": (
                    "Trigger określa moment startu akwizycji.\n"
                    "\n"
                    "Dostępne tryby:\n"
                    "- NOW: start od razu po kliknięciu Start.\n"
                    "- CH1_PE: zbocze narastające na IN1.\n"
                    "- CH1_NE: zbocze opadające na IN1.\n"
                    "- CH2_PE: zbocze narastające na IN2.\n"
                    "- CH2_NE: zbocze opadające na IN2.\n"
                    "- EXT_PE: zewnętrzny Trigger, zbocze narastające.\n"
                    "- EXT_NE: zewnętrzny Trigger, zbocze opadające.\n"
                    "\n"
                    "Dla trybów CH1/CH2 używane jest pole Poziom Triggera [V]."
                ),
                "trigger_level": (
                    "Poziom Triggera w woltach dla trybów CH1_PE, CH1_NE, CH2_PE i CH2_NE.\n"
                    "\n"
                    "Przykład:\n"
                    "CH1_PE oraz poziom 0.1 V rozpocznie akwizycję, gdy sygnał IN1 przejdzie "
                    "narastająco przez około 0.1 V."
                ),
                "trigger_delay": (
                    "Opóźnienie Triggera w próbkach używane przez bufor DMA.\n"
                    "\n"
                    "Znaczenie:\n"
                    "- określa położenie momentu Triggera względem zapisanego bufora,\n"
                    "- wpływa na to, ile próbek po Triggerze trafi do pliku,\n"
                    "- wartość 0 w tym programie oznacza tryb automatyczny.\n"
                    "\n"
                    "Tryb automatyczny:\n"
                    "agent ustawia opóźnienie na pełny rozmiar akwizycji, czyli zbiera cały żądany bufor po Triggerze.\n"
                    "\n"
                    "Przykład:\n"
                    "przy fs=1 MS/s wartość 10000 odpowiada 10 ms.\n"
                    "\n"
                    "Wartości ujemne mogą służyć do obejrzenia fragmentu sprzed Triggera, "
                    "jeśli dana wersja API/FPGA Red Pitaya to obsługuje."
                ),
                "trigger_timeout": (
                    "Maksymalny czas oczekiwania na Trigger i zapełnienie bufora DMA.\n"
                    "\n"
                    "Jeśli czas minie, akwizycja zostanie przerwana z błędem."
                ),
                "mode": "Tryb wyznaczania długości akwizycji:\n\n- po czasie trwania,\n- po liczbie ramek.",
                "duration": "Czas zbierania danych w trybie czasu trwania.",
                "frame_size": (
                    "Liczba próbek na kanał w jednej ramce TCP.\n"
                    "\n"
                    "Ta sama wielkość jest używana przy zapisie ramek w pliku RPPR."
                ),
                "frame_count": "Liczba ramek do zebrania w trybie ramek.",
                "summary": (
                    "Podsumowanie wyliczonych parametrów:\n"
                    "- częstotliwość próbkowania,\n"
                    "- liczba próbek,\n"
                    "- liczba ramek,\n"
                    "- rozmiar danych."
                ),
                "status": "Aktualny stan połączenia, akwizycji, zapisu lub ładowania pliku.",
            }

            connection_form = self.create_group_form("Połączenie", controls_layout)
            adc_form = self.create_group_form("ADC", controls_layout)
            trigger_form = self.create_group_form("Trigger", controls_layout)
            acquisition_form = self.create_group_form("Akwizycja", controls_layout)
            state_form = self.create_group_form("Stan", controls_layout)

            self.add_help_row(connection_form, "Adres płytki", self.host, help_texts["host"])
            self.add_help_row(connection_form, "Port", self.port, help_texts["port"])
            self.add_help_row(adc_form, "Kanały", self.channels, help_texts["channels"])
            self.add_help_row(adc_form, "Zakres IN1", self.gain_ch1, help_texts["gain_ch1"])
            self.add_help_row(adc_form, "Zakres IN2", self.gain_ch2, help_texts["gain_ch2"])
            self.add_help_row(adc_form, "Decymacja", self.decimation, help_texts["decimation"])
            self.add_help_row(adc_form, "Uśrednianie", self.averaging, help_texts["averaging"])
            self.add_help_row(trigger_form, "Trigger", self.trigger, help_texts["trigger"])
            trigger_level_widget = QtWidgets.QWidget()
            trigger_level_layout = QtWidgets.QVBoxLayout(trigger_level_widget)
            trigger_level_layout.setContentsMargins(0, 0, 0, 0)
            trigger_level_layout.setSpacing(6)
            trigger_level_layout.addWidget(self.trigger_level)
            trigger_level_layout.addWidget(self.auto_trigger_btn)
            self.add_help_row(trigger_form, "Poziom Triggera [V]", trigger_level_widget, help_texts["trigger_level"])
            self.add_help_row(trigger_form, "Opóźnienie Triggera [próbki]", self.trigger_delay, help_texts["trigger_delay"])
            self.add_help_row(trigger_form, "Limit czasu Triggera", self.trigger_timeout, help_texts["trigger_timeout"])
            self.add_help_row(acquisition_form, "Tryb akwizycji", self.mode, help_texts["mode"])
            self.add_help_row(acquisition_form, "Czas akwizycji", self.duration, help_texts["duration"])
            self.add_help_row(acquisition_form, "Rozmiar ramki [próbki/kanał]", self.frame_size, help_texts["frame_size"])
            self.add_help_row(acquisition_form, "Liczba ramek", self.frame_count, help_texts["frame_count"])
            self.add_help_row(state_form, "Podsumowanie", self.summary, help_texts["summary"])
            state_form.addRow(self.progress)
            state_form.addRow(self.start_btn)
            self.add_help_row(state_form, "Status", self.status, help_texts["status"])

            self.file_list = QtWidgets.QListWidget()
            self.refresh_btn = QtWidgets.QPushButton("Odśwież pliki")
            self.load_btn = QtWidgets.QPushButton("Wczytaj wybrany")
            self.export_btn = QtWidgets.QPushButton("Eksportuj Java BIN")
            self.plot_ch1 = QtWidgets.QCheckBox("IN1")
            self.plot_ch1.setChecked(True)
            self.plot_ch2 = QtWidgets.QCheckBox("IN2")
            self.plot_ch2.setChecked(True)
            self.plot_theme = QtWidgets.QComboBox()
            self.plot_theme.addItem("Ciemne tło", "dark")
            self.plot_theme.addItem("Jasne tło", "light")
            self.curve1_color = "#1f77b4"
            self.curve2_color = "#d62728"
            self.curve1_color_btn = QtWidgets.QPushButton("Kolor IN1")
            self.curve2_color_btn = QtWidgets.QPushButton("Kolor IN2")

            file_box = QtWidgets.QGroupBox("Pliki")
            file_layout = QtWidgets.QVBoxLayout(file_box)
            file_layout.addWidget(self.file_list)
            file_layout.addWidget(self.refresh_btn)
            file_layout.addWidget(self.load_btn)
            file_layout.addWidget(self.export_btn)
            channel_row = QtWidgets.QHBoxLayout()
            channel_row.addWidget(self.plot_ch1)
            channel_row.addWidget(self.plot_ch2)
            file_layout.addLayout(channel_row)

            appearance_box = QtWidgets.QGroupBox("Wygląd wykresu")
            appearance_layout = QtWidgets.QFormLayout(appearance_box)
            appearance_layout.addRow("Tło", self.plot_theme)
            color_widget = QtWidgets.QWidget()
            color_row = QtWidgets.QHBoxLayout()
            color_row.setContentsMargins(0, 0, 0, 0)
            color_row.addWidget(self.curve1_color_btn)
            color_row.addWidget(self.curve2_color_btn)
            color_widget.setLayout(color_row)
            appearance_layout.addRow("Kolor linii", color_widget)
            file_layout.addWidget(appearance_box)
            controls_layout.addWidget(file_box)
            controls_layout.addStretch(1)

            plot_area = QtWidgets.QWidget()
            plot_layout = QtWidgets.QVBoxLayout(plot_area)
            self.plot = pg.PlotWidget()
            self.plot.showGrid(x=True, y=True, alpha=0.25)
            self.plot.setLabel("bottom", "Czas", units="s")
            self.plot.setLabel("left", "ADC", units="raw int16")
            self.plot.addLegend()
            self.curve1 = self.plot.plot([], [], pen=pg.mkPen(self.curve1_color, width=1), name="IN1")
            self.curve2 = self.plot.plot([], [], pen=pg.mkPen(self.curve2_color, width=1), name="IN2")
            self.info = QtWidgets.QLabel("Nie wczytano pliku")
            self.info.setWordWrap(True)
            plot_layout.addWidget(self.plot, stretch=1)
            plot_layout.addWidget(self.info)

            splitter = QtWidgets.QSplitter()
            splitter.addWidget(controls_scroll)
            splitter.addWidget(plot_area)
            splitter.setStretchFactor(0, 0)
            splitter.setStretchFactor(1, 1)
            layout.addWidget(splitter)

            for widget in [
                self.host,
                self.port,
                self.channels,
                self.gain_ch1,
                self.gain_ch2,
                self.decimation,
                self.averaging,
                self.trigger,
                self.trigger_level,
                self.trigger_delay,
                self.trigger_timeout,
                self.mode,
                self.duration,
                self.frame_size,
                self.frame_count,
            ]:
                if hasattr(widget, "valueChanged"):
                    widget.valueChanged.connect(lambda *_: self.update_summary())
                if hasattr(widget, "currentTextChanged"):
                    widget.currentTextChanged.connect(lambda *_: self.update_summary())
                if hasattr(widget, "textChanged"):
                    widget.textChanged.connect(lambda *_: self.update_summary())
                if hasattr(widget, "stateChanged"):
                    widget.stateChanged.connect(lambda *_: self.update_summary())

            self.start_btn.clicked.connect(self.start_acquisition)
            self.refresh_btn.clicked.connect(self.refresh_files)
            self.load_btn.clicked.connect(self.load_selected_file)
            self.export_btn.clicked.connect(self.export_selected_file)
            self.auto_trigger_btn.clicked.connect(self.start_trigger_calibrator)
            self.channels.currentTextChanged.connect(lambda *_: self.update_gain_controls())
            self.plot_ch1.stateChanged.connect(self.update_plot)
            self.plot_ch2.stateChanged.connect(self.update_plot)
            self.plot_theme.currentIndexChanged.connect(lambda *_: self.apply_plot_style())
            self.curve1_color_btn.clicked.connect(lambda: self.choose_curve_color(1))
            self.curve2_color_btn.clicked.connect(lambda: self.choose_curve_color(2))
            self.mode.currentTextChanged.connect(self.update_mode_controls)

            self.update_color_buttons()
            self.apply_plot_style()
            self.update_gain_controls()
            self.update_mode_controls()
            self.update_summary()
            self.refresh_files()

        def update_gain_controls(self) -> None:
            channels = channel_ids(self.channels.currentText())
            self.gain_ch1.setEnabled(1 in channels)
            self.gain_ch2.setEnabled(2 in channels)

        def apply_plot_style(self) -> None:
            theme = self.plot_theme.currentData()
            if theme == "light":
                background = "#ffffff"
                foreground = "#202124"
                grid_alpha = 0.22
            else:
                background = "#111111"
                foreground = "#eeeeee"
                grid_alpha = 0.25

            self.plot.setBackground(background)
            self.plot.showGrid(x=True, y=True, alpha=grid_alpha)
            for axis_name in ("bottom", "left"):
                axis = self.plot.getAxis(axis_name)
                axis.setPen(pg.mkPen(foreground))
                axis.setTextPen(pg.mkPen(foreground))
            self.info.setStyleSheet(f"color: {foreground};")
            self.plot.setStyleSheet(f"background-color: {background};")
            self.update_curve_pens()

        def update_curve_pens(self) -> None:
            self.curve1.setPen(pg.mkPen(self.curve1_color, width=1))
            self.curve2.setPen(pg.mkPen(self.curve2_color, width=1))

        def update_color_buttons(self) -> None:
            self.set_color_button_style(self.curve1_color_btn, self.curve1_color)
            self.set_color_button_style(self.curve2_color_btn, self.curve2_color)

        @staticmethod
        def set_color_button_style(button, color: str) -> None:
            qcolor = QtGui.QColor(color)
            text_color = "#000000" if qcolor.lightness() > 150 else "#ffffff"
            button.setStyleSheet(
                f"QPushButton {{ background-color: {color}; color: {text_color}; "
                "border: 1px solid #777; padding: 4px 8px; }}"
            )

        def choose_curve_color(self, channel: int) -> None:
            current = self.curve1_color if channel == 1 else self.curve2_color
            color = QtWidgets.QColorDialog.getColor(
                QtGui.QColor(current),
                self,
                f"Wybierz kolor IN{channel}",
            )
            if not color.isValid():
                return

            if channel == 1:
                self.curve1_color = color.name()
            else:
                self.curve2_color = color.name()
            self.update_color_buttons()
            self.update_curve_pens()

        def create_group_form(self, title: str, parent_layout) -> object:
            group = QtWidgets.QGroupBox(title)
            form = QtWidgets.QFormLayout(group)
            form.setFieldGrowthPolicy(QtWidgets.QFormLayout.AllNonFixedFieldsGrow)
            form.setLabelAlignment(QtCore.Qt.AlignLeft)
            form.setContentsMargins(10, 12, 10, 10)
            form.setHorizontalSpacing(10)
            form.setVerticalSpacing(8)
            parent_layout.addWidget(group)
            return form

        def add_help_row(self, form, label_text: str, field, tooltip: str) -> None:
            rich_tooltip = self.format_tooltip(tooltip)
            label_widget = QtWidgets.QWidget()
            label_layout = QtWidgets.QHBoxLayout(label_widget)
            label_layout.setContentsMargins(0, 0, 0, 0)
            label_layout.setSpacing(6)

            label = QtWidgets.QLabel(label_text)
            label.setToolTip(rich_tooltip)
            help_btn = QtWidgets.QToolButton()
            help_btn.setText("?")
            help_btn.setToolTip(rich_tooltip)
            help_btn.setCursor(QtCore.Qt.WhatsThisCursor)
            help_btn.setAutoRaise(True)
            help_btn.setFixedSize(20, 20)
            help_btn.setStyleSheet(
                "QToolButton { border: 1px solid #888; border-radius: 10px; "
                "font-weight: bold; padding: 0px; }"
            )

            field.setToolTip(rich_tooltip)
            label_layout.addWidget(label)
            label_layout.addWidget(help_btn)
            label_layout.addStretch(1)
            form.addRow(label_widget, field)

        @staticmethod
        def format_tooltip(text: str) -> str:
            escaped_lines = []
            for line in text.strip().splitlines():
                escaped_line = html.escape(line.strip())
                if escaped_line.startswith("- "):
                    escaped_line = "&bull; " + escaped_line[2:]
                escaped_lines.append(escaped_line)
            escaped = "<br>".join(escaped_lines)
            return (
                "<div style='"
                "width: 340px; "
                "white-space: normal; "
                "line-height: 1.25em;"
                f"'>{escaped}</div>"
            )

        def get_config(self) -> dict:
            return build_config_from_values(
                host=self.host.text().strip(),
                port=int(self.port.value()),
                channels=channel_ids(self.channels.currentText()),
                gains={
                    1: self.gain_ch1.currentText(),
                    2: self.gain_ch2.currentText(),
                },
                decimation=int(self.decimation.value()),
                averaging=self.averaging.isChecked(),
                trigger_source=self.trigger.currentText(),
                trigger_level=float(self.trigger_level.value()),
                trigger_delay=int(self.trigger_delay.value()),
                acquisition_mode=self.mode.currentData(),
                duration_s=float(self.duration.value()),
                frame_size=int(self.frame_size.value()),
                frame_count=int(self.frame_count.value()),
                trigger_timeout_s=float(self.trigger_timeout.value()),
            )

        def start_trigger_calibrator(self) -> None:
            if self.trigger_calibrator_process is not None:
                QtWidgets.QMessageBox.information(
                    self,
                    "Kalibrator Triggera",
                    "Kalibrator Triggera jest już uruchomiony.",
                )
                return

            try:
                config = self.get_config()
            except Exception as exc:
                QtWidgets.QMessageBox.warning(self, "Nieprawidłowa konfiguracja", str(exc))
                return

            config_fd, config_name = tempfile.mkstemp(prefix="rp_trigger_config_", suffix=".json")
            result_fd, result_name = tempfile.mkstemp(prefix="rp_trigger_result_", suffix=".json")
            os.close(config_fd)
            os.close(result_fd)
            config_path = Path(config_name)
            result_path = Path(result_name)
            result_path.unlink(missing_ok=True)
            config_path.write_text(json.dumps(config, indent=2, sort_keys=True), encoding="utf-8")

            script_path = Path(__file__).resolve().with_name("rp_trigger_calibrator.py")
            process = QtCore.QProcess(self)
            process.setProcessChannelMode(QtCore.QProcess.MergedChannels)
            process.finished.connect(self.on_trigger_calibrator_finished)
            process.errorOccurred.connect(self.on_trigger_calibrator_error)

            self.trigger_calibrator_process = process
            self.trigger_calibrator_config_path = config_path
            self.trigger_calibrator_result_path = result_path
            self.auto_trigger_btn.setEnabled(False)
            self.status.setText("Uruchamianie kalibratora Triggera...")
            process.start(sys.executable, [str(script_path), "--config", str(config_path), "--result", str(result_path)])

            if not process.waitForStarted(3000):
                message = process.errorString()
                self.cleanup_trigger_calibrator()
                QtWidgets.QMessageBox.critical(self, "Nie udało się uruchomić kalibratora", message)

        def on_trigger_calibrator_error(self, error) -> None:
            if self.trigger_calibrator_process is None:
                return
            self.status.setText(f"Błąd kalibratora Triggera: {self.trigger_calibrator_process.errorString()}")

        def on_trigger_calibrator_finished(self, exit_code: int, exit_status) -> None:
            process = self.trigger_calibrator_process
            result_path = self.trigger_calibrator_result_path
            output = ""
            if process is not None:
                output = bytes(process.readAllStandardOutput()).decode("utf-8", errors="replace").strip()

            try:
                if exit_code != 0:
                    message = output or f"Proces zakończył się kodem {exit_code}."
                    QtWidgets.QMessageBox.critical(self, "Kalibrator Triggera zakończył się błędem", message)
                    self.status.setText("Kalibrator Triggera zakończył się błędem.")
                    return

                if result_path is None or not result_path.exists():
                    self.status.setText("Kalibrator Triggera zakończony bez wyniku.")
                    return

                result = json.loads(result_path.read_text(encoding="utf-8"))
                if result.get("accepted"):
                    value = float(result["trigger_level_v"])
                    self.trigger_level.setValue(value)
                    self.status.setText(f"Ustawiono Poziom Triggera: {value:.6f} V")
                    self.refresh_files()
                else:
                    self.status.setText("Kalibracja Triggera anulowana.")
            except Exception as exc:
                QtWidgets.QMessageBox.critical(self, "Nie udało się odczytać wyniku kalibracji", str(exc))
                self.status.setText("Nie udało się odczytać wyniku kalibracji.")
            finally:
                self.cleanup_trigger_calibrator()

        def cleanup_trigger_calibrator(self) -> None:
            for path in (self.trigger_calibrator_config_path, self.trigger_calibrator_result_path):
                if path is not None:
                    try:
                        path.unlink(missing_ok=True)
                    except Exception:
                        pass
            self.trigger_calibrator_process = None
            self.trigger_calibrator_config_path = None
            self.trigger_calibrator_result_path = None
            self.auto_trigger_btn.setEnabled(True)

        def update_mode_controls(self) -> None:
            duration_mode = self.mode.currentData() == "duration"
            self.duration.setEnabled(duration_mode)
            self.frame_count.setEnabled(not duration_mode)
            self.update_summary()

        def update_summary(self) -> None:
            try:
                config = self.get_config()
                mib = config["total_samples"] * len(config["channels"]) * 2 / (1024 * 1024)
                self.summary.setText(
                    f"fs={config['sample_rate']:.3f} Hz, próbki/kanał={config['total_samples']}, "
                    f"ramki={config['frame_count']}, czas={config['duration_s']:.6f} s, "
                    f"dane={mib:.2f} MiB"
                )
            except Exception as exc:
                self.summary.setText(str(exc))

        def start_acquisition(self) -> None:
            try:
                config = self.get_config()
            except Exception as exc:
                QtWidgets.QMessageBox.warning(self, "Nieprawidłowa konfiguracja", str(exc))
                return

            self.start_btn.setEnabled(False)
            self.progress.setValue(0)
            self.status.setText("Start akwizycji")
            self.thread = QtCore.QThread()
            self.worker = AcquisitionWorker(config)
            self.worker.moveToThread(self.thread)
            self.thread.started.connect(self.worker.run)
            self.worker.progress.connect(self.on_progress)
            self.worker.status.connect(self.status.setText)
            self.worker.finished.connect(self.on_acquisition_finished)
            self.worker.failed.connect(self.on_acquisition_failed)
            self.worker.finished.connect(self.thread.quit)
            self.worker.failed.connect(self.thread.quit)
            self.thread.finished.connect(self.thread.deleteLater)
            self.thread.start()

        def on_progress(self, done: int, total: int, samples: int, expected: int) -> None:
            self.progress.setMaximum(total)
            self.progress.setValue(done)
            self.status.setText(f"Odebrano {done}/{total} ramek, {samples}/{expected} próbek/kanał")

        def on_acquisition_finished(self, path: str) -> None:
            self.start_btn.setEnabled(True)
            self.status.setText(f"Zapisano {path}")
            self.refresh_files()
            self.select_path(Path(path))
            self.load_path(Path(path))

        def on_acquisition_failed(self, message: str) -> None:
            self.start_btn.setEnabled(True)
            self.status.setText(f"Błąd: {message}")
            QtWidgets.QMessageBox.critical(self, "Akwizycja nie powiodła się", message)

        def refresh_files(self) -> None:
            RECEIVED_DIR.mkdir(parents=True, exist_ok=True)
            current = self.current_path
            self.file_list.clear()
            files = sorted(RECEIVED_DIR.glob("*.rppr.bin"), key=lambda p: p.stat().st_mtime, reverse=True)
            for path in files:
                item = QtWidgets.QListWidgetItem(path.name)
                item.setData(QtCore.Qt.UserRole, str(path))
                self.file_list.addItem(item)
            if current:
                self.select_path(current)

        def select_path(self, path: Path) -> None:
            for row in range(self.file_list.count()):
                item = self.file_list.item(row)
                if Path(item.data(QtCore.Qt.UserRole)) == path:
                    self.file_list.setCurrentRow(row)
                    break

        def selected_path(self) -> Path | None:
            item = self.file_list.currentItem()
            if not item:
                return None
            return Path(item.data(QtCore.Qt.UserRole))

        def load_selected_file(self) -> None:
            path = self.selected_path()
            if not path:
                return
            self.load_path(path)

        def load_path(self, path: Path) -> None:
            try:
                metadata, t, data = load_rppr_for_plot(path)
            except Exception as exc:
                QtWidgets.QMessageBox.critical(self, "Nie udało się wczytać pliku", str(exc))
                return

            self.current_path = path
            self.current_metadata = metadata
            self.current_t = t
            self.current_data = data
            self.info.setText(
                f"{path.name} | kanały={metadata.get('channels')} | "
                f"fs={metadata.get('sample_rate')} Hz | punkty na wykresie={metadata.get('plot_points')} "
                f"(krok={metadata.get('plot_stride')})"
            )
            self.update_plot()

        def update_plot(self) -> None:
            if self.current_t is None or self.current_data is None or self.current_metadata is None:
                return
            channels = list(self.current_metadata.get("channels") or [])
            self.curve1.clear()
            self.curve2.clear()
            if 1 in channels and self.plot_ch1.isChecked():
                idx = channels.index(1)
                self.curve1.setData(self.current_t, self.current_data[:, idx])
            if 2 in channels and self.plot_ch2.isChecked():
                idx = channels.index(2)
                self.curve2.setData(self.current_t, self.current_data[:, idx])

        def export_selected_file(self) -> None:
            path = self.selected_path() or self.current_path
            if not path:
                return
            metadata, _ = iter_frames(path)
            channels = list(metadata.get("channels") or [1])
            channel = 1 if 1 in channels else channels[0]
            out = path.with_suffix("").with_suffix(f".ch{channel}.prpdtool.bin")
            try:
                export_prpdtool_double(path, out, channel=channel)
            except Exception as exc:
                QtWidgets.QMessageBox.critical(self, "Eksport nie powiódł się", str(exc))
                return
            self.status.setText(f"Wyeksportowano {out}")

    app = QtWidgets.QApplication(sys.argv)
    pg.setConfigOptions(antialias=False)
    window = MainWindow()
    window.show()
    return app.exec()


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Red Pitaya PRPD receiver GUI")
    parser.add_argument("--mock-agent", action="store_true", help="run a local mock Red Pitaya agent")
    parser.add_argument("--host", default="0.0.0.0", help="mock-agent bind host")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--export", nargs=2, metavar=("INPUT_RPPR", "OUTPUT_BIN"), help="export RPPR to Java PRPDtool binary")
    parser.add_argument("--channel", type=int, default=1, choices=[1, 2], help="channel used with --export")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    if args.mock_agent:
        run_mock_agent(args.host, args.port)
        return 0
    if args.export:
        export_prpdtool_double(Path(args.export[0]), Path(args.export[1]), args.channel)
        return 0
    return launch_gui()


if __name__ == "__main__":
    raise SystemExit(main())
