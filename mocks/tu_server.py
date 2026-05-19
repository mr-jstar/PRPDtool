#!/usr/bin/env python3
"""
Poniższy program:

* generuje lub wysyła próbki `(t,u)`
* działa przez TCP
* używa binarnego formatu little-endian
* umożliwia transmisję do ok. 5 Ms/s
* wysyła:

[t0(double), u0(double)]
[t1(double), u1(double)]

"""


import argparse
import math
import socket
import struct
import time

import numpy as np


# ==========================================================
# CONFIG
# ==========================================================

DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 9999


# ==========================================================
# GENERATOR
# ==========================================================


def generate_chunk(
        t0,
        fs,
        chunk_samples,
        f0,
        noise_std,
        amp
):
    dt = 1.0 / fs

    t = t0 + np.arange(chunk_samples) * dt

    u = amp * np.sin(2.0 * np.pi * f0 * t)

    if noise_std > 0.0:
        u += np.random.normal(0.0, noise_std, chunk_samples)

    return t, u


# ==========================================================
# SERIALIZATION
# ==========================================================


def pack_chunk(t, u):
    n = len(t)

    # 2 double na próbkę
    out = bytearray(n * 16)

    offset = 0

    for ti, ui in zip(t, u):
        struct.pack_into("<dd", out, offset, ti, ui)
        offset += 16

    return out


# ==========================================================
# SERVER
# ==========================================================


def run_server(
        host,
        port,
        fs,
        chunk_samples,
        f0,
        noise_std,
        amp,
        limit_msps
):

    print(f"Listening on {host}:{port}")

    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    # duże bufory
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 8 * 1024 * 1024)

    srv.bind((host, port))
    srv.listen(1)

    conn, addr = srv.accept()

    print(f"Client connected: {addr}")

    conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    conn.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 8 * 1024 * 1024)

    t0 = 0.0

    sent_samples = 0
    t_start = time.perf_counter()

    try:
        while True:

            t, u = generate_chunk(
                t0=t0,
                fs=fs,
                chunk_samples=chunk_samples,
                f0=f0,
                noise_std=noise_std,
                amp=amp
            )

            payload = pack_chunk(t, u)

            conn.sendall(payload)

            sent_samples += chunk_samples
            t0 = t[-1] + 1.0 / fs

            # --------------------------------------------------
            # throttling do zadanej Ms/s
            # --------------------------------------------------

            if limit_msps > 0:
                elapsed = time.perf_counter() - t_start

                target_samples = elapsed * limit_msps * 1_000_000

                while sent_samples > target_samples:
                    time.sleep(0.0001)
                    elapsed = time.perf_counter() - t_start
                    target_samples = elapsed * limit_msps * 1_000_000

    except KeyboardInterrupt:
        print("Interrupted")

    except BrokenPipeError:
        print("Client disconnected")

    finally:
        conn.close()
        srv.close()


# ==========================================================
# MAIN
# ==========================================================


def main():
    p = argparse.ArgumentParser(
        description="High-speed t,u streaming server"
    )

    p.add_argument("--host", default=DEFAULT_HOST)
    p.add_argument("--port", type=int, default=DEFAULT_PORT)

    p.add_argument("--fs", type=float, default=5_000_000)

    p.add_argument(
        "--chunk",
        type=int,
        default=100_000,
        help="Samples per chunk"
    )

    p.add_argument("--f0", type=float, default=50.0)
    p.add_argument("--amp", type=float, default=1.0)
    p.add_argument("--noise", type=float, default=0.01)

    p.add_argument(
        "--limit-msps",
        type=float,
        default=5.0,
        help="Limit transmit speed in Ms/s"
    )

    args = p.parse_args()

    run_server(
        host=args.host,
        port=args.port,
        fs=args.fs,
        chunk_samples=args.chunk,
        f0=args.f0,
        noise_std=args.noise,
        amp=args.amp,
        limit_msps=args.limit_msps
    )


if __name__ == "__main__":
    main()
