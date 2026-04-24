#!/usr/bin/env python3
import argparse
import csv
import numpy as np


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


def generate(
    output,
    defect,
    duration,
    fs,
    f0,
    seed,
    noise_std,
    baseline_amp,
    pulse_us,
    tau_us,
    background_rate,
    background_amp,
):
    rng = np.random.default_rng(seed)

    n = int(duration * fs)
    t = np.arange(n) / fs

    # Sygnał bazowy, który potem ekstraktor powinien odfiltrować.
    u = baseline_amp * np.sin(2.0 * np.pi * f0 * t)

    # Szum pomiarowy.
    u += rng.normal(0.0, noise_std, n)

    T = 1.0 / f0
    cycles = int(duration * f0)

    cfg = DEFECTS[defect]

    for c in range(cycles):
        # Niski, losowy „dywan” impulsów w całym zakresie faz.
        n_bg = rng.poisson(background_rate)

        for _ in range(n_bg):
            phase = rng.uniform(0.0, 360.0)
            ti = c * T + phase / 360.0 * T
            if ti >= duration:
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

        # Impulsy właściwe dla klasy defektu.
        for cl in cfg["clusters"]:
            count = rng.poisson(cl["rate"])

            for _ in range(count):
                phase = rng.normal(cl["center"], cl["sigma"]) % 360.0
                ti = c * T + phase / 360.0 * T

                if ti >= duration:
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

    with open(output, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["t", "u"])

        for ti, ui in zip(t, u):
            w.writerow([f"{ti:.9f}", f"{ui:.9f}"])


def main():
    p = argparse.ArgumentParser(
        description="Generator syntetycznego strumienia WNZ: t,u"
    )

    p.add_argument("-o", "--output", default="pd_stream.csv")
    p.add_argument(
        "--defect",
        choices=sorted(DEFECTS.keys()),
        default="void",
    )

    p.add_argument("-d", "--duration", type=float, default=5.0)
    p.add_argument("--fs", type=float, default=1_000_000)
    p.add_argument("--f0", type=float, default=50.0)
    p.add_argument("--seed", type=int, default=123)

    p.add_argument("--noise", type=float, default=0.002)
    p.add_argument("--baseline-amp", type=float, default=0.030)

    p.add_argument("--pulse-us", type=float, default=20.0)
    p.add_argument("--tau-us", type=float, default=3.0)

    p.add_argument("--background-rate", type=float, default=60.0)
    p.add_argument("--background-amp", type=float, default=0.006)

    args = p.parse_args()

    generate(
        output=args.output,
        defect=args.defect,
        duration=args.duration,
        fs=args.fs,
        f0=args.f0,
        seed=args.seed,
        noise_std=args.noise,
        baseline_amp=args.baseline_amp,
        pulse_us=args.pulse_us,
        tau_us=args.tau_us,
        background_rate=args.background_rate,
        background_amp=args.background_amp,
    )


if __name__ == "__main__":
    main()
