# infer_squeezenet_yolo_6defects.py
# Inference for SqueezeNet + YOLO-like head - 6 PRPD defects

from pathlib import Path
import argparse
import time

import cv2
import numpy as np

import torch
import torch.nn as nn
from torchvision import transforms, models
import torch.nn.functional as F


# ============================================================
# CONFIG
# ============================================================

NUM_CLASSES = 6
CLASS_NAMES = [
    "floating",
    "ncorona",
    "noise",
    "pcorona",
    "surface",
    "void"
]

NUM_ANCHORS = 9
GRID_SIZE = 7
OUTPUT_SIZE = NUM_ANCHORS * (5 + NUM_CLASSES)
IMAGE_SIZE = 224

class ONNXWrapper(nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, x):
        y = self.model(x)

        if isinstance(y, (list, tuple)):
            y = y[0]

        return y.reshape(
            1,
            GRID_SIZE,
            GRID_SIZE,
            OUTPUT_SIZE
        ).contiguous()

class SqueezeNetYOLO_ONNX(nn.Module):
    def __init__(self, original):
        super().__init__()
        self.backbone = original.backbone
        self.yolo_head = original.yolo_head

    def forward(self, x):
        x = self.backbone(x)
        x = F.interpolate(
            x,
            size=(GRID_SIZE, GRID_SIZE),
            mode="bilinear",
            align_corners=False
        )
        x = self.yolo_head(x)
        x = torch.sigmoid(x)
        x = x.permute(0, 2, 3, 1)
        return x.reshape(1, GRID_SIZE, GRID_SIZE, OUTPUT_SIZE).contiguous()


# ============================================================
# MODEL - SQUEEZENET + YOLO HEAD
# ============================================================

class SqueezeNetYOLO(nn.Module):
    def __init__(self):
        super().__init__()

        sq = models.squeezenet1_1(weights=None)

        self.backbone = sq.features

        self.pool_to_grid = nn.AdaptiveAvgPool2d((GRID_SIZE, GRID_SIZE))

        self.yolo_head = nn.Sequential(
            nn.Conv2d(512, 256, kernel_size=1),
            nn.ReLU(inplace=True),
            nn.Dropout(p=0.2),
            nn.Conv2d(256, OUTPUT_SIZE, kernel_size=1),
        )

    def forward(self, x):
        x = self.backbone(x)
        x = self.pool_to_grid(x)
        x = self.yolo_head(x)
        x = torch.sigmoid(x)
        x = x.permute(0, 2, 3, 1)
        return x


# ============================================================
# IMAGE LOADING
# ============================================================

def load_image(image_path):
    image_gray = cv2.imread(str(image_path), cv2.IMREAD_GRAYSCALE)

    if image_gray is None:
        raise RuntimeError(f"Nie można wczytać obrazu: {image_path}")

    original = image_gray.copy()

    image_resized = cv2.resize(image_gray, (IMAGE_SIZE, IMAGE_SIZE))
    image_rgb = np.stack([image_resized] * 3, axis=2)

    transform = transforms.Compose([
        transforms.ToTensor(),
        transforms.Normalize([0.5] * 3, [0.5] * 3)
    ])

    tensor = transform(image_rgb).unsqueeze(0)

    return tensor, original


# ============================================================
# INFERENCE
# ============================================================

def infer_image(model, image_path, device, conf_th=0.25):
    tensor, original = load_image(image_path)
    tensor = tensor.to(device)

    t0 = time.time()

    with torch.no_grad():
        preds = model(tensor)
        preds = preds.reshape(
            1,
            GRID_SIZE,
            GRID_SIZE,
            NUM_ANCHORS,
            5 + NUM_CLASSES
        )

    inference_time = time.time() - t0

    preds = preds[0].cpu()

    obj_scores = preds[..., 4]
    cls_scores = preds[..., 5:]

    combined_scores = obj_scores.unsqueeze(-1) * cls_scores

    detections = []

    for gy in range(GRID_SIZE):
        for gx in range(GRID_SIZE):
            for a in range(NUM_ANCHORS):
                for cls_id in range(NUM_CLASSES):

                    confidence = combined_scores[gy, gx, a, cls_id].item()

                    if confidence < conf_th:
                        continue

                    x, y, w, h = preds[gy, gx, a, :4].numpy()

                    detections.append({
                        "class_id": int(cls_id),
                        "class_name": CLASS_NAMES[cls_id],
                        "confidence": float(confidence),
                        "objectness": float(obj_scores[gy, gx, a].item()),
                        "class_probability": float(cls_scores[gy, gx, a, cls_id].item()),
                        "bbox_yolo": [float(x), float(y), float(w), float(h)],
                        "grid": [int(gx), int(gy)],
                        "anchor": int(a),
                    })

    detections = sorted(detections, key=lambda d: d["confidence"], reverse=True)

    # Jeżeli nic nie przekroczyło progu, zwróć najlepszy wynik
    if len(detections) == 0:
        best_index = torch.argmax(combined_scores)
        best_index = np.unravel_index(best_index.item(), combined_scores.shape)

        gy, gx, anchor, cls_id = best_index

        confidence = combined_scores[gy, gx, anchor, cls_id].item()
        objectness = obj_scores[gy, gx, anchor].item()
        class_probability = cls_scores[gy, gx, anchor, cls_id].item()

        x, y, w, h = preds[gy, gx, anchor, :4].numpy()

        detections.append({
            "class_id": int(cls_id),
            "class_name": CLASS_NAMES[cls_id],
            "confidence": float(confidence),
            "objectness": float(objectness),
            "class_probability": float(class_probability),
            "bbox_yolo": [float(x), float(y), float(w), float(h)],
            "grid": [int(gx), int(gy)],
            "anchor": int(anchor),
            "best_below_threshold": True,
        })

    return detections, inference_time


