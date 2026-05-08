# cam_2026_ui.py
# Rozszerzona wersja cam_2026.py:
# - ten sam model MobileNetYOLO
# - dowolna liczba ROI
# - ładniejszy interfejs
# - panel pomocy / nawigacji
# - zapis / odczyt layoutu
# - dynamiczne progi

import json
from pathlib import Path
from typing import List, Dict, Optional

import cv2
import mss
import numpy as np
import torch
import torch.nn as nn
from torchvision import transforms, models

# =========================
# USTAWIENIA
# =========================
NUM_CLASSES = 4
NUM_ANCHORS = 9
GRID_SIZE = 7
OUTPUT_SIZE = NUM_ANCHORS * (5 + NUM_CLASSES)

BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = BASE_DIR / "mobilenet_yolo_prpd_2026.pt"
LAYOUT_PATH = BASE_DIR / "prpd_layout.json"

CONF_OBJ_TH = 0.30
CONF_CLS_TH = 0.55

LABELS = ["DEF1", "DEF2", "DEF3", "DEF4"]
COLORS = [
    (0, 0, 255),      # DEF1
    (0, 160, 0),      # DEF2
    (255, 0, 0),      # DEF3
    (128, 0, 128),    # DEF4
]

PANEL_BG = (32, 36, 42)
PANEL_TEXT = (235, 235, 235)
PANEL_ACCENT = (0, 215, 255)
UNKNOWN_COLOR = (90, 90, 90)
ROI_COLOR = (0, 215, 255)

WINDOW_NAME = "PRPD Monitoring - MobileNetYOLO UI"


# =========================
# MODEL
# =========================
class MobileNetYOLO(nn.Module):
    def __init__(self):
        super().__init__()
        self.backbone = models.mobilenet_v2(weights=None).features
        self.conv_head = nn.Sequential(
            nn.Conv2d(1280, 256, kernel_size=1),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Conv2d(256, OUTPUT_SIZE, kernel_size=1),
        )

    def forward(self, x):
        x = self.backbone(x)
        x = self.conv_head(x)
        x = torch.sigmoid(x)
        x = x.permute(0, 2, 3, 1)  # (B, 7, 7, OUTPUT_SIZE) dla 224x224
        return x


# =========================
# PREPROCESS
# =========================
transform = transforms.Compose([
    transforms.ToTensor(),
    transforms.Normalize([0.5] * 3, [0.5] * 3)
])


def crop_to_model_tensor(bgr_crop: np.ndarray) -> torch.Tensor:
    gray = cv2.cvtColor(bgr_crop, cv2.COLOR_BGR2GRAY)
    gray = cv2.resize(gray, (224, 224), interpolation=cv2.INTER_AREA)
    img3 = np.stack([gray, gray, gray], axis=2)
    ten = transform(img3).unsqueeze(0)
    return ten


def predict_defect(model: nn.Module, tensor: torch.Tensor, device: torch.device,
                   conf_obj_th: float, conf_cls_th: float):
    with torch.no_grad():
        out = model(tensor.to(device))
        out = out.reshape(1, GRID_SIZE, GRID_SIZE, NUM_ANCHORS, 5 + NUM_CLASSES)[0]

        obj = out[..., 4]
        best_idx = torch.argmax(obj)
        gy, gx, a = np.unravel_index(best_idx.cpu().item(), obj.shape)

        best_obj = obj[gy, gx, a].item()
        cls_scores = out[gy, gx, a, 5:].detach().cpu()
        cls_prob, cls_id = torch.max(cls_scores, dim=0)

        cls_prob = float(cls_prob.item())
        cls_id = int(cls_id.item())

        if best_obj < conf_obj_th or cls_prob < conf_cls_th:
            return None, best_obj, cls_scores.numpy()

        return cls_id, best_obj, cls_scores.numpy()


# =========================
# ROI / LAYOUT
# =========================
def save_layout(layout_boxes: List[Dict], path: str = LAYOUT_PATH):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(layout_boxes, f, indent=2, ensure_ascii=False)
    print(f"[INFO] Layout saved to: {path}")


