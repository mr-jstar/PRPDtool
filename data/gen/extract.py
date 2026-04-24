#!/usr/bin/env python3
import argparse
import csv
import numpy as np


def read_stream(filename):
    t = []
    u = []

    with open(filename) as f:
        r = csv.DictReader(f)
        for row in r:
            t.append(float(row["t"]))
            u.append(float(row["u"]))

    return np.array(t), np.array(u)


def moving_average(x, win):
    if win < 3:
        return np.zeros_like(x)
    kernel = np.ones(win) / win
    return np.convolve(x, kernel, mode="same")


def extract(input_file, output_file, f0, threshold, dead_us, smooth_us):
    t, u = read_stream(input_file)

    fs = 1.0 / np.median(np.diff(t))

    smooth_n = max(3, int(smooth_us * 1e-6 * fs))
    background = moving_average(u, smooth_n)
    x = u - background

    dead_n = max(1, int(dead_us * 1e-6 * fs))

    pulses = []
    i = 1

    while i < len(x) - 1:
        if abs(x[i]) >= threshold:
            j0 = i
            j1 = min(len(x), i + dead_n)

            segment = x[j0:j1]
            k = np.argmax(np.abs(segment))
            idx = j0 + k

            amp = x[idx]
            phase = (360.0 * f0 * t[idx]) % 360.0

            pulses.append((t[idx], phase, amp))

            i = j1
        else:
            i += 1

    with open(output_file, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["t", "phase_deg", "amplitude"])
        for ti, ph, amp in pulses:
            w.writerow([f"{ti:.9f}", f"{ph:.4f}", f"{amp:.9f}"])

    print(f"Detected pulses: {len(pulses)}")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("input")
    p.add_argument("-o", "--output", default="pulses.csv")
    p.add_argument("--f0", type=float, default=50.0)
    p.add_argument("--threshold", type=float, default=0.012)
    p.add_argument("--dead-us", type=float, default=30.0)
    p.add_argument("--smooth-us", type=float, default=200.0)
    args = p.parse_args()

    extract(
        args.input,
        args.output,
        args.f0,
        args.threshold,
        args.dead_us,
        args.smooth_us,
    )


if __name__ == "__main__":
    main()
