# infer_rtdetr_prpd.py
# Inferencja RT-DETR dla PRPD
# Obsługuje:
# - pojedynczy obraz
# - folder obrazów
# - confidence threshold
# - zapis wyników

from pathlib import Path
import argparse

from ultralytics import RTDETR


# ============================================================
# ARGUMENTS
# ============================================================

parser = argparse.ArgumentParser()

parser.add_argument(
    "--weights",
    type=str,
    required=True,
    help="Ścieżka do best.pt"
)

parser.add_argument(
    "--source",
    type=str,
    required=True,
    help="Obraz lub folder obrazów"
)

parser.add_argument(
    "--conf",
    type=float,
    default=0.25,
    help="Confidence threshold"
)

parser.add_argument(
    "--imgsz",
    type=int,
    default=224,
    help="Rozmiar wejściowy"
)

parser.add_argument(
    "--onnx",
    type=str,
    default=None,
    help="Eksport modelu do ONNX"
)

args = parser.parse_args()


# ============================================================
# LOAD MODEL
# ============================================================

weights_path = Path(args.weights)

if not weights_path.exists():
    raise FileNotFoundError(f"Nie znaleziono wag: {weights_path}")

print(f"\nŁadowanie modelu:\n{weights_path}")

model = RTDETR(str(weights_path))

if args.onnx is not None:
    onnx_path = Path(args.onnx)

    print(f"\nEksport ONNX do: {onnx_path}")

    exported = model.export(
        format="onnx",
        imgsz=args.imgsz,
        opset=11,
        simplify=True,
        dynamic=False
    )

    exported = Path(exported)

    if onnx_path.suffix.lower() != ".onnx":
        onnx_path = onnx_path.with_suffix(".onnx")

    onnx_path.parent.mkdir(parents=True, exist_ok=True)
    exported.rename(onnx_path)

    print(f"ONNX zapisany jako: {onnx_path}")

# ============================================================
# OUTPUT
# ============================================================

output_dir = Path("rtdetr_inference_results")
output_dir.mkdir(parents=True, exist_ok=True)


# ============================================================
# PREDICT
# ============================================================

print("\nStart inferencji...")
print(f"Source: {args.source}")
print(f"Confidence threshold: {args.conf}")

results = model.predict(
    source=args.source,
    imgsz=args.imgsz,
    conf=args.conf,
    save=True,
    save_txt=True,
    save_conf=True,
    project=str(output_dir),
    name="predictions",
    line_width=2,
    show=False
)

print("\nInferencja zakończona.")
print(f"Wyniki zapisano w:\n{output_dir / 'predictions'}")


# ============================================================
# PRINT DETECTIONS
# ============================================================

print("\n=== DETECTIONS ===")

for r in results:

    image_name = Path(r.path).name

    print(f"\nObraz: {image_name}")

    boxes = r.boxes

    if boxes is None or len(boxes) == 0:
        print("Brak detekcji.")
        continue

    for box in boxes:

        cls_id = int(box.cls[0].item())
        conf = float(box.conf[0].item())

        class_name = model.names[cls_id]

        xyxy = box.xyxy[0].tolist()

        print(
            f"Klasa: {class_name:10s} | "
            f"Conf: {conf:.3f} | "
            f"BBOX: {[round(v,1) for v in xyxy]}"
        )

print("\nGotowe.")