# ============================================================
# DRAW RESULTS
# ============================================================

def yolo_to_xyxy(bbox, img_w, img_h):
    x, y, w, h = bbox

    x1 = int((x - w / 2) * img_w)
    y1 = int((y - h / 2) * img_h)
    x2 = int((x + w / 2) * img_w)
    y2 = int((y + h / 2) * img_h)

    x1 = max(0, min(img_w - 1, x1))
    y1 = max(0, min(img_h - 1, y1))
    x2 = max(0, min(img_w - 1, x2))
    y2 = max(0, min(img_h - 1, y2))

    return x1, y1, x2, y2


def draw_detections(image_path, detections, output_path, max_draw=5):
    image = cv2.imread(str(image_path), cv2.IMREAD_GRAYSCALE)

    if image is None:
        return

    image = cv2.resize(image, (IMAGE_SIZE, IMAGE_SIZE))
    image = cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)

    for det in detections[:max_draw]:
        x1, y1, x2, y2 = yolo_to_xyxy(
            det["bbox_yolo"],
            IMAGE_SIZE,
            IMAGE_SIZE
        )

        label = f'{det["class_name"]} {det["confidence"]:.2f}'

        cv2.rectangle(image, (x1, y1), (x2, y2), (0, 255, 0), 2)
        cv2.putText(
            image,
            label,
            (x1, max(15, y1 - 5)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.45,
            (0, 255, 0),
            1,
            cv2.LINE_AA
        )

    cv2.imwrite(str(output_path), image)


# ============================================================
# MAIN
# ============================================================

def main():
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--weights",
        type=str,
        default="squeezenet_yolo_prpd_6defects_2026.pt",
        help="Ścieżka do wag .pt"
    )

    parser.add_argument(
        "--source",
        type=str,
        required=True,
        help="Obraz albo folder z obrazami"
    )

    parser.add_argument(
        "--conf",
        type=float,
        default=0.25,
        help="Próg confidence"
    )

    parser.add_argument(
        "--save",
        action="store_true",
        help="Zapisz obrazy z predykcjami"
    )

    parser.add_argument(
        "--onnx",
        type=str,
        default=None,
        help="Zapis modelu do ONNX"
    )

    parser.add_argument(
        "--cpu",
        action="store_true",
        help="Wymuś CPU"
    )

    args = parser.parse_args()

    base_dir = Path(__file__).resolve().parent

    weights_path = Path(args.weights)
    source_path = Path(args.source)

    if not weights_path.is_absolute():
        weights_path = base_dir / weights_path

    if not source_path.is_absolute():
        source_path = base_dir / source_path

    if not weights_path.exists():
        raise FileNotFoundError(f"Nie znaleziono wag: {weights_path}")

    if not source_path.exists():
        raise FileNotFoundError(f"Nie znaleziono źródła: {source_path}")

    device = torch.device( "cpu" if args.cpu else ("cuda" if torch.cuda.is_available() else "cpu"))

    model = SqueezeNetYOLO().to(device)
    model.load_state_dict(torch.load(weights_path, map_location=device))
    model.eval()

    if args.onnx is not None:

        onnx_path = Path(args.onnx)

        if not onnx_path.is_absolute():
            onnx_path = base_dir / onnx_path

        print(f"Eksport ONNX: {onnx_path}")

        export_model = SqueezeNetYOLO_ONNX(model.to("cpu")).eval()
        dummy_input = torch.randn(1, 3, IMAGE_SIZE, IMAGE_SIZE)

        torch.onnx.export(
            export_model,
            dummy_input,
            str(onnx_path),
            input_names=["input"],
            output_names=["permute"],
            opset_version=18,
            dynamo=False
        )

        print("ONNX zapisany.")

    print("Model:", weights_path)
    print("Source:", source_path)
    print("Device:", device)
    print("-" * 80)

    if source_path.is_file():
        image_paths = [source_path]
    else:
        image_paths = []
        for ext in ("*.png", "*.jpg", "*.jpeg", "*.bmp", "*.PNG", "*.JPG", "*.JPEG"):
            image_paths.extend(source_path.rglob(ext))
        image_paths = sorted(image_paths)

    if len(image_paths) == 0:
        print("Nie znaleziono obrazów.")
        return

    output_dir = base_dir / "squeezenet_yolo_predictions"
    if args.save:
        output_dir.mkdir(parents=True, exist_ok=True)

    total_time = 0.0

    for image_path in image_paths:
        detections, infer_time = infer_image(
            model,
            image_path,
            device,
            conf_th=args.conf
        )

        total_time += infer_time

        best = detections[0]

        print(
            f"{image_path.name} -> "
            f"{best['class_name']} | "
            f"conf={best['confidence']:.4f} | "
            f"obj={best['objectness']:.4f} | "
            f"cls_prob={best['class_probability']:.4f} | "
            f"time={infer_time * 1000:.2f} ms"
        )

        if len(detections) > 1:
            for det in detections[1:5]:
                print(
                    f"   + {det['class_name']} | "
                    f"conf={det['confidence']:.4f} | "
                    f"bbox={det['bbox_yolo']}"
                )

        if args.save:
            out_path = output_dir / f"{image_path.stem}_pred.png"
            draw_detections(image_path, detections, out_path)

    avg_time = total_time / len(image_paths)
    fps = 1.0 / avg_time if avg_time > 0 else 0

    print("-" * 80)
    print(f"Liczba obrazów: {len(image_paths)}")
    print(f"Średni czas inferencji: {avg_time * 1000:.2f} ms")
    print(f"FPS: {fps:.2f}")

    if args.save:
        print(f"Zapisano predykcje w: {output_dir}")


if __name__ == "__main__":
    main()
