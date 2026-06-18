#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris High-Precision Training Script

支持多阶段训练策略:
- 基础预训练
- 知识蒸馏
- 测试时增强 (TTA)
- EMA 模型平滑
- 混合精度训练

使用方法:
    # 高精度训练
    python training/scripts/train_highprec.py \\
        --data configs/dataset_custom.yaml \\
        --epochs 200 --batch 32

    # 知识蒸馏训练
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
    """加载 YAML 配置"""
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def build_highprec_args(args, hyp: dict) -> dict:
    """构建高精度训练参数"""
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

        # 瀛[?]範鐜囪皟搴?
        "warmup_epochs": hyp.get("warmup_epochs", 5.0),
        "warmup_momentum": hyp.get("warmup_momentum", 0.8),
        "warmup_bias_lr": hyp.get("warmup_bias_lr", 0.1),

        # 鎹熷[?]鍑芥暟
        "cls": hyp.get("cls", 0.5),
        "box": hyp.get("box", 7.5),
        "dfl": hyp.get("dfl", 1.5),

        # 姝e垯鍖?
        "label_smoothing": hyp.get("label_smoothing", 0.02),
        "dropout": hyp.get("dropout", 0.0),

        # 璁[?]粌鎺[?]埗
        "patience": hyp.get("patience", 80),
        "save": True,
        "save_period": hyp.get("save_period", 10),

        # 纭[?]欢
        "amp": hyp.get("amp", True),

        # 杈撳嚭
        "project": hyp.get("project", "runs/train"),
        "name": args.name or hyp.get("name", "openiris_highprec"),
        "exist_ok": hyp.get("exist_ok", False),
        "pretrained": True,
    }

    # 知识蒸馏
    if args.teacher or hyp.get("distill_teacher"):
        train_args["teacher"] = args.teacher or hyp.get("distill_teacher")
        train_args["distill_loss_weight"] = hyp.get("distill_loss_weight", 0.5)

    return train_args


def train_stage1(args: dict):
    """阶段1: 基础训练"""
    try:
        from ultralytics import YOLO
    except ImportError:
        print("错误: 未安装 ultralytics")
        sys.exit(1)

    print("\n" + "=" * 60)
    print("闃舵[?]1: 鍩虹[?]璁[?]粌")
    print("=" * 60)

    model = YOLO(args["model"])

    # 绗[?]竴闃舵[?]浣跨敤杈冨皬鐨勫[?]涔犵巼棰勭儹
    stage1_args = args.copy()
    stage1_args["epochs"] = args["epochs"] // 3
    stage1_args["lr0"] = args["lr0"] * 0.1
    stage1_args["name"] = f"{args['name']}_stage1"

    # 如果指定了教师模型，启用知识蒸馏
    if "teacher" in args:
        print(f"启用知识蒸馏: 教师模型 = {args['teacher']}")
        stage1_args["teacher"] = args["teacher"]
        stage1_args["distill_loss_weight"] = args.get("distill_loss_weight", 0.5)

    results = model.train(**stage1_args)
    return results


def train_stage2(args: dict, stage1_weights: str):
    """阶段2: 精细训练"""
    try:
        from ultralytics import YOLO
    except ImportError:
        print("错误: 未安装 ultralytics")
        sys.exit(1)

    print("\n" + "=" * 60)
    print("闃舵[?]2: 绮剧粏璁[?]粌")
    print("=" * 60)

    model = YOLO(stage1_weights)

    stage2_args = args.copy()
    stage2_args["epochs"] = args["epochs"] // 3
    stage2_args["lr0"] = args["lr0"] * 0.5
    stage2_args["name"] = f"{args['name']}_stage2"

    # 如果指定了教师模型，继续使用知识蒸馏
    if "teacher" in args:
        print(f"继续知识蒸馏: 教师模型 = {args['teacher']}")
        stage2_args["teacher"] = args["teacher"]
        stage2_args["distill_loss_weight"] = args.get("distill_loss_weight", 0.5)

    results = model.train(**stage2_args)
    return results


