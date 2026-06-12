#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris High-Precision Training Script

楂樼簿搴﹁缁冭剼鏈紝鏀寔:
- 澶氶樁娈佃缁冪瓥鐣?
- 鐭ヨ瘑钂搁
- 娴嬭瘯鏃跺寮?(TTA)
- EMA 妯″瀷骞虫粦
- 娣峰悎绮惧害璁粌

浣跨敤鏂规硶:
    # 楂樼簿搴﹁缁?
    python training/scripts/train_highprec.py \\
        --data configs/dataset_custom.yaml \\
        --epochs 200 --batch 32

    # 鐭ヨ瘑钂搁璁粌
    python training/scripts/train_highprec.py \\
        --data configs/dataset_custom.yaml \\
        --teacher yolov11m.pt \\
        --epochs 200
"""

import argparse
import sys
from pathlib import Path
import yaml


def load_yaml(path: str) -> dict:
    """鍔犺浇 YAML 閰嶇疆"""
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def build_highprec_args(args, hyp: dict) -> dict:
    """鏋勫缓楂樼簿搴﹁缁冨弬鏁?""
    train_args = {
        "model": args.model or hyp.get("model", "yolov11n.pt"),
        "data": args.data,
        "epochs": args.epochs or hyp.get("epochs", 200),
        "batch": args.batch or hyp.get("batch", 32),
        "imgsz": args.imgsz or hyp.get("imgsz", 640),
        "device": args.device or hyp.get("device", 0),
        "workers": hyp.get("workers", 8),

        # 浼樺寲鍣?
        "optimizer": hyp.get("optimizer", "AdamW"),
        "lr0": hyp.get("lr0", 0.001),
        "lrf": hyp.get("lrf", 0.01),
        "momentum": hyp.get("momentum", 0.937),
        "weight_decay": hyp.get("weight_decay", 0.0005),

        # 瀛︿範鐜囪皟搴?
        "warmup_epochs": hyp.get("warmup_epochs", 5.0),
        "warmup_momentum": hyp.get("warmup_momentum", 0.8),
        "warmup_bias_lr": hyp.get("warmup_bias_lr", 0.1),

        # 鎹熷け鍑芥暟
        "cls": hyp.get("cls", 0.5),
        "box": hyp.get("box", 7.5),
        "dfl": hyp.get("dfl", 1.5),

        # 姝e垯鍖?
        "label_smoothing": hyp.get("label_smoothing", 0.02),
        "dropout": hyp.get("dropout", 0.0),

        # 璁粌鎺у埗
        "patience": hyp.get("patience", 80),
        "save": True,
        "save_period": hyp.get("save_period", 10),

        # 纭欢
        "amp": hyp.get("amp", True),

        # 杈撳嚭
        "project": hyp.get("project", "runs/train"),
        "name": args.name or hyp.get("name", "openiris_highprec"),
        "exist_ok": hyp.get("exist_ok", False),
        "pretrained": True,
    }

    # 鐭ヨ瘑钂搁
    if args.teacher or hyp.get("distill_teacher"):
        train_args["teacher"] = args.teacher or hyp.get("distill_teacher")
        train_args["distill_loss_weight"] = hyp.get("distill_loss_weight", 0.5)

    return train_args


def train_stage1(args: dict):
    """闃舵1: 鍩虹璁粌"""
    try:
        from ultralytics import YOLO
    except ImportError:
        print("閿欒: 鏈畨瑁?ultralytics")
        sys.exit(1)

    print("\n" + "=" * 60)
    print("闃舵1: 鍩虹璁粌")
    print("=" * 60)

    model = YOLO(args["model"])

    # 绗竴闃舵浣跨敤杈冨皬鐨勫涔犵巼棰勭儹
    stage1_args = args.copy()
    stage1_args["epochs"] = args["epochs"] // 3
    stage1_args["lr0"] = args["lr0"] * 0.1
    stage1_args["name"] = f"{args['name']}_stage1"

    results = model.train(**stage1_args)
    return results


