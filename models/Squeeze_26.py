# SqueezeNet_2026_6defects_YOLO_head.py
# SqueezeNet + YOLO-like detection head for 6 PRPD defect classes

import os
import warnings
from pathlib import Path

import cv2
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, Dataset
from torchvision import transforms, models

from sklearn.metrics import (
    confusion_matrix,
    ConfusionMatrixDisplay,
    precision_recall_fscore_support,
    classification_report,
)

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
BATCH_SIZE = 8
MAX_EPOCHS = 30
LEARNING_RATE = 1e-4
EARLY_STOPPING_PATIENCE = 6

BASE_DIR = Path(__file__).resolve().parent
DATASET_DIR = BASE_DIR / "dataset_yolo_6defects"

TRAIN_IMG_DIR = DATASET_DIR / "images" / "train"
TRAIN_LBL_DIR = DATASET_DIR / "labels" / "train"
VAL_IMG_DIR = DATASET_DIR / "images" / "val"
VAL_LBL_DIR = DATASET_DIR / "labels" / "val"

OUTPUT_DIR = BASE_DIR / "squeezenet_yolo_6defects_results"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

MODEL_PATH = OUTPUT_DIR / "squeezenet_yolo_prpd_6defects_2026.pt"
TRAINING_PLOT_PATH = OUTPUT_DIR / "training_plot_squeezenet_yolo_6defects.png"
CONFUSION_MATRIX_PNG = OUTPUT_DIR / "confusion_matrix_squeezenet_yolo_6defects.png"
CONFUSION_MATRIX_CSV = OUTPUT_DIR / "confusion_matrix_squeezenet_yolo_6defects.csv"
PER_CLASS_CSV = OUTPUT_DIR / "per_class_metrics_squeezenet_yolo_6defects.csv"
CLASSIFICATION_REPORT_TXT = OUTPUT_DIR / "classification_report_squeezenet_yolo_6defects.txt"
WEIGHTS_TXT = OUTPUT_DIR / "weights_squeezenet_yolo_6defects.txt"


# ============================================================
# DATASET YOLO
# ============================================================

class PRPDYOLODataset(Dataset):
    def __init__(self, image_dir, label_dir, transform=None):
        self.image_dir = Path(image_dir)
        self.label_dir = Path(label_dir)
        self.transform = transform
        self.image_paths = []

        for ext in ("*.png", "*.jpg", "*.jpeg", "*.PNG", "*.JPG", "*.JPEG"):
            self.image_paths.extend(self.image_dir.glob(ext))

        self.image_paths = sorted(self.image_paths)

        valid_paths = []
        for img_path in self.image_paths:
            lbl_path = self.label_dir / f"{img_path.stem}.txt"
            if lbl_path.exists():
                valid_paths.append(img_path)

        self.image_paths = valid_paths

        if len(self.image_paths) == 0:
            warnings.warn(f"Dataset pusty: {self.image_dir}")

    def __len__(self):
        return len(self.image_paths)

    def __getitem__(self, idx):
        img_path = self.image_paths[idx]
        lbl_path = self.label_dir / f"{img_path.stem}.txt"

        image = cv2.imread(str(img_path), cv2.IMREAD_GRAYSCALE)
        if image is None:
            raise RuntimeError(f"Nie można wczytać obrazu: {img_path}")

        image = cv2.resize(image, (IMAGE_SIZE, IMAGE_SIZE))
        image = np.stack([image] * 3, axis=2)

        if self.transform:
            image = self.transform(image)

        targets = torch.zeros((GRID_SIZE, GRID_SIZE, NUM_ANCHORS, 5 + NUM_CLASSES))

        with open(lbl_path, "r", encoding="utf-8") as f:
            for line in f:
                parts = line.strip().split()

                if len(parts) != 5:
                    continue

                cls = int(float(parts[0]))
                x, y, w, h = map(float, parts[1:5])

                if not (0 <= cls < NUM_CLASSES):
                    continue

                cx = min(int(x * GRID_SIZE), GRID_SIZE - 1)
                cy = min(int(y * GRID_SIZE), GRID_SIZE - 1)

                for a in range(NUM_ANCHORS):
                    targets[cy, cx, a, :4] = torch.tensor([x, y, w, h])
                    targets[cy, cx, a, 4] = 1.0
                    targets[cy, cx, a, 5 + cls] = 1.0

        return image, targets


