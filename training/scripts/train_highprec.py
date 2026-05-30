#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris High-Precision Training Script

高精度训练脚本，支持:
- 多阶段训练策略
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

        # 优化器
        "optimizer": hyp.get("optimizer", "AdamW"),
        "lr0": hyp.get("lr0", 0.001),
        "lrf": hyp.get("lrf", 0.01),
        "momentum": hyp.get("momentum", 0.937),
        "weight_decay": hyp.get("weight_decay", 0.0005),

        # 学习率调度
        "warmup_epochs": hyp.get("warmup_epochs", 5.0),
        "warmup_momentum": hyp.get("warmup_momentum", 0.8),
        "warmup_bias_lr": hyp.get("warmup_bias_lr", 0.1),

        # 损失函数
        "cls": hyp.get("cls", 0.5),
        "box": hyp.get("box", 7.5),
        "dfl": hyp.get("dfl", 1.5),

        # 正则化
        "label_smoothing": hyp.get("label_smoothing", 0.02),
        "dropout": hyp.get("dropout", 0.0),

        # 训练控制
        "patience": hyp.get("patience", 80),
        "save": True,
        "save_period": hyp.get("save_period", 10),

        # 硬件
        "amp": hyp.get("amp", True),

        # 输出
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
    print("阶段1: 基础训练")
    print("=" * 60)

    model = YOLO(args["model"])

    # 第一阶段使用较小的学习率预热
    stage1_args = args.copy()
    stage1_args["epochs"] = args["epochs"] // 3
    stage1_args["lr0"] = args["lr0"] * 0.1
    stage1_args["name"] = f"{args['name']}_stage1"

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
    print("阶段2: 精细训练")
    print("=" * 60)

    model = YOLO(stage1_weights)

    stage2_args = args.copy()
    stage2_args["epochs"] = args["epochs"] // 3
    stage2_args["lr0"] = args["lr0"] * 0.5
    stage2_args["name"] = f"{args['name']}_stage2"

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
    stage3_args["epochs"] = args["epochs"] // 3
    stage3_args["name"] = f"{args['name']}_final"

    results = model.train(**stage3_args)
    return results


def main():
    parser = argparse.ArgumentParser(
        description="OpenIris High-Precision Training",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )

    parser.add_argument("--data", type=str, required=True, help="数据集配置")
    parser.add_argument("--model", type=str, default=None, help="预训练模型")
    parser.add_argument("--hyp", type=str, default=None, help="超参数配置")
    parser.add_argument("--epochs", type=int, default=None, help="训练轮次")
    parser.add_argument("--batch", type=int, default=None, help="批次大小")
    parser.add_argument("--imgsz", type=int, default=None, help="输入尺寸")
    parser.add_argument("--device", type=str, default=None, help="设备")
    parser.add_argument("--name", type=str, default=None, help="实验名称")
    parser.add_argument("--teacher", type=str, default=None, help="教师模型 (知识蒸馏)")
    parser.add_argument("--stage", type=int, default=0, help="训练阶段 (0=全部, 1/2/3)")

    args = parser.parse_args()

    # 加载配置
    hyp = load_yaml(args.hyp) if args.hyp else {}

    # 构建训练参数
    train_args = build_highprec_args(args, hyp)

    print("=" * 60)
    print("OpenIris 高精度训练")
    print("=" * 60)
    print(f"数据集: {train_args['data']}")
    print(f"模型: {train_args['model']}")
    print(f"总轮次: {train_args['epochs']}")
    print(f"批次大小: {train_args['batch']}")
    print(f"设备: {train_args['device']}")
    print("=" * 60)

    if args.stage == 0 or args.stage == 1:
        # 阶段1
        results1 = train_stage1(train_args)
        stage1_weights = f"{train_args['project']}/{train_args['name']}_stage1/weights/best.pt"

        if args.stage == 1:
            print(f"\n阶段1完成! 权重: {stage1_weights}")
            return

    if args.stage == 0 or args.stage == 2:
        # 阶段2
        weights = stage1_weights if args.stage == 0 else train_args["model"]
        results2 = train_stage2(train_args, weights)
        stage2_weights = f"{train_args['project']}/{train_args['name']}_stage2/weights/best.pt"

        if args.stage == 2:
            print(f"\n阶段2完成! 权重: {stage2_weights}")
            return

    if args.stage == 0 or args.stage == 3:
        # 阶段3
        weights = stage2_weights if args.stage == 0 else train_args["model"]
        results3 = train_stage3(train_args, weights)
        final_weights = f"{train_args['project']}/{train_args['name']}_final/weights/best.pt"

        print(f"\n高精度训练完成!")
        print(f"最终权重: {final_weights}")


if __name__ == "__main__":
    main()
