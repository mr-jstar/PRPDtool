# MobileNetV2 + YOLO-like head for 6 PRPD defect classes
# Automatic ZIP extraction + train/val split + YOLO label generation

import os
import zipfile
import random
import shutil
from pathlib import Path

import cv2
import numpy as np
import matplotlib.pyplot as plt

import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader
from torchvision import transforms, models

from sklearn.metrics import confusion_matrix, ConfusionMatrixDisplay


# ============================================================
# SETTINGS
# ============================================================

NUM_CLASSES = 6
NUM_ANCHORS = 9
GRID_SIZE = 7
OUTPUT_SIZE = NUM_ANCHORS * (5 + NUM_CLASSES)

VAL_RATIO = 0.2
IMAGE_SIZE = 224
BATCH_SIZE = 4
MAX_EPOCHS = 30
LR = 1e-3

BASE_DIR = Path(__file__).resolve().parent

RAW_DIR = BASE_DIR / "unzipped_data"
DATASET_DIR = BASE_DIR / "dataset_yolo_6defects"

MODEL_NAME = "mobilenet_yolo_prpd_6defects_2026.pt"


# ============================================================
# ZIP EXTRACTION + DATASET PREPARATION
# ============================================================

def prepare_dataset():
    print("Przygotowanie datasetu...")

    RAW_DIR.mkdir(exist_ok=True)

    zip_files = sorted(BASE_DIR.glob("*.zip"))

    if len(zip_files) == 0:
        raise FileNotFoundError("Nie znaleziono plików .zip w folderze ze skryptem.")

    class_names = [z.stem for z in zip_files]
    class_to_id = {name: i for i, name in enumerate(class_names)}

    print("Wykryte klasy:")
    for name, idx in class_to_id.items():
        print(f"{idx}: {name}")

    # Rozpakowanie ZIP-ów
    for zip_path in zip_files:
        class_name = zip_path.stem
        target_dir = RAW_DIR / class_name
        target_dir.mkdir(parents=True, exist_ok=True)

        print(f"Rozpakowuję: {zip_path.name}")

        with zipfile.ZipFile(zip_path, "r") as zip_ref:
            zip_ref.extractall(target_dir)

    # Czyszczenie starego datasetu
    if DATASET_DIR.exists():
        shutil.rmtree(DATASET_DIR)

    for split in ["train", "val"]:
        (DATASET_DIR / "images" / split).mkdir(parents=True, exist_ok=True)
        (DATASET_DIR / "labels" / split).mkdir(parents=True, exist_ok=True)

    # Zebranie obrazów
    samples = []

    for class_name, class_id in class_to_id.items():
        class_dir = RAW_DIR / class_name
        image_paths = list(class_dir.rglob("*.png")) + list(class_dir.rglob("*.jpg")) + list(class_dir.rglob("*.jpeg"))

        if len(image_paths) == 0:
            print(f"UWAGA: brak obrazów dla klasy {class_name}")
            continue

        for img_path in image_paths:
            samples.append((img_path, class_id, class_name))

    if len(samples) == 0:
        raise RuntimeError("Nie znaleziono żadnych obrazów .png/.jpg/.jpeg.")

    random.shuffle(samples)

    split_idx = int(len(samples) * (1 - VAL_RATIO))
    train_samples = samples[:split_idx]
    val_samples = samples[split_idx:]

    print(f"Liczba próbek: {len(samples)}")
    print(f"Train: {len(train_samples)}")
    print(f"Val:   {len(val_samples)}")

    def copy_samples(samples_list, split):
        for i, (img_path, class_id, class_name) in enumerate(samples_list):
            new_stem = f"{class_name}_{i:05d}"
            dst_img = DATASET_DIR / "images" / split / f"{new_stem}.png"
            dst_lbl = DATASET_DIR / "labels" / split / f"{new_stem}.txt"

            img = cv2.imread(str(img_path), cv2.IMREAD_GRAYSCALE)

            if img is None:
                print(f"Pominięto uszkodzony plik: {img_path}")
                continue

            cv2.imwrite(str(dst_img), img)

            with open(dst_lbl, "w") as f:
                f.write(f"{class_id} 0.5 0.5 1.0 1.0\n")

    copy_samples(train_samples, "train")
    copy_samples(val_samples, "val")

    # Zapis mapowania klas
    with open(DATASET_DIR / "classes.txt", "w", encoding="utf-8") as f:
        for name, idx in class_to_id.items():
            f.write(f"{idx}: {name}\n")

    print("Dataset przygotowany.")
    print(f"Zapisano w: {DATASET_DIR}")

    return class_names


# ============================================================
# MODEL
# ============================================================

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


# ============================================================
# DATASET
# ============================================================

