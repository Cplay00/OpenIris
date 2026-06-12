#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Training Results Visualizer

鍙鍖栬缁冭繃绋嬪拰缁撴灉锛屽寘鎷?
- 璁粌鎹熷け鏇茬嚎
- mAP 鎸囨爣鏇茬嚎
- 瀛︿範鐜囧彉鍖?
- 娣锋穯鐭╅樀
- 姣忕被鍒?AP 鏌辩姸鍥?

浣跨敤鏂规硶:
    # 鍙鍖栬缁冪粨鏋?
    python training/tools/visualize_results.py --run-dir runs/train/openiris_v1

    # 鎸囧畾杈撳嚭鐩綍
    python training/tools/visualize_results.py --run-dir runs/train/openiris_v1 --output plots/
"""

import argparse
import json
import sys
from pathlib import Path


def load_training_results(csv_path: str) -> dict:
    """
    鍔犺浇 ultralytics 璁粌鏃ュ織 CSV銆?

    Args:
        csv_path: results.csv 鏂囦欢璺緞

    Returns:
        璁粌缁撴灉瀛楀吀
    """
    try:
        import pandas as pd
    except ImportError:
        print("閿欒: 闇€瑕?pandas銆傝杩愯: pip install pandas")
        sys.exit(1)

    df = pd.read_csv(csv_path)
    # 娓呯悊鍒楀悕绌烘牸
    df.columns = df.columns.str.strip()
    return df


def plot_training_curves(df, output_dir: str):
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("Error: matplotlib not installed. Run: pip install matplotlib")
        sys.exit(1)

    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # 1. 鎹熷け鏇茬嚎
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

    # 鎬绘崯澶?
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
    print(f"  淇濆瓨: {loss_path}")

    # 2. mAP 鏇茬嚎
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
    print(f"  淇濆瓨: {map_path}")

    # 3. Precision-Recall 鏇茬嚎
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
    print(f"  淇濆瓨: {pr_path}")

    # 4. 瀛︿範鐜囨洸绾?
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
        print(f"  淇濆瓨: {lr_path}")


def generate_summary(run_dir: str) -> dict:
    """
    鐢熸垚璁粌鎽樿銆?

    Args:
        run_dir: 璁粌杩愯鐩綍

    Returns:
        鎽樿瀛楀吀
    """
    run_dir = Path(run_dir)

    summary = {
        "run_dir": str(run_dir),
        "weights": {},
        "best_epoch": None,
        "best_map50": 0,
        "best_map50_95": 0,
    }

    # 妫€鏌ユ潈閲嶆枃浠?
    weights_dir = run_dir / "weights"
    if weights_dir.exists():
        for f in weights_dir.glob("*.pt"):
            size_mb = f.stat().st_size / (1024 * 1024)
            summary["weights"][f.name] = f"{size_mb:.1f} MB"

    # 浠?CSV 鎻愬彇鏈€浣虫寚鏍?
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
    """鎵撳嵃璁粌鎽樿"""
    print("\n" + "=" * 60)
    print("璁粌鎽樿")
    print("=" * 60)
    print(f"杩愯鐩綍: {summary['run_dir']}")

    if summary["weights"]:
        print(f"\n鏉冮噸鏂囦欢:")
        for name, size in summary["weights"].items():
            print(f"  {name}: {size}")

    if summary["best_epoch"] is not None:
        print(f"\n鏈€浣崇粨鏋?(Epoch {summary['best_epoch']}):")
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
        help="璁粌杩愯鐩綍璺緞",
    )
    parser.add_argument(
        "--output",
        type=str,
        default=None,
        help="鍥捐〃杈撳嚭鐩綍 (榛樿: <run-dir>/plots)",
    )

    args = parser.parse_args()

    run_dir = Path(args.run_dir)
    if not run_dir.exists():
        print(f"閿欒: 杩愯鐩綍涓嶅瓨鍦? {run_dir}")
        sys.exit(1)

    output_dir = args.output or str(run_dir / "plots")

    # 鐢熸垚鎽樿
    summary = generate_summary(str(run_dir))
    print_summary(summary)

    # 缁樺埗鏇茬嚎
    csv_path = run_dir / "results.csv"
    if csv_path.exists():
        print("\n姝e湪鐢熸垚璁粌鏇茬嚎...")
        df = load_training_results(str(csv_path))
        plot_training_curves(df, output_dir)
        print(f"\n鍥捐〃宸蹭繚瀛樺埌: {output_dir}")
    else:
        print(f"\n璀﹀憡: 鏈壘鍒?results.csv锛岃烦杩囨洸绾跨粯鍒躲€?)
        print(f"  棰勬湡璺緞: {csv_path}")


if __name__ == "__main__":
    main()