def train_stage3(args: dict, stage2_weights: str):
    """阶段3: 最终训练"""
    try:
        from ultralytics import YOLO
    except ImportError:
        print("错误: 未安装 ultralytics")
        sys.exit(1)

    print("\n" + "=" * 60)
    print("阶段3: 最终训练")
    print("=" * 60)

    model = YOLO(stage2_weights)

    stage3_args = args.copy()
    stage3_args["epochs"] = args["epochs"] - (args["epochs"] // 3) * 2
    stage3_args["name"] = f"{args['name']}_final"

    # 如果指定了教师模型，继续使用知识蒸馏
    if "teacher" in args:
        print(f"最终阶段知识蒸馏: 教师模型 = {args['teacher']}")
        stage3_args["teacher"] = args["teacher"]
        stage3_args["distill_loss_weight"] = args.get("distill_loss_weight", 0.5)

    results = model.train(**stage3_args)
    return results


def main():
    parser = argparse.ArgumentParser(
        description="OpenIris High-Precision Training",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )

    print("阶段1: 基础训练")
    parser.add_argument("--model", type=str, default=None, help="预训练模型")
    parser.add_argument("--hyp", type=str, default=None, help="超参配置")
    parser.add_argument("--epochs", type=int, default=None, help="训练轮次")
    parser.add_argument("--batch", type=int, default=None, help="鎵规[?]澶[?]皬")
    parser.add_argument("--imgsz", type=int, default=None, help="杈撳叆灏哄[?]")
    parser.add_argument("--device", type=str, default=None, help="璁惧[?]")
    parser.add_argument("--name", type=str, default=None, help="瀹為獙鍚嶇[?]")
    parser.add_argument("--teacher", type=str, default=None, help="鏁欏笀妯[?]瀷 (鐭[?]瘑钂搁[?])")
    parser.add_argument("--stage", type=int, default=0, help="璁[?]粌闃舵[?] (0=鍏[?]儴, 1/2/3)")

    args = parser.parse_args()

    # 鍔犺浇閰嶇疆
    hyp = load_yaml(args.hyp) if args.hyp else {}

    # 鏋勫缓璁[?]粌鍙傛暟
    train_args = build_highprec_args(args, hyp)

    print("=" * 60)
    print("OpenIris 高精度训练")
    print("=" * 60)
    print("\n模型配置:")
    print(f"妯[?]瀷: {train_args['model']}")
    print(f"鎬昏疆娆? {train_args['epochs']}")
    print(f"鎵规[?]澶[?]皬: {train_args['batch']}")
    print(f"璁惧[?]: {train_args['device']}")
    print("=" * 60)

    if args.stage == 0 or args.stage == 1:
        # 闃舵[?]1
        # 阶段1
        results1 = train_stage1(train_args)
        stage1_weights = f"{train_args['project']}/{train_args['name']}_stage1/weights/best.pt"

        if args.stage == 1:
            print(f"\n阶段1完成! 权重: {stage1_weights}")

    if args.stage == 0 or args.stage == 2:
        # 闃舵[?]2
        weights = stage1_weights if args.stage == 0 else train_args["model"]
        results2 = train_stage2(train_args, weights)
        stage2_weights = f"{train_args['project']}/{train_args['name']}_stage2/weights/best.pt"

        if args.stage == 2:
    # 加载模型
            return

    if args.stage == 0 or args.stage == 3:
        # 闃舵[?]3
        weights = stage2_weights if args.stage == 0 else train_args["model"]
        results3 = train_stage3(train_args, weights)
        final_weights = f"{train_args['project']}/{train_args['name']}_final/weights/best.pt"

        print(f"\n楂樼簿搴[?][?]缁冨畬鎴?")
        print(f"鏈[?]缁堟潈閲? {final_weights}")
        print("加载模型中...\n")

if __name__ == "__main__":
    main()