class PRPDImageDataset(torch.utils.data.Dataset):
    def __init__(self, image_dir, label_dir, transform=None):
        self.image_paths = []
        self.label_paths = []
        self.transform = transform

        for ext in ("*.png", "*.jpg", "*.jpeg"):
            self.image_paths += list(Path(image_dir).glob(ext))

        self.image_paths = sorted(self.image_paths)

        for img_path in self.image_paths:
            lbl_path = Path(label_dir) / (img_path.stem + ".txt")
            self.label_paths.append(lbl_path)

        valid_images = []
        valid_labels = []

        for img, lbl in zip(self.image_paths, self.label_paths):
            if lbl.exists():
                valid_images.append(img)
                valid_labels.append(lbl)

        self.image_paths = valid_images
        self.label_paths = valid_labels

    def __len__(self):
        return len(self.image_paths)

    def __getitem__(self, idx):
        image = cv2.imread(str(self.image_paths[idx]), cv2.IMREAD_GRAYSCALE)

        if image is None:
            raise RuntimeError(f"Nie można wczytać obrazu: {self.image_paths[idx]}")

        image = cv2.resize(image, (IMAGE_SIZE, IMAGE_SIZE))
        image = np.stack([image] * 3, axis=2)

        if self.transform:
            image = self.transform(image)

        targets = torch.zeros((GRID_SIZE, GRID_SIZE, NUM_ANCHORS, 5 + NUM_CLASSES))

        with open(self.label_paths[idx]) as f:
            for line in f:
                parts = list(map(float, line.strip().split()))

                if len(parts) != 5:
                    continue

                cls = int(parts[0])
                x, y, w, h = parts[1:5]

                cx = min(int(x * GRID_SIZE), GRID_SIZE - 1)
                cy = min(int(y * GRID_SIZE), GRID_SIZE - 1)

                for a in range(NUM_ANCHORS):
                    targets[cy, cx, a, :4] = torch.tensor([x, y, w, h])
                    targets[cy, cx, a, 4] = 1.0
                    targets[cy, cx, a, 5 + cls] = 1.0

        return image, targets


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
# TRAINING
# ============================================================

def train_model(class_names):
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Urządzenie: {device}")

    model = MobileNetYOLO().to(device)
    optimizer = optim.Adam(model.parameters(), lr=LR)
    criterion = YOLOLikeLoss()

    transform = transforms.Compose([
        transforms.ToTensor(),
        transforms.Normalize([0.5] * 3, [0.5] * 3)
    ])

    train_ds = PRPDImageDataset(
        DATASET_DIR / "images" / "train",
        DATASET_DIR / "labels" / "train",
        transform=transform
    )

    val_ds = PRPDImageDataset(
        DATASET_DIR / "images" / "val",
        DATASET_DIR / "labels" / "val",
        transform=transform
    )

    train_loader = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True)
    val_loader = DataLoader(val_ds, batch_size=BATCH_SIZE, shuffle=False)

    print(f"Train dataset: {len(train_ds)}")
    print(f"Val dataset:   {len(val_ds)}")

    train_losses = []
    val_losses = []
    val_accuracies = []

    best_acc = 0.0
    y_true_best = []
    y_pred_best = []

    for epoch in range(MAX_EPOCHS):
        model.train()
        total_train_loss = 0.0

        for images, targets in train_loader:
            images = images.to(device)
            targets = targets.to(device)

            preds = model(images)
            preds = preds.reshape(-1, GRID_SIZE, GRID_SIZE, NUM_ANCHORS, 5 + NUM_CLASSES)

            loss = criterion(preds, targets)

            optimizer.zero_grad()
            loss.backward()
            optimizer.step()

            total_train_loss += loss.item()

        model.eval()
        total_val_loss = 0.0
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
                total_val_loss += loss.item()

                mask = targets[..., 4] == 1.0

                if mask.sum() > 0:
                    pred_classes = torch.argmax(preds[mask][..., 5:], dim=-1)
                    true_classes = torch.argmax(targets[mask][..., 5:], dim=-1)

                    correct += (pred_classes == true_classes).sum().item()
                    total += true_classes.numel()

                    y_true.extend(true_classes.cpu().numpy())
                    y_pred.extend(pred_classes.cpu().numpy())

        acc = correct / total * 100 if total > 0 else 0.0

        train_losses.append(total_train_loss)
        val_losses.append(total_val_loss)
        val_accuracies.append(acc)

        print(
            f"Epoch {epoch + 1}/{MAX_EPOCHS} | "
            f"Train Loss: {total_train_loss:.4f} | "
            f"Val Loss: {total_val_loss:.4f} | "
            f"Val Accuracy: {acc:.2f}%"
        )

        if acc > best_acc:
            best_acc = acc
            y_true_best = y_true.copy()
            y_pred_best = y_pred.copy()
            torch.save(model.state_dict(), MODEL_NAME)
            print(f"Zapisano najlepszy model: {MODEL_NAME}")

    # Wykres uczenia
    plt.figure()
    plt.plot(train_losses, label="Train Loss")
    plt.plot(val_losses, label="Val Loss")
    plt.plot(val_accuracies, label="Val Accuracy [%]")
    plt.title("Training Progress - MobileNetV2 YOLO Head")
    plt.xlabel("Epoch")
    plt.ylabel("Loss / Accuracy")
    plt.legend()
    plt.grid(True)
    plt.savefig("training_plot_6defects.png", dpi=300)

    # Macierz pomyłek
    if len(y_true_best) > 0 and len(y_pred_best) > 0:
        cm = confusion_matrix(
            y_true_best,
            y_pred_best,
            labels=list(range(NUM_CLASSES))
        )

        disp = ConfusionMatrixDisplay(
            confusion_matrix=cm,
            display_labels=class_names
        )

        disp.plot(cmap="Blues", xticks_rotation=45)
        plt.tight_layout()
        plt.savefig("confusion_matrix_6defects.png", dpi=300)

    print("Trening zakończony.")
    print(f"Najlepsza dokładność walidacyjna: {best_acc:.2f}%")
    print(f"Model: {MODEL_NAME}")
    print("Wykres: training_plot_6defects.png")
    print("Macierz pomyłek: confusion_matrix_6defects.png")


# ============================================================
# MAIN
# ============================================================

if __name__ == "__main__":
    class_names = prepare_dataset()
    train_model(class_names)