# ============================================================
# SQUEEZENET + YOLO HEAD
# ============================================================

class SqueezeNetYOLO(nn.Module):
    def __init__(self):
        super().__init__()

        sq = models.squeezenet1_1(weights=None)

        # Zostawiamy tylko feature extractor SqueezeNet.
        # Dla wejścia 224x224 daje mapę cech około 13x13.
        self.backbone = sq.features

        self.yolo_head = nn.Sequential(
            nn.Conv2d(512, 256, kernel_size=1),
            nn.ReLU(inplace=True),
            nn.Dropout(p=0.2),
            nn.Conv2d(256, OUTPUT_SIZE, kernel_size=1),
        )

        self.pool_to_grid = nn.AdaptiveAvgPool2d((GRID_SIZE, GRID_SIZE))

    def forward(self, x):
        x = self.backbone(x)
        x = self.pool_to_grid(x)
        x = self.yolo_head(x)
        x = torch.sigmoid(x)
        x = x.permute(0, 2, 3, 1)
        return x


# ============================================================
# LOSS
# ============================================================

class YOLOLikeLoss(nn.Module):
    def __init__(self):
        super().__init__()
        self.bce = nn.BCELoss()
        self.mse = nn.MSELoss()

    def forward(self, preds, targets):
        mask = targets[..., 4] == 1.0

        if mask.sum() > 0:
            bbox_loss = self.mse(preds[mask][..., :4], targets[mask][..., :4])
            cls_loss = self.bce(preds[mask][..., 5:], targets[mask][..., 5:])
        else:
            bbox_loss = torch.tensor(0.0, device=preds.device)
            cls_loss = torch.tensor(0.0, device=preds.device)

        obj_loss = self.bce(preds[..., 4], targets[..., 4])

        return bbox_loss + obj_loss + cls_loss


# ============================================================
# METRICS
# ============================================================

def safe_div(a, b):
    return float(a) / float(b) if b != 0 else 0.0


def extract_classes_from_yolo(preds, targets):
    mask = targets[..., 4] == 1.0

    if mask.sum() == 0:
        return [], []

    pred_classes = torch.argmax(preds[mask][..., 5:], dim=-1)
    true_classes = torch.argmax(targets[mask][..., 5:], dim=-1)

    return true_classes.cpu().numpy().tolist(), pred_classes.cpu().numpy().tolist()


def plot_training(train_losses, val_losses, val_accuracies):
    plt.figure(figsize=(11, 7))
    plt.plot(train_losses, label="Train Loss", linewidth=2)
    plt.plot(val_losses, label="Val Loss", linewidth=2)
    plt.plot(val_accuracies, label="Val Accuracy [%]", linewidth=2)
    plt.title("Training Progress - SqueezeNet YOLO Head")
    plt.xlabel("Epoch")
    plt.ylabel("Loss / Accuracy")
    plt.legend()
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig(TRAINING_PLOT_PATH, dpi=300, bbox_inches="tight")
    plt.close()


