# infer_6defects.py
# Inference for MobileNetV2 + YOLO-like Head - 6 PRPD defects

import torch
import torch.nn as nn
from torchvision import transforms, models
from pathlib import Path
import cv2
import numpy as np
import argparse

NUM_CLASSES = 6
NUM_ANCHORS = 9
GRID_SIZE = 7
OUTPUT_SIZE = NUM_ANCHORS * (5 + NUM_CLASSES)
IMAGE_SIZE = 224

CLASS_NAMES = [
    "floating",
    "ncorona",
    "noise",
    "pcorona",
    "surface",
    "void"
]

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


class MobileNetYOLO(nn.Module):
    def __init__(self):
        super(MobileNetYOLO, self).__init__()

        self.backbone = models.mobilenet_v2(weights=None).features

        self.conv_head = nn.Sequential(
            nn.Conv2d(1280, 256, kernel_size=1),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Conv2d(256, OUTPUT_SIZE, kernel_size=1)
        )

    def forward(self, x):
        x = self.backbone(x)
        x = self.conv_head(x)
        x = torch.sigmoid(x)
        x = x.permute(0, 2, 3, 1)
        return x


def load_image(image_path):
    image_gray = cv2.imread(str(image_path), cv2.IMREAD_GRAYSCALE)

    if image_gray is None:
        raise RuntimeError(f"Nie można wczytać obrazu: {image_path}")

    image_resized = cv2.resize(image_gray, (IMAGE_SIZE, IMAGE_SIZE))
    image_rgb = np.stack([image_resized] * 3, axis=2)

    transform = transforms.Compose([
        transforms.ToTensor(),
        transforms.Normalize([0.5] * 3, [0.5] * 3)
    ])

    tensor = transform(image_rgb).unsqueeze(0)

    return tensor, image_resized


def infer_image(model, image_path, device):
    tensor, image = load_image(image_path)
    tensor = tensor.to(device)

    with torch.no_grad():
        preds = model(tensor)
        preds = preds.reshape(1, GRID_SIZE, GRID_SIZE, NUM_ANCHORS, 5 + NUM_CLASSES)

    preds = preds[0].cpu()

    obj_scores = preds[..., 4]
    cls_scores = preds[..., 5:]

    combined_scores = obj_scores.unsqueeze(-1) * cls_scores

    best_index = torch.argmax(combined_scores)
    best_index = np.unravel_index(best_index.item(), combined_scores.shape)

    gy, gx, anchor, cls_id = best_index

    confidence = combined_scores[gy, gx, anchor, cls_id].item()
    objectness = obj_scores[gy, gx, anchor].item()
    class_probability = cls_scores[gy, gx, anchor, cls_id].item()

    class_name = CLASS_NAMES[cls_id]

    return {
        "image_path": str(image_path),
        "class_id": int(cls_id),
        "class_name": class_name,
        "confidence": confidence,
        "objectness": objectness,
        "class_probability": class_probability
    }


def main():
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--weights",
        type=str,
        default="mobilenet_yolo_prpd_6defects_2026.pt",
        help="Ścieżka do pliku .pt"
    )

    parser.add_argument(
        "--source",
        type=str,
        required=True,
        help="Ścieżka do obrazu albo folderu z obrazami"
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
        raise FileNotFoundError(f"Nie znaleziono modelu: {weights_path}")

    if not source_path.exists():
        raise FileNotFoundError(f"Nie znaleziono źródła: {source_path}")

    if args.cpu:
        device = torch.device("cpu")
    else:
        device = torch.device(
            "cuda" if torch.cuda.is_available() else "cpu"
        )

    model = MobileNetYOLO().to(device)
    model.load_state_dict(torch.load(weights_path, map_location=device))
    model.eval()

    if args.onnx is not None:

        onnx_path = Path(args.onnx)

        if not onnx_path.is_absolute():
            onnx_path = base_dir / onnx_path

        dummy_input = torch.randn(
            1,
            3,
            IMAGE_SIZE,
            IMAGE_SIZE
        ).to("cpu")

        export_model = ONNXWrapper( model.to("cpu")).eval()

        print(f"Eksport ONNX: {onnx_path}")

        with torch.no_grad():
            traced = torch.jit.trace(
                export_model,
                dummy_input,
                strict=False
            )

            torch.onnx.export(
                export_model,
                dummy_input,
                str(onnx_path),
                input_names=["input"],
                output_names=["permute"],
                opset_version=11,
                dynamo=False
            )

        print("ONNX zapisany.")

    print(f"Model: {weights_path}")
    print(f"Źródło: {source_path}")
    print(f"Urządzenie: {device}")
    print("-" * 70)

    if source_path.is_file():
        image_paths = [source_path]
    else:
        image_paths = []
        for ext in ("*.png", "*.jpg", "*.jpeg", "*.bmp"):
            image_paths.extend(source_path.rglob(ext))

        image_paths = sorted(image_paths)

    if len(image_paths) == 0:
        print("Nie znaleziono obrazów.")
        return

    for image_path in image_paths:
        result = infer_image(model, image_path, device)

        print(
            f"{Path(result['image_path']).name} -> "
            f"{result['class_name']} | "
            f"confidence: {result['confidence']:.4f} | "
            f"objectness: {result['objectness']:.4f} | "
            f"class_prob: {result['class_probability']:.4f}"
        )


if __name__ == "__main__":
    main()
