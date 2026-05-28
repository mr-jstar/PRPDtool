#!/usr/bin/env python3
"""
Experimental IN1 trigger-level calibrator.

This is intentionally a separate GUI program. The main receiver launches it
with a JSON config file and reads back a JSON result file after the user
accepts a proposed trigger level.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np

try:
    from rp_prpd_receive_to_bin import acquire_to_file, load_rppr_for_plot
except ModuleNotFoundError:
    from .rp_prpd_receive_to_bin import acquire_to_file, load_rppr_for_plot


RAW_FULL_SCALE_COUNTS = 8192.0
GAIN_FULL_SCALE_VOLTS = {
    "LV": 1.0,
    "HV": 20.0,
}


def calibration_config(base_config: dict) -> dict:
    config = dict(base_config)
    gains = dict(config.get("gains") or {})
    config.update(
        {
            "channels": [1],
            "gain": str(gains.get("1", config.get("gain", "LV"))).upper(),
            "trigger_source": "NOW",
            "trigger_level": 0.0,
        }
    )
    config["gains"] = {"1": config["gain"], "2": str(gains.get("2", "LV")).upper()}
    return config


def gain_for_in1(metadata: dict, fallback_config: dict) -> str:
    gains = metadata.get("gains") or fallback_config.get("gains") or {}
    return str(gains.get("1", metadata.get("gain", fallback_config.get("gain", "LV")))).upper()


def raw_to_volts(raw: np.ndarray, gain: str) -> np.ndarray:
    full_scale = GAIN_FULL_SCALE_VOLTS.get(gain.upper(), 1.0)
    return raw.astype(np.float64) / RAW_FULL_SCALE_COUNTS * full_scale


def load_in1_volts(path: Path, fallback_config: dict) -> tuple[dict, np.ndarray, np.ndarray]:
    metadata, t, data = load_rppr_for_plot(path, max_points=900_000)
    channels = list(metadata.get("channels") or [1])
    if 1 not in channels:
        raise RuntimeError("Plik kalibracyjny nie zawiera kanału IN1")
    gain = gain_for_in1(metadata, fallback_config)
    values = raw_to_volts(data[:, channels.index(1)], gain)
    metadata["in1_gain_used_for_volts"] = gain
    return metadata, t, values


def mad_sigma(values: np.ndarray) -> float:
    median = float(np.median(values))
    mad = float(np.median(np.abs(values - median)))
    return 1.4826 * mad


def propose_trigger(reference: np.ndarray, defect: np.ndarray, mode: str = "abs") -> tuple[float, dict]:
    if mode == "abs":
        reference = np.abs(reference)
        defect = np.abs(defect)

    ref_median = float(np.median(reference))
    ref_sigma = mad_sigma(reference)
    if mode == "signed":
        ref_p995 = float(np.percentile(reference, 99.5))
        ref_p005 = float(np.percentile(reference, 0.5))
        pos_noise = max(ref_p995, ref_median + 6.0 * ref_sigma)
        neg_noise = min(ref_p005, ref_median - 6.0 * ref_sigma)
        defect_p99 = float(np.percentile(defect, 99.0))
        defect_p01 = float(np.percentile(defect, 1.0))

        pos_gap = defect_p99 - pos_noise
        neg_gap = neg_noise - defect_p01
        if neg_gap > pos_gap and neg_gap > 0.0:
            proposed = neg_noise - 0.35 * neg_gap
            polarity = "negative"
            ref_noise = neg_noise
            defect_level = defect_p01
        elif pos_gap > 0.0:
            proposed = pos_noise + 0.35 * pos_gap
            polarity = "positive"
            ref_noise = pos_noise
            defect_level = defect_p99
        else:
            proposed = pos_noise if abs(pos_noise) >= abs(neg_noise) else neg_noise
            polarity = "none"
            ref_noise = proposed
            defect_level = defect_p99 if proposed >= 0 else defect_p01

        stats = {
            "mode": mode,
            "polarity": polarity,
            "reference_median_v": ref_median,
            "reference_mad_sigma_v": ref_sigma,
            "reference_p99_5_v": ref_p995,
            "reference_p0_5_v": ref_p005,
            "reference_noise_v": float(ref_noise),
            "defect_p99_v": defect_p99,
            "defect_p1_v": defect_p01,
            "defect_level_v": float(defect_level),
            "proposed_trigger_v": float(proposed),
        }
        return float(proposed), stats

    ref_p995 = float(np.percentile(reference, 99.5))
    ref_noise = max(ref_p995, ref_median + 6.0 * ref_sigma)
    defect_p99 = float(np.percentile(defect, 99.0))

    if defect_p99 > ref_noise:
        proposed = ref_noise + 0.35 * (defect_p99 - ref_noise)
    else:
        proposed = ref_noise

    stats = {
        "mode": mode,
        "polarity": "absolute",
        "reference_median_v": ref_median,
        "reference_mad_sigma_v": ref_sigma,
        "reference_p99_5_v": ref_p995,
        "reference_noise_v": ref_noise,
        "defect_p99_v": defect_p99,
        "defect_level_v": defect_p99,
        "proposed_trigger_v": float(proposed),
    }
    return float(proposed), stats


def launch_gui(config_path: Path, result_path: Path) -> int:
    try:
        from PySide6 import QtCore, QtWidgets
        import pyqtgraph as pg
    except ImportError as exc:
        print("Brak zależności GUI:", exc)
        print("Utwórz venv i zainstaluj: pip install -r receiver/requirements.txt")
        return 2

    base_config = json.loads(config_path.read_text(encoding="utf-8"))
    config = calibration_config(base_config)

    class AcquisitionWorker(QtCore.QObject):
        status = QtCore.Signal(str)
        finished = QtCore.Signal(str)
        failed = QtCore.Signal(str)

        def __init__(self, acquisition_config: dict):
            super().__init__()
            self.acquisition_config = dict(acquisition_config)

        @QtCore.Slot()
        def run(self) -> None:
            try:
                path = acquire_to_file(self.acquisition_config, status_cb=self.status.emit)
                self.finished.emit(str(path))
            except Exception as exc:
                self.failed.emit(str(exc))

    class CalibratorWindow(QtWidgets.QMainWindow):
        def __init__(self):
            super().__init__()
            self.setWindowTitle("Eksperymentalne wyznaczanie Triggera IN1")
            self.resize(1200, 860)
            self.reference_path: Path | None = None
            self.defect_path: Path | None = None
            self.reference_raw_values: np.ndarray | None = None
            self.defect_raw_values: np.ndarray | None = None
            self.reference_values: np.ndarray | None = None
            self.defect_values: np.ndarray | None = None
            self.reference_t: np.ndarray | None = None
            self.defect_t: np.ndarray | None = None
            self.algorithm_stats: dict = {}
            self.thread: QtCore.QThread | None = None
            self.worker: AcquisitionWorker | None = None
            self.pending_kind: str | None = None

            root = QtWidgets.QWidget()
            self.setCentralWidget(root)
            layout = QtWidgets.QVBoxLayout(root)

            toolbar = QtWidgets.QHBoxLayout()
            self.reference_btn = QtWidgets.QPushButton("Start: referencja bez defektu")
            self.defect_btn = QtWidgets.QPushButton("Start: pomiar z defektem")
            self.defect_btn.setEnabled(False)
            self.signal_mode = QtWidgets.QComboBox()
            self.signal_mode.addItem("ABS | abs(IN1)", "abs")
            self.signal_mode.addItem("+/- | IN1", "signed")
            self.trigger_value = QtWidgets.QDoubleSpinBox()
            self.trigger_value.setRange(0.0, 20.0)
            self.trigger_value.setDecimals(6)
            self.trigger_value.setSingleStep(0.001)
            self.trigger_value.setSuffix(" V")
            self.ok_btn = QtWidgets.QPushButton("OK")
            self.ok_btn.setEnabled(False)
            self.cancel_btn = QtWidgets.QPushButton("Anuluj")

            toolbar.addWidget(self.reference_btn)
            toolbar.addWidget(self.defect_btn)
            toolbar.addStretch(1)
            toolbar.addWidget(QtWidgets.QLabel("Tryb"))
            toolbar.addWidget(self.signal_mode)
            toolbar.addWidget(QtWidgets.QLabel("Poziom Triggera"))
            toolbar.addWidget(self.trigger_value)
            toolbar.addWidget(self.ok_btn)
            toolbar.addWidget(self.cancel_btn)
            layout.addLayout(toolbar)

            self.reference_plot = pg.PlotWidget()
            self.reference_plot.setLabel("bottom", "Czas", units="s")
            self.reference_plot.setLabel("left", "abs(IN1)", units="V")
            self.reference_plot.showGrid(x=True, y=True, alpha=0.25)
            self.reference_curve = self.reference_plot.plot([], [], pen=pg.mkPen("#1f77b4", width=1))
            self.reference_line = pg.InfiniteLine(angle=0, movable=False, pen=pg.mkPen("#ffcc00", width=2))
            self.reference_plot.addItem(self.reference_line)

            self.defect_plot = pg.PlotWidget()
            self.defect_plot.setLabel("bottom", "Czas", units="s")
            self.defect_plot.setLabel("left", "abs(IN1)", units="V")
            self.defect_plot.showGrid(x=True, y=True, alpha=0.25)
            self.defect_curve = self.defect_plot.plot([], [], pen=pg.mkPen("#d62728", width=1))
            self.defect_line = pg.InfiniteLine(angle=0, movable=False, pen=pg.mkPen("#ffcc00", width=2))
            self.defect_plot.addItem(self.defect_line)

            self.status = QtWidgets.QLabel("Najpierw zbierz referencję bez defektu.")
            self.stats = QtWidgets.QLabel("")
            self.stats.setWordWrap(True)

            layout.addWidget(QtWidgets.QLabel("Referencja bez defektu"))
            layout.addWidget(self.reference_plot, stretch=1)
            layout.addWidget(QtWidgets.QLabel("Pomiar z defektem"))
            layout.addWidget(self.defect_plot, stretch=1)
            layout.addWidget(self.stats)
            layout.addWidget(self.status)

            self.reference_btn.clicked.connect(lambda: self.start_acquisition("reference"))
            self.defect_btn.clicked.connect(lambda: self.start_acquisition("defect"))
            self.signal_mode.currentIndexChanged.connect(lambda *_: self.recompute_display())
            self.trigger_value.valueChanged.connect(lambda *_: self.update_trigger_lines())
            self.ok_btn.clicked.connect(self.accept_result)
            self.cancel_btn.clicked.connect(self.reject_result)
            self.update_trigger_lines()

        def start_acquisition(self, kind: str) -> None:
            self.pending_kind = kind
            self.reference_btn.setEnabled(False)
            self.defect_btn.setEnabled(False)
            self.ok_btn.setEnabled(False)
            self.status.setText("Trwa akwizycja...")

            self.thread = QtCore.QThread()
            self.worker = AcquisitionWorker(config)
            self.worker.moveToThread(self.thread)
            self.thread.started.connect(self.worker.run)
            self.worker.status.connect(self.status.setText)
            self.worker.finished.connect(self.on_acquisition_finished)
            self.worker.failed.connect(self.on_acquisition_failed)
            self.worker.finished.connect(self.thread.quit)
            self.worker.failed.connect(self.thread.quit)
            self.thread.finished.connect(self.thread.deleteLater)
            self.thread.start()

        def on_acquisition_finished(self, path_text: str) -> None:
            path = Path(path_text)
            try:
                metadata, t, values = load_in1_volts(path, config)
            except Exception as exc:
                self.on_acquisition_failed(str(exc))
                return

            if self.pending_kind == "reference":
                self.reference_path = path
                self.reference_t = t
                self.reference_raw_values = values
                self.defect_btn.setEnabled(True)
                self.status.setText("Referencja odebrana. Teraz zbierz pomiar z defektem.")
            else:
                self.defect_path = path
                self.defect_t = t
                self.defect_raw_values = values
                self.status.setText("Pomiar z defektem odebrany.")

            self.recompute_display()

            self.reference_btn.setEnabled(True)
            if self.reference_raw_values is not None:
                self.defect_btn.setEnabled(True)
            self.update_trigger_lines()

        def on_acquisition_failed(self, message: str) -> None:
            self.status.setText(f"Błąd: {message}")
            QtWidgets.QMessageBox.critical(self, "Akwizycja nie powiodła się", message)
            self.reference_btn.setEnabled(True)
            if self.reference_raw_values is not None:
                self.defect_btn.setEnabled(True)
            if self.reference_raw_values is not None and self.defect_raw_values is not None:
                self.ok_btn.setEnabled(True)

        def current_mode(self) -> str:
            return str(self.signal_mode.currentData())

        def values_for_mode(self, values: np.ndarray) -> np.ndarray:
            if self.current_mode() == "abs":
                return np.abs(values)
            return values

        def recompute_display(self) -> None:
            mode = self.current_mode()
            signed = mode == "signed"
            self.trigger_value.setRange(-20.0 if signed else 0.0, 20.0)
            y_label = "IN1" if signed else "abs(IN1)"
            self.reference_plot.setLabel("left", y_label, units="V")
            self.defect_plot.setLabel("left", y_label, units="V")

            if self.reference_raw_values is not None and self.reference_t is not None:
                self.reference_values = self.values_for_mode(self.reference_raw_values)
                self.reference_curve.setData(self.reference_t, self.reference_values)
            if self.defect_raw_values is not None and self.defect_t is not None:
                self.defect_values = self.values_for_mode(self.defect_raw_values)
                self.defect_curve.setData(self.defect_t, self.defect_values)

            if self.reference_raw_values is not None and self.defect_raw_values is not None:
                proposed, stats = propose_trigger(self.reference_raw_values, self.defect_raw_values, mode)
                self.algorithm_stats = stats
                self.trigger_value.setValue(proposed)
                self.ok_btn.setEnabled(True)
                label = "ABS" if mode == "abs" else "+/-"
                self.stats.setText(
                    f"Tryb: {label} | "
                    f"propozycja={proposed:.6f} V | "
                    f"tło={stats['reference_noise_v']:.6f} V | "
                    f"defekt={stats['defect_level_v']:.6f} V | "
                    f"polaryzacja={stats['polarity']}"
                )
            self.update_trigger_lines()

        def update_trigger_lines(self) -> None:
            value = float(self.trigger_value.value())
            self.reference_line.setValue(value)
            self.defect_line.setValue(value)

        def write_result(self, accepted: bool) -> None:
            payload = {
                "accepted": accepted,
                "trigger_level_v": float(self.trigger_value.value()),
                "signal_mode": self.current_mode(),
                "reference_file": str(self.reference_path) if self.reference_path else None,
                "defect_file": str(self.defect_path) if self.defect_path else None,
                "algorithm_stats": self.algorithm_stats,
            }
            result_path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")

        def accept_result(self) -> None:
            self.write_result(True)
            self.close()

        def reject_result(self) -> None:
            self.write_result(False)
            self.close()

        def closeEvent(self, event) -> None:
            if not result_path.exists():
                self.write_result(False)
            event.accept()

    app = QtWidgets.QApplication(sys.argv)
    pg.setConfigOptions(antialias=False)
    window = CalibratorWindow()
    window.show()
    return app.exec()


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="IN1 trigger-level calibrator")
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--result", required=True, type=Path)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    return launch_gui(args.config, args.result)


if __name__ == "__main__":
    raise SystemExit(main())