def save_metrics(y_true, y_pred):
    cm = confusion_matrix(y_true, y_pred, labels=list(range(NUM_CLASSES)))

    precision, recall, f1, support = precision_recall_fscore_support(
        y_true,
        y_pred,
        labels=list(range(NUM_CLASSES)),
        average=None,
        zero_division=0
    )

    rows = []
    total = int(cm.sum())

    for c in range(NUM_CLASSES):
        tp = int(cm[c, c])
        fp = int(cm[:, c].sum() - tp)
        fn = int(cm[c, :].sum() - tp)
        tn = int(total - tp - fp - fn)

        rows.append({
            "class_id": c,
            "class_name": CLASS_NAMES[c],
            "support": int(support[c]),
            "TP": tp,
            "FP": fp,
            "FN": fn,
            "TN_one_vs_rest": tn,
            "precision": round(float(precision[c]), 6),
            "recall": round(float(recall[c]), 6),
            "f1_score": round(float(f1[c]), 6),
        })

    metrics_df = pd.DataFrame(rows)
    metrics_df.to_csv(PER_CLASS_CSV, index=False, encoding="utf-8-sig")

    cm_df = pd.DataFrame(cm, index=CLASS_NAMES, columns=CLASS_NAMES)
    cm_df.to_csv(CONFUSION_MATRIX_CSV, encoding="utf-8-sig")

    report = classification_report(
        y_true,
        y_pred,
        labels=list(range(NUM_CLASSES)),
        target_names=CLASS_NAMES,
        digits=6,
        zero_division=0
    )

    with open(CLASSIFICATION_REPORT_TXT, "w", encoding="utf-8") as f:
        f.write("Classification report - SqueezeNet YOLO Head\n")
        f.write("===========================================\n\n")
        f.write(report)
        f.write("\n\nConfusion matrix\n")
        f.write("================\n")
        f.write(cm_df.to_string())

    fig, ax = plt.subplots(figsize=(10, 8))
    disp = ConfusionMatrixDisplay(confusion_matrix=cm, display_labels=CLASS_NAMES)
    disp.plot(ax=ax, cmap="Blues", colorbar=True, values_format="d")
    ax.set_title("Confusion Matrix - SqueezeNet YOLO Head")
    ax.set_xlabel("Predicted class")
    ax.set_ylabel("True class")
    plt.xticks(rotation=35, ha="right")
    plt.tight_layout()
    plt.savefig(CONFUSION_MATRIX_PNG, dpi=300, bbox_inches="tight")
    plt.close()

    print("\n=== PER-CLASS METRICS ===")
    print(metrics_df.to_string(index=False))

    print("\n=== CONFUSION MATRIX ===")
    print(cm_df.to_string())

    print("\n=== CLASSIFICATION REPORT ===")
    print(report)


# ============================================================
# EXPORT WEIGHTS TO TXT
# ============================================================

def export_weights_to_txt(model_path, output_txt):
    print("\nEksport wag do TXT...")

    state_dict = torch.load(model_path, map_location="cpu")

    total_params = 0

    with open(output_txt, "w", encoding="utf-8") as f:
        f.write("EXPORT WAG MODELU - SQUEEZENET YOLO HEAD\n")
        f.write("=" * 100 + "\n\n")
        f.write(f"Model: {model_path}\n")
        f.write(f"Liczba tensorów: {len(state_dict)}\n\n")

        for name, tensor in state_dict.items():
            if not torch.is_tensor(tensor):
                continue

            params = tensor.numel()
            total_params += params

            f.write("=" * 100 + "\n")
            f.write(f"NAZWA WARSTWY: {name}\n")
            f.write(f"Shape: {list(tensor.shape)}\n")
            f.write(f"Liczba parametrów: {params}\n\n")

            flat = tensor.flatten().numpy()

            f.write("Pierwsze 200 wartości wag:\n")
            for i, value in enumerate(flat[:200]):
                f.write(f"{value:.10f} ")
                if (i + 1) % 10 == 0:
                    f.write("\n")

            f.write("\n\n")

        f.write("=" * 100 + "\n")
        f.write(f"SUMA PARAMETRÓW: {total_params}\n")
        f.write("=" * 100 + "\n")

    print(f"Zapisano wagi TXT: {output_txt}")


# ============================================================
# TRAINING
# ============================================================

