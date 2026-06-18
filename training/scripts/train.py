#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris YOLOv11 Training Script

Mobile-deployment optimized training with auto image size adaptation.

Usage:
    python training/scripts/train.py --data configs/dataset_custom.yaml
    python training/scripts/train.py --data configs/dataset_custom.yaml --epochs 200 --rect
"""

import argparse
import sys
from pathlib import Path
import yaml

# Project root (training/scripts/train.py -> project root)
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent

# Mobile deployment constraints
MOBILE_CONSTRAINTS = {
    "model": "yolo11n.pt",
    "imgsz": 640,
    "min_epochs": 50,
    "recommended_epochs": 100,
}


def load_yaml(path):
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def validate_constraints(args):
    """Validate training params meet mobile deployment requirements."""
    warnings = []
    if "model" in args and args.model:
        model_name = Path(args.model).stem.lower()
        if "yolo" in model_name and not model_name.endswith("n"):
            warnings.append(f"[Warning] Model '{args.model}' is not nano. Use yolov11n.pt for mobile.")
    if args.imgsz != MOBILE_CONSTRAINTS["imgsz"]:
        warnings.append(f"[Warning] imgsz {args.imgsz} != {MOBILE_CONSTRAINTS['imgsz']}. NCNN uses {MOBILE_CONSTRAINTS['imgsz']}.")
    if args.epochs < MOBILE_CONSTRAINTS["min_epochs"]:
        warnings.append(f"[Warning] Epochs {args.epochs} < {MOBILE_CONSTRAINTS['min_epochs']}. Recommend 100+.")
    return warnings


def build_train_args(args, hyp):
    """Build ultralytics train() arguments."""
    train_args = {
        "model": args.model or hyp.get("model", "yolo11n.pt"),
        "data": args.data,
        "epochs": args.epochs or hyp.get("epochs", 200),
        "batch": args.batch or hyp.get("batch", 32),
        "imgsz": args.imgsz,
        "workers": hyp.get("workers", 4),
        "optimizer": hyp.get("optimizer", "AdamW"),
        "lr0": hyp.get("lr0", 0.001),
        "lrf": hyp.get("lrf", 0.01),
        "momentum": hyp.get("momentum", 0.937),
        "weight_decay": hyp.get("weight_decay", 0.0005),
        "warmup_epochs": hyp.get("warmup_epochs", 5.0),
        "warmup_momentum": hyp.get("warmup_momentum", 0.8),
        "warmup_bias_lr": hyp.get("warmup_bias_lr", 0.1),
        "cls": hyp.get("cls", 0.5),
        "box": hyp.get("box", 7.5),
        "dfl": hyp.get("dfl", 1.5),
        "label_smoothing": hyp.get("label_smoothing", 0.02),
        "dropout": hyp.get("dropout", 0.0),
        "patience": hyp.get("patience", 50),
        "save": True,
        "save_period": hyp.get("save_period", 10),
        "device": args.device or hyp.get("device", 0),
        "amp": hyp.get("amp", True),
        "project": hyp.get("project", str(PROJECT_ROOT / "training" / "run")),
        "name": args.name or hyp.get("name", "openiris"),
        "exist_ok": True,
        "pretrained": True,
        # Auto-adapt for non-standard image sizes
        "rect": args.rect,
        "cos_lr": True,
    }

    # Augmentation params
    aug_keys = ["mosaic", "mixup", "fliplr", "degrees", "scale", "hsv_h", "hsv_s", "hsv_v", "erasing"]
    for k in aug_keys:
        if k in hyp:
            train_args[k] = hyp[k]

    if args.resume:
        train_args["model"] = args.resume
        train_args["resume"] = True

    return train_args


def main():
    parser = argparse.ArgumentParser(description="OpenIris YOLOv11 Training")
    parser.add_argument("--data", type=str, required=True, help="Dataset config (YAML)")
    parser.add_argument("--model", type=str, default=None, help="Pretrained model")
    parser.add_argument("--hyp", type=str, default=None, help="Hyperparameters config")
    parser.add_argument("--epochs", type=int, default=None, help="Training epochs")
    parser.add_argument("--batch", type=int, default=None, help="Batch size")
    parser.add_argument("--imgsz", type=int, default=MOBILE_CONSTRAINTS["imgsz"], help="Image size")
    parser.add_argument("--device", type=str, default=None, help="Device (0, 0,1, cpu)")
    parser.add_argument("--name", type=str, default=None, help="Experiment name")
    parser.add_argument("--resume", type=str, default=None, help="Resume from weights")
    parser.add_argument("--rect", action="store_true", default=True,
                        help="Rectangular training (auto-adapt image aspect ratios)")
    parser.add_argument("--no-rect", action="store_false", dest="rect",
                        help="Disable rectangular training")
    parser.add_argument("--dry-run", action="store_true", help="Show config only")

    args = parser.parse_args()
    hyp = load_yaml(args.hyp) if args.hyp else {}

    # Validate
    warnings = validate_constraints(args)
    if warnings:
        print("=" * 60)
        for w in warnings:
            print(f"  {w}")
        print("=" * 60)

    train_args = build_train_args(args, hyp)

    if args.dry_run:
        print("\nTraining config:")
        print("-" * 40)
        for k, v in sorted(train_args.items()):
            print(f"  {k}: {v}")
        return

    # Train
    try:
        from ultralytics import YOLO

        print(f"\nLoading model: {train_args['model']}")
        model = YOLO(train_args["model"])

        print(f"Dataset: {train_args['data']}")
        print(f"Epochs: {train_args['epochs']}")
        print(f"Batch: {train_args['batch']}")
        print(f"Image size: {train_args['imgsz']}")
        print(f"Rect (auto-adapt): {train_args['rect']}")
        print(f"Device: {train_args['device']}")
        print("\nStarting training...\n")

        train_args.pop("model")
        results = model.train(**train_args)

        print("\nTraining complete!")
        best = f"{train_args['project']}/{train_args['name']}/weights/best.pt"
        print(f"Best weights: {best}")

    except ImportError:
        print("Error: ultralytics not installed. pip install ultralytics")
        sys.exit(1)
    except Exception as e:
        print(f"Training failed: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