def load_layout(path: str = LAYOUT_PATH) -> List[Dict]:
    p = Path(path)
    if not p.exists():
        return []
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    print(f"[INFO] Layout loaded from: {path}")
    return data


def select_rois_interactively(screen_bgr: np.ndarray, window_title: str = "Selectz PRPD") -> List[Dict]:
    print("\n[INFO] Manually select ROI.")
    print("[INFO] Select next area PRPD.")
    print("[INFO] ENTER confirm ROI.")
    print("[INFO] Empty ROI will finish.\n")

    rois: List[Dict] = []
    idx = 1

    while True:
        clone = screen_bgr.copy()
        msg1 = f"ROI {idx}: select PRPD area"
        msg2 = "ENTER = confirm  |  empty ROI = finish"
        cv2.putText(clone, msg1, (20, 35), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 255, 255), 2, cv2.LINE_AA)
        cv2.putText(clone, msg2, (20, 70), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2, cv2.LINE_AA)

        r = cv2.selectROI(window_title, clone, fromCenter=False, showCrosshair=True)
        x, y, w, h = map(int, r)

        if w == 0 or h == 0:
            break

        rois.append({"left": x, "top": y, "width": w, "height": h})
        idx += 1

    try:
        cv2.destroyWindow(window_title)
    except Exception:
        pass

    print(f"[INFO] Selected ROI: {len(rois)}")
    return rois


# =========================
# UI
# =========================
def draw_label_box(img, text: str, x: int, y: int, color, scale: float = 0.70):
    font = cv2.FONT_HERSHEY_SIMPLEX
    thickness = 2
    (tw, th), _ = cv2.getTextSize(text, font, scale, thickness)
    y1 = max(0, y - th - 10)
    cv2.rectangle(img, (x, y1), (x + tw + 10, y), color, -1)
    cv2.putText(img, text, (x + 5, y - 5), font, scale, (255, 255, 255), thickness, cv2.LINE_AA)


def draw_sidebar(base_frame: np.ndarray, show_help: bool,
                 roi_count: int, conf_obj_th: float, conf_cls_th: float) -> np.ndarray:
    frame = base_frame.copy()
    h, w = frame.shape[:2]
    panel_w = 360

    canvas = np.zeros((h, w + panel_w, 3), dtype=np.uint8)
    canvas[:, :w] = frame
    canvas[:, w:] = PANEL_BG

    x0 = w + 18
    y = 32

    def put(text, color=PANEL_TEXT, scale=0.65, thick=2, gap=30):
        nonlocal y
        cv2.putText(canvas, text, (x0, y), cv2.FONT_HERSHEY_SIMPLEX, scale, color, thick, cv2.LINE_AA)
        y += gap

    put("PRPD Monitor", PANEL_ACCENT, 0.95, 2, 38)
    put(f"ROI active: {roi_count}", PANEL_TEXT, 0.68, 2, 28)
    put(f"CONF_OBJ_TH: {conf_obj_th:.2f}", PANEL_TEXT, 0.68, 2, 28)
    put(f"CONF_CLS_TH: {conf_cls_th:.2f}", PANEL_TEXT, 0.68, 2, 36)

    if show_help:
        put("Control:", PANEL_ACCENT, 0.78, 2, 34)
        put("ESC  - exit", gap=26)
        put("r    - select ROI", gap=26)
        put("s    - save layout", gap=26)
        put("l    - load layout", gap=26)
        put("c    - load ROI", gap=26)
        put("h    - show/hide help", gap=26)
        put("+/-  - change threshold class", gap=26)
        put("[/]  - change threshold objectness", gap=34)

        put("Class legend:", PANEL_ACCENT, 0.78, 2, 34)
        for name, color in zip(LABELS, COLORS):
            cv2.rectangle(canvas, (x0, y - 18), (x0 + 24, y + 2), color, -1)
            cv2.putText(canvas, name, (x0 + 36, y), cv2.FONT_HERSHEY_SIMPLEX, 0.65, PANEL_TEXT, 2, cv2.LINE_AA)
            y += 30
    else:
        put("Select 'h', to show help", PANEL_ACCENT, 0.66, 2, 30)

    return canvas