def train_model():
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print("Device:", device)

    print("Dataset:")
    print("Train images:", TRAIN_IMG_DIR)
    print("Train labels:", TRAIN_LBL_DIR)
    print("Val images:", VAL_IMG_DIR)
    print("Val labels:", VAL_LBL_DIR)

    transform = transforms.Compose([
        transforms.ToTensor(),
        transforms.Normalize([0.5] * 3, [0.5] * 3)
    ])

    train_ds = PRPDYOLODataset(TRAIN_IMG_DIR, TRAIN_LBL_DIR, transform=transform)
    val_ds = PRPDYOLODataset(VAL_IMG_DIR, VAL_LBL_DIR, transform=transform)

    print(f"Train samples: {len(train_ds)}")
    print(f"Val samples:   {len(val_ds)}")

    if len(train_ds) == 0 or len(val_ds) == 0:
        raise RuntimeError("Train albo val dataset jest pusty. Sprawdź folder dataset_yolo_6defects.")

    train_loader = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True)
    val_loader = DataLoader(val_ds, batch_size=BATCH_SIZE, shuffle=False)

    model = SqueezeNetYOLO().to(device)
    criterion = YOLOLikeLoss()
    optimizer = optim.Adam(model.parameters(), lr=LEARNING_RATE, weight_decay=1e-5)

    train_losses = []
    val_losses = []
    val_accuracies = []

    best_val_acc = 0.0
    best_val_loss = float("inf")
    epochs_without_improvement = 0

    best_y_true = []
    best_y_pred = []

    for epoch in range(MAX_EPOCHS):
        model.train()
        running_train_loss = 0.0

        for images, targets in train_loader:
            images = images.to(device)
            targets = targets.to(device)

            preds = model(images)
            preds = preds.reshape(-1, GRID_SIZE, GRID_SIZE, NUM_ANCHORS, 5 + NUM_CLASSES)

            loss = criterion(preds, targets)

            optimizer.zero_grad()
            loss.backward()
            optimizer.step()

            running_train_loss += loss.item()

        avg_train_loss = running_train_loss / len(train_loader)

        model.eval()
        running_val_loss = 0.0
        correct = 0
        total = 0

        y_true = []
        y_pred = []

        with torch.no_grad():
            for images, targets in val_loader:
                images = images.to(device)
                targets = targets.to(device)

                preds = model(images)
                preds = preds.reshape(-1, GRID_SIZE, GRID_SIZE, NUM_ANCHORS, 5 + NUM_CLASSES)

                loss = criterion(preds, targets)
                running_val_loss += loss.item()

                true_cls, pred_cls = extract_classes_from_yolo(preds, targets)

                y_true.extend(true_cls)
                y_pred.extend(pred_cls)

                for t, p in zip(true_cls, pred_cls):
                    if t == p:
                        correct += 1
                    total += 1

        avg_val_loss = running_val_loss / len(val_loader)
        val_acc = safe_div(correct, total) * 100.0

        train_losses.append(avg_train_loss)
        val_losses.append(avg_val_loss)
        val_accuracies.append(val_acc)

        print(
            f"Epoch {epoch + 1:02d}/{MAX_EPOCHS} | "
            f"Train Loss: {avg_train_loss:.4f} | "
            f"Val Loss: {avg_val_loss:.4f} | "
            f"Val Accuracy: {val_acc:.2f}%"
        )

        improved = False
        if val_acc > best_val_acc:
            improved = True
        elif val_acc == best_val_acc and avg_val_loss < best_val_loss:
            improved = True

        if improved:
            best_val_acc = val_acc
            best_val_loss = avg_val_loss
            epochs_without_improvement = 0
            best_y_true = y_true.copy()
            best_y_pred = y_pred.copy()

            torch.save(model.state_dict(), MODEL_PATH)
            print(f"  -> zapisano najlepszy model: {MODEL_PATH}")
        else:
            epochs_without_improvement += 1

        if epochs_without_improvement >= EARLY_STOPPING_PATIENCE:
            print("Early stopping triggered.")
            break

    print("\nTrening zakończony.")
    print(f"Najlepsza walidacyjna accuracy: {best_val_acc:.2f}%")
    print(f"Model zapisany jako: {MODEL_PATH}")

    plot_training(train_losses, val_losses, val_accuracies)
    print(f"Wykres treningu zapisany: {TRAINING_PLOT_PATH}")

    if len(best_y_true) > 0 and len(best_y_pred) > 0:
        save_metrics(best_y_true, best_y_pred)

    export_weights_to_txt(MODEL_PATH, WEIGHTS_TXT)

    print("\nZapisane pliki:")
    print(MODEL_PATH)
    print(TRAINING_PLOT_PATH)
    print(CONFUSION_MATRIX_PNG)
    print(CONFUSION_MATRIX_CSV)
    print(PER_CLASS_CSV)
    print(CLASSIFICATION_REPORT_TXT)
    print(WEIGHTS_TXT)


if __name__ == "__main__":
    train_model()