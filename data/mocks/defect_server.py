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
import re
import random


# ==========================================================
# CONFIG
# ==========================================================

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 7777


# ==========================================================
# GENERATOR
# ==========================================================

DEFECTS = {
    "void": {
        "clusters": [
            {"center": 55,  "sigma": 18, "rate": 35, "amp": 0.035, "amp_sigma": 0.015, "sign": "both"},
            {"center": 235, "sigma": 18, "rate": 35, "amp": 0.035, "amp_sigma": 0.015, "sign": "both"},
        ]
    },

    "surface": {
        "clusters": [
            {"center": 50,  "sigma": 35, "rate": 45, "amp": 0.030, "amp_sigma": 0.020, "sign": "positive"},
            {"center": 230, "sigma": 55, "rate": 80, "amp": 0.050, "amp_sigma": 0.030, "sign": "negative"},
        ]
    },

    "corona_positive": {
        "clusters": [
            {"center": 70, "sigma": 16, "rate": 90, "amp": 0.040, "amp_sigma": 0.015, "sign": "positive"},
        ]
    },

    "corona_negative": {
        "clusters": [
            {"center": 250, "sigma": 16, "rate": 90, "amp": 0.040, "amp_sigma": 0.015, "sign": "negative"},
        ]
    },

    "floating": {
        "clusters": [
            {"center": 40,  "sigma": 12, "rate": 30, "amp": 0.030, "amp_sigma": 0.015, "sign": "both"},
            {"center": 90,  "sigma": 5,  "rate": 20, "amp": 0.060, "amp_sigma": 0.020, "sign": "both"},
            {"center": 210, "sigma": 10, "rate": 70, "amp": 0.070, "amp_sigma": 0.025, "sign": "both"},
            {"center": 235, "sigma": 6,  "rate": 40, "amp": 0.080, "amp_sigma": 0.020, "sign": "both"},
        ]
    },

    "noise": {
        "clusters": [
            {"center": 180, "sigma": 180, "rate": 120, "amp": 0.010, "amp_sigma": 0.005, "sign": "both"},
        ]
    },
}

def choose_sign(rng, mode):
    if mode == "positive":
        return 1.0
    if mode == "negative":
        return -1.0
    return 1.0 if rng.random() < 0.5 else -1.0


def add_pulse(u, idx, amp, fs, pulse_us, tau_us):
    length = max(1, int(pulse_us * 1e-6 * fs))
    tau = max(1.0, tau_us * 1e-6 * fs)

    shape = np.exp(-np.arange(length) / tau)

    end = min(len(u), idx + length)
    if end <= idx:
        return

    u[idx:end] += amp * shape[:end - idx]

def generate_chunk(
        t0,
        fs,
        chunk_samples,
        f0,
        baseline_amp,
        defects,
        rng,
        noise_std,
        pulse_us,
        tau_us,
        background_rate,
        background_amp
):
    dt = 1.0 / fs

    t = t0 + np.arange(chunk_samples) * dt

    u = baseline_amp * np.sin(2.0 * np.pi * f0 * t)
    u += rng.normal(0.0, noise_std, chunk_samples)
    T = 1.0 / f0
    cycles = int(chunk_samples*dt * f0)

    for c in range(cycles):
        # Niski, losowy „dywan” impulsów w całym zakresie faz.
        n_bg = rng.poisson(background_rate)

        for _ in range(n_bg):
            phase = rng.uniform(0.0, 360.0)
            ti = c * T + phase / 360.0 * T
            if ti >= chunk_samples*dt:
                continue

            amp = abs(rng.normal(background_amp, background_amp * 0.35))
            amp *= choose_sign(rng, "both")

            add_pulse(
                u,
                int(ti * fs),
                amp,
                fs,
                pulse_us * 0.5,
                tau_us * 0.7,
            )

        nd = random.randint(1, len(defects))
        for defect in random.sample(defects, nd):
            cfg = DEFECTS[defect]
            # Impulsy właściwe dla klasy defektu.
            for cl in cfg["clusters"]:
                count = rng.poisson(cl["rate"])

                for _ in range(count):
                    phase = rng.normal(cl["center"], cl["sigma"]) % 360.0
                    ti = c * T + phase / 360.0 * T

                    if ti >= chunk_samples*dt:
                        continue

                    amp = abs(rng.normal(cl["amp"], cl["amp_sigma"]))
                    amp *= choose_sign(rng, cl["sign"])

                    add_pulse(
                        u,
                        int(ti * fs),
                        amp,
                        fs,
                        pulse_us,
                        tau_us,
                    )

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
        baseline_amp,
        defects,
        limit_msps,
        seed,
        noise_std,
        pulse_us,
        tau_us,
        background_rate,
        background_amp
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

    rng = np.random.default_rng(seed)
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
                baseline_amp=baseline_amp,
                defects=defects,
                rng=rng,
                noise_std=noise_std,
                pulse_us=pulse_us,
                tau_us=tau_us,
                background_rate=background_rate,
                background_amp=background_amp
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
        description="High-speed t,u streaming server with PD simulation"
    )

    p.add_argument("--host", default=DEFAULT_HOST)
    p.add_argument("--port", type=int, default=DEFAULT_PORT)
    p.add_argument("--daemon", action="store_true")

    p.add_argument("--fs", type=float, default=5_000_000)

    p.add_argument(
        "--chunk",
        type=int,
        default=100_000,
        help="Samples per chunk"
    )

    p.add_argument("--f0", type=float, default=50.0)
    p.add_argument("--baseline_amp", type=float, default=320.0)
    p.add_argument("--noise_std", type=float, default=0.002)
    p.add_argument("--pulse-us", type=float, default=20.0)
    p.add_argument("--tau-us", type=float, default=3.0)

    p.add_argument("--background-rate", type=float, default=60.0)
    p.add_argument("--background-amp", type=float, default=0.006)

    p.add_argument(
        "--limit-msps",
        type=float,
        default=5.0,
        help="Limit transmit speed in Ms/s"
    )

    p.add_argument(
        "--defects",
        #choices=sorted(DEFECTS.keys()),
        type=str,
        default="void",
    )

    p.add_argument("--seed", type=int, default=123)

    args = p.parse_args()

    while True:
        run_server(
            host=args.host,
            port=args.port,
            fs=args.fs,
            chunk_samples=args.chunk,
            f0=args.f0,
            baseline_amp=args.baseline_amp,
            defects=re.split(r"[,;]+",args.defects),
            limit_msps=args.limit_msps,
            seed=args.seed,
            noise_std=args.noise_std,
            pulse_us=args.pulse_us,
            tau_us=args.tau_us,
            background_rate=args.background_rate,
            background_amp=args.background_amp
        )

        if args.daemon:
            print( "Daemon mode, restarting" )
        else:
            break

if __name__ == "__main__":
    main()