# =========================
# MAIN
# =========================
def screen_monitor():
    global CONF_OBJ_TH, CONF_CLS_TH

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print("[INFO] Device:", device)

    model = MobileNetYOLO().to(device)
    model.load_state_dict(torch.load(MODEL_PATH, map_location=device))
    model.eval()
    print(f"[INFO] Model load: {MODEL_PATH}")

    show_help = True
    layout_boxes = load_layout()

    with mss.mss() as sct:
        while True:
            screen = np.array(sct.grab(sct.monitors[1]))
            frame = cv2.cvtColor(screen, cv2.COLOR_BGRA2BGR)

            # jeśli nie ma ROI, poproś użytkownika
            if len(layout_boxes) == 0:
                preview = frame.copy()
                cv2.putText(preview, "No ROI - select 'r', to select PRPD area",
                            (20, 40), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 255, 255), 2, cv2.LINE_AA)
                output = draw_sidebar(preview, show_help, 0, CONF_OBJ_TH, CONF_CLS_TH)
                cv2.imshow(WINDOW_NAME, output)
            else:
                display = frame.copy()

                for i, box in enumerate(layout_boxes, start=1):
                    x, y, w, h = box["left"], box["top"], box["width"], box["height"]

                    # zabezpieczenie granic
                    x = max(0, min(x, frame.shape[1] - 1))
                    y = max(0, min(y, frame.shape[0] - 1))
                    w = max(1, min(w, frame.shape[1] - x))
                    h = max(1, min(h, frame.shape[0] - y))

                    prpd_crop = frame[y:y + h, x:x + w]
                    if prpd_crop.size == 0:
                        continue

                    tensor = crop_to_model_tensor(prpd_crop)
                    cls_id, obj_score, cls_scores = predict_defect(
                        model, tensor, device, CONF_OBJ_TH, CONF_CLS_TH
                    )

                    if cls_id is None:
                        label = f"ROI {i} | UNKNOWN | obj={obj_score:.2f}"
                        color = UNKNOWN_COLOR
                    else:
                        cls_p = float(np.max(cls_scores))
                        label = f"ROI {i} | {LABELS[cls_id]} | obj={obj_score:.2f} | p={cls_p:.2f}"
                        color = COLORS[cls_id]

                    cv2.rectangle(display, (x, y), (x + w, y + h), color, 3)
                    draw_label_box(display, label, x, max(26, y), color, scale=0.68)

                output = draw_sidebar(display, show_help, len(layout_boxes), CONF_OBJ_TH, CONF_CLS_TH)
                cv2.imshow(WINDOW_NAME, output)

            key = cv2.waitKey(1) & 0xFF

            if key == 27:  # ESC
                break
            elif key == ord("r"):
                layout_boxes = select_rois_interactively(frame, "Select PRPD")
            elif key == ord("s"):
                save_layout(layout_boxes)
            elif key == ord("l"):
                layout_boxes = load_layout()
            elif key == ord("c"):
                layout_boxes = []
                print("[INFO] Deleted ROI from memory.")
            elif key == ord("h"):
                show_help = not show_help
            elif key in (ord("+"), ord("=")):
                CONF_CLS_TH = min(0.99, CONF_CLS_TH + 0.05)
                print(f"[INFO] CONF_CLS_TH = {CONF_CLS_TH:.2f}")
            elif key == ord("-"):
                CONF_CLS_TH = max(0.01, CONF_CLS_TH - 0.05)
                print(f"[INFO] CONF_CLS_TH = {CONF_CLS_TH:.2f}")
            elif key == ord("]"):
                CONF_OBJ_TH = min(0.99, CONF_OBJ_TH + 0.05)
                print(f"[INFO] CONF_OBJ_TH = {CONF_OBJ_TH:.2f}")
            elif key == ord("["):
                CONF_OBJ_TH = max(0.01, CONF_OBJ_TH - 0.05)
                print(f"[INFO] CONF_OBJ_TH = {CONF_OBJ_TH:.2f}")

    cv2.destroyAllWindows()


if __name__ == "__main__":
    screen_monitor()