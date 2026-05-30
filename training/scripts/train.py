#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris YOLOv11 Training Script

针对 Android NCNN 部署优化的训练入口。
基于 ultralytics 框架，添加移动端特定的约束和验证。

使用方法:
    # 基本训练
    python training/scripts/train.py --data configs/dataset_custom.yaml

    # 自定义超参数
    python training/scripts/train.py \\
        --data configs/dataset_custom.yaml \\
        --hyp configs/hyp_train.yaml \\
        --aug configs/hyp_augment.yaml \\
        --epochs 100 --batch 32

    # 从上次中断继续
    python training/scripts/train.py --resume runs/train/openiris_v1/weights/last.pt
"""

import argparse
import sys
from pathlib import Path

import yaml


# 项目约束常量 - 与 NCNN 推理端保持一致
MOBILE_CONSTRAINTS = {
    "model": "yolov11n.pt",
    "imgsz": 640,
    "min_epochs": 50,
    "recommended_epochs": 100,
    "activation": "SiLU",  # 不建议改为 RELU
}


def load_yaml(path: str) -> dict:
    """加载 YAML 配置文件"""
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def validate_constraints(args: argparse.Namespace) -> list:
    """
    验证训练参数是否满足移动端部署约束。

    Returns:
        警告信息列表
    """
    warnings = []

    # 模型变体检查
    if "model" in args and args.model:
        model_name = Path(args.model).stem.lower()
        if not model_name.endswith("n") and "yolo" in model_name:
            warnings.append(
                f"[警告] 当前模型 '{args.model}' 不是 nano 变体。"
                f"移动端推荐使用 yolov11n.pt 以获得最佳推理速度。"
            )

    # 输入尺寸检查
    if args.imgsz != MOBILE_CONSTRAINTS["imgsz"]:
        warnings.append(
            f"[警告] 输入尺寸 {args.imgsz} != {MOBILE_CONSTRAINTS['imgsz']}。"
            f"NCNN 推理端固定使用 {MOBILE_CONSTRAINTS['imgsz']}，"
            f"不一致会导致精度下降。"
        )

    # 训练轮次检查
    if args.epochs < MOBILE_CONSTRAINTS["min_epochs"]:
        warnings.append(
            f"[警告] 训练轮次 {args.epochs} 过少。"
            f"建议至少 {MOBILE_CONSTRAINTS['min_epochs']} epoch，"
            f"正式发布推荐 {MOBILE_CONSTRAINTS['recommended_epochs']}+ epoch。"
        )

    return warnings


def build_train_args(args: argparse.Namespace, hyp: dict, aug: dict) -> dict:
    """
    构建 ultralytics train() 参数字典。

    Args:
        args: 命令行参数
        hyp: 训练超参数
        aug: 数据增强参数

    Returns:
        ultralytics 兼容的参数字典
    """
    train_args = {
        # 模型
        "model": args.model or hyp.get("model", "yolov11n.pt"),

        # 数据
        "data": args.data,

        # 训练参数
        "epochs": args.epochs or hyp.get("epochs", 100),
        "batch": args.batch or hyp.get("batch", 32),
        "imgsz": args.imgsz,
        "workers": hyp.get("workers", 8),

        # 优化器
        "optimizer": hyp.get("optimizer", "auto"),
        "lr0": hyp.get("lr0", 0.01),
        "lrf": hyp.get("lrf", 0.01),
        "momentum": hyp.get("momentum", 0.937),
        "weight_decay": hyp.get("weight_decay", 0.0005),

        # 学习率调度
        "warmup_epochs": hyp.get("warmup_epochs", 3.0),
        "warmup_momentum": hyp.get("warmup_momentum", 0.8),
        "warmup_bias_lr": hyp.get("warmup_bias_lr", 0.1),

        # 损失函数
        "cls": hyp.get("cls", 0.5),
        "box": hyp.get("box", 7.5),
        "dfl": hyp.get("dfl", 1.5),

        # 正则化
        "label_smoothing": hyp.get("label_smoothing", 0.0),
        "dropout": hyp.get("dropout", 0.0),

        # 训练控制
        "patience": hyp.get("patience", 50),
        "save": hyp.get("save", True),
        "save_period": hyp.get("save_period", -1),

        # 硬件
        "device": args.device or hyp.get("device", 0),
        "amp": hyp.get("amp", True),

        # 输出
        "project": hyp.get("project", "runs/train"),
        "name": args.name or hyp.get("name", "openiris_v1"),
        "exist_ok": hyp.get("exist_ok", False),
        "pretrained": hyp.get("pretrained", True),

        # Resume
        "resume": args.resume is not None,
    }

    # 如果有 resume 权重
    if args.resume:
        train_args["model"] = args.resume

    # 合并数据增强参数
    train_args.update(aug)

    return train_args


def main():
    parser = argparse.ArgumentParser(
        description="OpenIris YOLOv11 Training Script",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )

    # 必需参数
    parser.add_argument(
        "--data",
        type=str,
        required=True,
        help="数据集配置文件路径 (YAML)",
    )

    # 可选参数
    parser.add_argument(
        "--model",
        type=str,
        default=None,
        help="预训练模型路径 (默认: yolov11n.pt)",
    )
    parser.add_argument(
        "--hyp",
        type=str,
        default=None,
        help="训练超参数配置文件路径 (YAML)",
    )
    parser.add_argument(
        "--aug",
        type=str,
        default=None,
        help="数据增强配置文件路径 (YAML)",
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=None,
        help="训练轮次 (覆盖 hyp 中的值)",
    )
    parser.add_argument(
        "--batch",
        type=int,
        default=None,
        help="批次大小 (覆盖 hyp 中的值)",
    )
    parser.add_argument(
        "--imgsz",
        type=int,
        default=MOBILE_CONSTRAINTS["imgsz"],
        help=f"输入图像尺寸 (默认: {MOBILE_CONSTRAINTS['imgsz']})",
    )
    parser.add_argument(
        "--device",
        type=str,
        default=None,
        help="训练设备 (0, 0,1, cpu)",
    )
    parser.add_argument(
        "--name",
        type=str,
        default=None,
        help="实验名称",
    )
    parser.add_argument(
        "--resume",
        type=str,
        default=None,
        help="从指定权重文件恢复训练",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="仅打印训练配置，不实际训练",
    )

    args = parser.parse_args()

    # 加载配置
    hyp = load_yaml(args.hyp) if args.hyp else {}
    aug = load_yaml(args.aug) if args.aug else {}

    # 验证约束
    warnings = validate_constraints(args)
    if warnings:
        print("=" * 60)
        print("部署约束检查:")
        for w in warnings:
            print(f"  {w}")
        print("=" * 60)

        if not args.dry_run:
            response = input("是否继续训练? [y/N]: ").strip().lower()
            if response != "y":
                print("训练已取消。")
                sys.exit(0)

    # 构建训练参数
    train_args = build_train_args(args, hyp, aug)

    # Dry run 模式
    if args.dry_run:
        print("\n训练配置预览:")
        print("-" * 40)
        for key, value in sorted(train_args.items()):
            print(f"  {key}: {value}")
        print("-" * 40)
        print("\n提示: 去掉 --dry-run 参数开始实际训练。")
        return

    # 开始训练
    try:
        from ultralytics import YOLO

        print(f"\n加载模型: {train_args['model']}")
        model = YOLO(train_args["model"])

        print(f"数据集: {train_args['data']}")
        print(f"训练轮次: {train_args['epochs']}")
        print(f"批次大小: {train_args['batch']}")
        print(f"输入尺寸: {train_args['imgsz']}")
        print(f"设备: {train_args['device']}")
        print("\n开始训练...\n")

        # 移除 model 参数，因为已经加载
        model_path = train_args.pop("model")
        results = model.train(**train_args)

        print("\n训练完成!")
        print(f"最优权重: {train_args['project']}/{train_args['name']}/weights/best.pt")
        print(f"最终权重: {train_args['project']}/{train_args['name']}/weights/last.pt")

        # 打印关键指标
        if hasattr(results, "results_dict"):
            metrics = results.results_dict
            print(f"\n训练结果:")
            print(f"  mAP50:    {metrics.get('metrics/mAP50(B)', 'N/A'):.4f}")
            print(f"  mAP50-95: {metrics.get('metrics/mAP50-95(B)', 'N/A'):.4f}")

    except ImportError:
        print("错误: 未安装 ultralytics。请运行: pip install ultralytics")
        sys.exit(1)
    except Exception as e:
        print(f"训练失败: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
