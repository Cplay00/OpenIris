#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris YOLOv11 Model Evaluation Script

评估训练好的 YOLO 模型，生成详细的性能报告。
支持 mAP、Precision、Recall、推理速度等指标。

使用方法:
    python training/scripts/evaluate.py \\
        --weights runs/train/openiris_v1/weights/best.pt \\
        --data configs/dataset_custom.yaml
"""

import argparse
import json
import sys
from pathlib import Path
from datetime import datetime


def evaluate_model(weights: str, data: str, imgsz: int = 640,
                    batch: int = 16, device: str = "0",
                    conf: float = 0.001, iou: float = 0.6,
                    save_json: bool = False) -> dict:
    """
    评估模型性能。

    Args:
        weights: 模型权重路径
        data: 数据集配置文件
        imgsz: 输入图像尺寸
        batch: 评估批次大小
        device: 计算设备
        conf: 置信度阈值
        iou: NMS IoU 阈值
        save_json: 是否保存 COCO 格式 JSON

    Returns:
        评估指标字典
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        print("错误: 未安装 ultralytics。请运行: pip install ultralytics")
        sys.exit(1)

    print(f"加载模型: {weights}")
    model = YOLO(weights)

    print(f"数据集: {data}")
    print(f"输入尺寸: {imgsz}")
    print(f"批次大小: {batch}")
    print(f"置信度阈值: {conf}")
    print(f"IoU 阈值: {iou}")
    print("\n开始评估...\n")

    # 运行验证
    results = model.val(
        data=data,
        imgsz=imgsz,
        batch=batch,
        device=device,
        conf=conf,
        iou=iou,
        save_json=save_json,
        verbose=True,
    )

    # 提取指标
    metrics = {
        "model": str(weights),
        "dataset": str(data),
        "imgsz": imgsz,
        "timestamp": datetime.now().isoformat(),

        # 整体指标
        "mAP50": float(results.box.map50),
        "mAP50-95": float(results.box.map),
        "precision": float(results.box.mp),
        "recall": float(results.box.mr),

        # 每类别指标
        "per_class_ap50": results.box.ap50.tolist() if hasattr(results.box, 'ap50') else [],
        "per_class_ap": results.box.ap.tolist() if hasattr(results.box, 'ap') else [],

        # 推理速度
        "speed_preprocess": results.speed.get("preprocess", 0),
        "speed_inference": results.speed.get("inference", 0),
        "speed_postprocess": results.speed.get("postprocess", 0),
    }

    return metrics


def print_metrics(metrics: dict):
    """格式化打印评估指标"""
    print("\n" + "=" * 60)
    print("评估结果")
    print("=" * 60)
    print(f"模型: {metrics['model']}")
    print(f"数据集: {metrics['dataset']}")
    print(f"时间: {metrics['timestamp']}")
    print("-" * 60)

    print(f"\n【整体指标】")
    print(f"  mAP50:      {metrics['mAP50']:.4f}")
    print(f"  mAP50-95:   {metrics['mAP50-95']:.4f}")
    print(f"  Precision:  {metrics['precision']:.4f}")
    print(f"  Recall:     {metrics['recall']:.4f}")

    print(f"\n【推理速度】")
    total_time = (metrics['speed_preprocess'] +
                  metrics['speed_inference'] +
                  metrics['speed_postprocess'])
    fps = 1000.0 / total_time if total_time > 0 else 0
    print(f"  预处理:     {metrics['speed_preprocess']:.1f} ms")
    print(f"  推理:       {metrics['speed_inference']:.1f} ms")
    print(f"  后处理:     {metrics['speed_postprocess']:.1f} ms")
    print(f"  总计:       {total_time:.1f} ms")
    print(f"  FPS:        {fps:.1f}")
    print("=" * 60)


def save_report(metrics: dict, output_dir: str):
    """保存评估报告到 JSON 文件"""
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    report_file = output_path / f"eval_report_{timestamp}.json"

    with open(report_file, "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2, ensure_ascii=False)

    print(f"\n评估报告已保存: {report_file}")
    return report_file


def main():
    parser = argparse.ArgumentParser(
        description="OpenIris YOLOv11 Model Evaluation",
    )

    parser.add_argument(
        "--weights",
        type=str,
        required=True,
        help="模型权重文件路径 (.pt)",
    )
    parser.add_argument(
        "--data",
        type=str,
        required=True,
        help="数据集配置文件路径 (YAML)",
    )
    parser.add_argument(
        "--imgsz",
        type=int,
        default=640,
        help="输入图像尺寸 (默认: 640)",
    )
    parser.add_argument(
        "--batch",
        type=int,
        default=16,
        help="评估批次大小 (默认: 16)",
    )
    parser.add_argument(
        "--device",
        type=str,
        default="0",
        help="计算设备 (默认: 0)",
    )
    parser.add_argument(
        "--conf",
        type=float,
        default=0.001,
        help="置信度阈值 (默认: 0.001)",
    )
    parser.add_argument(
        "--iou",
        type=float,
        default=0.6,
        help="NMS IoU 阈值 (默认: 0.6)",
    )
    parser.add_argument(
        "--save-json",
        action="store_true",
        help="保存 COCO 格式评估 JSON",
    )
    parser.add_argument(
        "--output",
        type=str,
        default="runs/eval",
        help="评估报告输出目录 (默认: runs/eval)",
    )

    args = parser.parse_args()

    # 运行评估
    metrics = evaluate_model(
        weights=args.weights,
        data=args.data,
        imgsz=args.imgsz,
        batch=args.batch,
        device=args.device,
        conf=args.conf,
        iou=args.iou,
        save_json=args.save_json,
    )

    # 打印指标
    print_metrics(metrics)

    # 保存报告
    save_report(metrics, args.output)

    # 检查是否达到部署标准
    print("\n【部署检查】")
    if metrics["mAP50"] >= 0.5:
        print("  ✓ mAP50 >= 0.5，达到基本部署标准")
    else:
        print("  ✗ mAP50 < 0.5，建议继续训练或优化数据")

    if metrics["mAP50-95"] >= 0.3:
        print("  ✓ mAP50-95 >= 0.3，检测精度良好")
    else:
        print("  ✗ mAP50-95 < 0.3，建议增加训练轮次或数据量")

    fps_estimate = 1000.0 / (
        metrics["speed_preprocess"] +
        metrics["speed_inference"] +
        metrics["speed_postprocess"]
    )
    print(f"\n  注意: 上述 FPS 为 GPU 评估值。")
    print(f"  Android 端推理速度请使用 benchmark.py 测试。")


if __name__ == "__main__":
    main()
