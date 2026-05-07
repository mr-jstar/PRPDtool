import sys

from pathlib import Path

import cv2
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

CONF_OBJ_TH = 0.30
CONF_CLS_TH = 0.55

LABELS = ["DEF1", "DEF2", "DEF3", "DEF4"]
COLORS = [
    (0, 0, 255),      # DEF1
    (0, 160, 0),      # DEF2
    (255, 0, 0),      # DEF3
    (128, 0, 128),    # DEF4
]


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

def predict_defect_from_file(
        filename,
        model,
        device,
        conf_obj_th,
        conf_cls_th
):
    """
    Wczytuje obraz PNG/JPG, przygotowuje tensor i zwraca:
        (cls_id, obj_score, cls_scores)

    cls_id może być None, jeśli klasyfikator nie zaakceptował wyniku.
    """

    frame = cv2.imread(filename, cv2.IMREAD_COLOR)

    if frame is None:
        raise ValueError(f"Cannot read image file: {filename}")

    tensor = crop_to_model_tensor(frame)

    cls_id, obj_score, cls_scores = predict_defect(
        model,
        tensor,
        device,
        conf_obj_th,
        conf_cls_th
    )

    return cls_id, obj_score, cls_scores, tensor

def prepare_model(model_path):
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    #print("[INFO] Device:", device)

    model = MobileNetYOLO().to(device)
    model.load_state_dict(torch.load(model_path, map_location=device))
    model.eval()
    #print(f"[INFO] Model load: {MODEL_PATH}")
    return (device,model)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage:", sys.argv[0], " <file>")
        sys.exit(1)

    (device,model) = prepare_model(MODEL_PATH)
    if model is not None:
        (cls,score,cls_scores,tensor) = predict_defect_from_file( sys.argv[1], model, device, CONF_OBJ_TH, CONF_CLS_TH )
        if cls is None:
            print( "-1 0 0 0 0 0" )
        else:
            print( cls, score,  cls_scores )

        if len(sys.argv) > 2 :
            torch.onnx.export(model, tensor, sys.argv[2] + ".onnx")
