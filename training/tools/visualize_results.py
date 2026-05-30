#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Training Results Visualizer

可视化训练过程和结果，包括:
- 训练损失曲线
- mAP 指标曲线
- 学习率变化
- 混淆矩阵
- 每类别 AP 柱状图

使用方法:
    # 可视化训练结果
    python training/tools/visualize_results.py --run-dir runs/train/openiris_v1

    # 指定输出目录
    python training/tools/visualize_results.py --run-dir runs/train/openiris_v1 --output plots/
"""

import argparse
import json
import sys
from pathlib import Path


def load_training_results(csv_path: str) -> dict:
    """
    加载 ultralytics 训练日志 CSV。

    Args:
        csv_path: results.csv 文件路径

    Returns:
        训练结果字典
    """
    try:
        import pandas as pd
    except ImportError:
        print("错误: 需要 pandas。请运行: pip install pandas")
        sys.exit(1)

    df = pd.read_csv(csv_path)
    # 清理列名空格
    df.columns = df.columns.str.strip()
    return df


def plot_training_curves(df, output_dir: str):
    """绘制训练曲线"""
    try:
        import matplotlib.pyplot as plt
        import matplotlib
        matplotlib.use("Agg")
    except ImportError:
        print("错误: 需要 matplotlib。请运行: pip install matplotlib")
        sys.exit(1)

    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # 1. 损失曲线
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    fig.suptitle("Training Loss Curves", fontsize=14)

    loss_cols = {
        "train/box_loss": ("Box Loss", "blue"),
        "train/cls_loss": ("Classification Loss", "red"),
        "train/dfl_loss": ("DFL Loss", "green"),
    }

    for ax, (col, (title, color)) in zip(axes.flat[:3], loss_cols.items()):
        if col in df.columns:
            ax.plot(df["epoch"], df[col], color=color, linewidth=1.5)
            ax.set_title(title)
            ax.set_xlabel("Epoch")
            ax.set_ylabel("Loss")
            ax.grid(True, alpha=0.3)

    # 总损失
    if "train/box_loss" in df.columns:
        total = (df["train/box_loss"].fillna(0) +
                 df["train/cls_loss"].fillna(0) +
                 df["train/dfl_loss"].fillna(0))
        axes[1, 1].plot(df["epoch"], total, color="purple", linewidth=1.5)
        axes[1, 1].set_title("Total Loss")
        axes[1, 1].set_xlabel("Epoch")
        axes[1, 1].set_ylabel("Loss")
        axes[1, 1].grid(True, alpha=0.3)

    plt.tight_layout()
    loss_path = output_dir / "loss_curves.png"
    plt.savefig(loss_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  保存: {loss_path}")

    # 2. mAP 曲线
    fig, ax = plt.subplots(figsize=(10, 6))
    if "metrics/mAP50(B)" in df.columns:
        ax.plot(df["epoch"], df["metrics/mAP50(B)"],
                label="mAP50", color="blue", linewidth=2)
    if "metrics/mAP50-95(B)" in df.columns:
        ax.plot(df["epoch"], df["metrics/mAP50-95(B)"],
                label="mAP50-95", color="red", linewidth=2)

    ax.set_title("Validation mAP")
    ax.set_xlabel("Epoch")
    ax.set_ylabel("mAP")
    ax.legend()
    ax.grid(True, alpha=0.3)
    ax.set_ylim(0, 1)

    plt.tight_layout()
    map_path = output_dir / "map_curves.png"
    plt.savefig(map_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  保存: {map_path}")

    # 3. Precision-Recall 曲线
    fig, ax = plt.subplots(figsize=(10, 6))
    if "metrics/precision(B)" in df.columns:
        ax.plot(df["epoch"], df["metrics/precision(B)"],
                label="Precision", color="green", linewidth=2)
    if "metrics/recall(B)" in df.columns:
        ax.plot(df["epoch"], df["metrics/recall(B)"],
                label="Recall", color="orange", linewidth=2)

    ax.set_title("Precision & Recall")
    ax.set_xlabel("Epoch")
    ax.set_ylabel("Value")
    ax.legend()
    ax.grid(True, alpha=0.3)
    ax.set_ylim(0, 1)

    plt.tight_layout()
    pr_path = output_dir / "precision_recall.png"
    plt.savefig(pr_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  保存: {pr_path}")

    # 4. 学习率曲线
    lr_cols = [c for c in df.columns if "lr" in c.lower() or "pg" in c.lower()]
    if lr_cols:
        fig, ax = plt.subplots(figsize=(10, 4))
        for col in lr_cols[:3]:
            ax.plot(df["epoch"], df[col], label=col, linewidth=1.5)
        ax.set_title("Learning Rate Schedule")
        ax.set_xlabel("Epoch")
        ax.set_ylabel("Learning Rate")
        ax.legend()
        ax.grid(True, alpha=0.3)

        plt.tight_layout()
        lr_path = output_dir / "learning_rate.png"
        plt.savefig(lr_path, dpi=150, bbox_inches="tight")
        plt.close()
        print(f"  保存: {lr_path}")


def generate_summary(run_dir: str) -> dict:
    """
    生成训练摘要。

    Args:
        run_dir: 训练运行目录

    Returns:
        摘要字典
    """
    run_dir = Path(run_dir)

    summary = {
        "run_dir": str(run_dir),
        "weights": {},
        "best_epoch": None,
        "best_map50": 0,
        "best_map50_95": 0,
    }

    # 检查权重文件
    weights_dir = run_dir / "weights"
    if weights_dir.exists():
        for f in weights_dir.glob("*.pt"):
            size_mb = f.stat().st_size / (1024 * 1024)
            summary["weights"][f.name] = f"{size_mb:.1f} MB"

    # 从 CSV 提取最佳指标
    csv_path = run_dir / "results.csv"
    if csv_path.exists():
        df = load_training_results(str(csv_path))
        if "metrics/mAP50(B)" in df.columns:
            best_idx = df["metrics/mAP50(B)"].idxmax()
            summary["best_epoch"] = int(df.loc[best_idx, "epoch"])
            summary["best_map50"] = float(df.loc[best_idx, "metrics/mAP50(B)"])
            if "metrics/mAP50-95(B)" in df.columns:
                summary["best_map50_95"] = float(df.loc[best_idx, "metrics/mAP50-95(B)"])

    return summary


def print_summary(summary: dict):
    """打印训练摘要"""
    print("\n" + "=" * 60)
    print("训练摘要")
    print("=" * 60)
    print(f"运行目录: {summary['run_dir']}")

    if summary["weights"]:
        print(f"\n权重文件:")
        for name, size in summary["weights"].items():
            print(f"  {name}: {size}")

    if summary["best_epoch"] is not None:
        print(f"\n最佳结果 (Epoch {summary['best_epoch']}):")
        print(f"  mAP50:    {summary['best_map50']:.4f}")
        print(f"  mAP50-95: {summary['best_map50_95']:.4f}")

    print("=" * 60)


def main():
    parser = argparse.ArgumentParser(
        description="OpenIris Training Results Visualizer",
    )
    parser.add_argument(
        "--run-dir",
        type=str,
        required=True,
        help="训练运行目录路径",
    )
    parser.add_argument(
        "--output",
        type=str,
        default=None,
        help="图表输出目录 (默认: <run-dir>/plots)",
    )

    args = parser.parse_args()

    run_dir = Path(args.run_dir)
    if not run_dir.exists():
        print(f"错误: 运行目录不存在: {run_dir}")
        sys.exit(1)

    output_dir = args.output or str(run_dir / "plots")

    # 生成摘要
    summary = generate_summary(str(run_dir))
    print_summary(summary)

    # 绘制曲线
    csv_path = run_dir / "results.csv"
    if csv_path.exists():
        print("\n正在生成训练曲线...")
        df = load_training_results(str(csv_path))
        plot_training_curves(df, output_dir)
        print(f"\n图表已保存到: {output_dir}")
    else:
        print(f"\n警告: 未找到 results.csv，跳过曲线绘制。")
        print(f"  预期路径: {csv_path}")


if __name__ == "__main__":
    main()