def train_stage2(args: dict, stage1_weights: str):
    """闃舵2: 绮剧粏璁粌"""
    try:
        from ultralytics import YOLO
    except ImportError:
        print("閿欒: 鏈畨瑁?ultralytics")
        sys.exit(1)

    print("\n" + "=" * 60)
    print("闃舵2: 绮剧粏璁粌")
    print("=" * 60)

    model = YOLO(stage1_weights)

    stage2_args = args.copy()
    stage2_args["epochs"] = args["epochs"] // 3
    stage2_args["lr0"] = args["lr0"] * 0.5
    stage2_args["name"] = f"{args['name']}_stage2"

    results = model.train(**stage2_args)
    return results


def train_stage3(args: dict, stage2_weights: str):
    """闃舵3: 鏈€缁堣缁?""
    try:
        from ultralytics import YOLO
    except ImportError:
        print("閿欒: 鏈畨瑁?ultralytics")
        sys.exit(1)

    print("\n" + "=" * 60)
    print("闃舵3: 鏈€缁堣缁?)
    print("=" * 60)

    model = YOLO(stage2_weights)

    stage3_args = args.copy()
    stage3_args["epochs"] = args["epochs"] - (args["epochs"] // 3) * 2
    stage3_args["name"] = f"{args['name']}_final"

    results = model.train(**stage3_args)
    return results


def main():
    parser = argparse.ArgumentParser(
        description="OpenIris High-Precision Training",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )

    parser.add_argument("--data", type=str, required=True, help="鏁版嵁闆嗛厤缃?)
    parser.add_argument("--model", type=str, default=None, help="棰勮缁冩ā鍨?)
    parser.add_argument("--hyp", type=str, default=None, help="瓒呭弬鏁伴厤缃?)
    parser.add_argument("--epochs", type=int, default=None, help="璁粌杞")
    parser.add_argument("--batch", type=int, default=None, help="鎵规澶у皬")
    parser.add_argument("--imgsz", type=int, default=None, help="杈撳叆灏哄")
    parser.add_argument("--device", type=str, default=None, help="璁惧")
    parser.add_argument("--name", type=str, default=None, help="瀹為獙鍚嶇О")
    parser.add_argument("--teacher", type=str, default=None, help="鏁欏笀妯″瀷 (鐭ヨ瘑钂搁)")
    parser.add_argument("--stage", type=int, default=0, help="璁粌闃舵 (0=鍏ㄩ儴, 1/2/3)")

    args = parser.parse_args()

    # 鍔犺浇閰嶇疆
    hyp = load_yaml(args.hyp) if args.hyp else {}

    # 鏋勫缓璁粌鍙傛暟
    train_args = build_highprec_args(args, hyp)

    print("=" * 60)
    print("OpenIris 楂樼簿搴﹁缁?)
    print("=" * 60)
    print(f"鏁版嵁闆? {train_args['data']}")
    print(f"妯″瀷: {train_args['model']}")
    print(f"鎬昏疆娆? {train_args['epochs']}")
    print(f"鎵规澶у皬: {train_args['batch']}")
    print(f"璁惧: {train_args['device']}")
    print("=" * 60)

    if args.stage == 0 or args.stage == 1:
        # 闃舵1
        results1 = train_stage1(train_args)
        stage1_weights = f"{train_args['project']}/{train_args['name']}_stage1/weights/best.pt"

        if args.stage == 1:
            print(f"\n闃舵1瀹屾垚! 鏉冮噸: {stage1_weights}")
            return

    if args.stage == 0 or args.stage == 2:
        # 闃舵2
        weights = stage1_weights if args.stage == 0 else train_args["model"]
        results2 = train_stage2(train_args, weights)
        stage2_weights = f"{train_args['project']}/{train_args['name']}_stage2/weights/best.pt"

        if args.stage == 2:
            print(f"\n闃舵2瀹屾垚! 鏉冮噸: {stage2_weights}")
            return

    if args.stage == 0 or args.stage == 3:
        # 闃舵3
        weights = stage2_weights if args.stage == 0 else train_args["model"]
        results3 = train_stage3(train_args, weights)
        final_weights = f"{train_args['project']}/{train_args['name']}_final/weights/best.pt"

        print(f"\n楂樼簿搴﹁缁冨畬鎴?")
        print(f"鏈€缁堟潈閲? {final_weights}")


if __name__ == "__main__":
    main()
