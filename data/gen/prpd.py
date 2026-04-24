#!/usr/bin/env python3
import argparse
import csv
import numpy as np
import matplotlib.pyplot as plt


def read_pulses(filename):
    phase = []
    amp = []

    with open(filename) as f:
        r = csv.DictReader(f)
        for row in r:
            phase.append(float(row["phase_deg"]))
            amp.append(abs(float(row["amplitude"])))

    return np.array(phase), np.array(amp)


def plot_prpd(input_file, output_file, bins_phase, bins_amp):
    phase, amp = read_pulses(input_file)

    amp_min = 0.0
    amp_max = np.percentile(amp, 99.5)

    hist, xedges, yedges = np.histogram2d(
        phase,
        amp,
        bins=[bins_phase, bins_amp],
        range=[[0, 360], [amp_min, amp_max]]
    )

    hist = np.log1p(hist.T)

    plt.figure(figsize=(12, 6))
    plt.imshow(
        hist,
        origin="lower",
        aspect="auto",
        extent=[0, 360, amp_min, amp_max],
    )

    plt.xlabel("Phase [deg]")
    plt.ylabel("|Amplitude|")
    plt.title("Synthetic PRPD")
    plt.tight_layout()
    plt.savefig(output_file, dpi=200)
    plt.close()


def main():
    p = argparse.ArgumentParser()
    p.add_argument("input")
    p.add_argument("-o", "--output", default="prpd.png")
    p.add_argument("--bins-phase", type=int, default=360)
    p.add_argument("--bins-amp", type=int, default=200)
    args = p.parse_args()

    plot_prpd(args.input, args.output, args.bins_phase, args.bins_amp)


if __name__ == "__main__":
    main()
