#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris YOLOv11 Model Evaluation Script

璇勪及璁[?]粌濂界殑 YOLO 妯[?]瀷锛岀敓鎴愯[?]缁嗙殑鎬[?]兘鎶[?]憡銆?
# 在验证集上评估 YOLO 模型的 mAP、Precision、Recall 等指标

浣跨敤鏂规硶:
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
    璇勪及妯[?]瀷鎬[?]兘銆?

    Args:
        weights: 模型权重文件路径
        data: 数据集配置路径
        imgsz: 推理图像尺寸
        batch: 评估批次大小
        device: 璁$畻璁惧[?]
        conf: 置信度阈值
        iou: NMS IoU 阈值
        save_json: 是否保存为 COCO 格式 JSON

        评估结果字典
        璇勪及鎸囨爣瀛楀吀
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        print("閿欒[?]: 鏈[?]畨瑁?ultralytics銆傝[?]杩愯[?]: pip install ultralytics")
        sys.exit(1)

    print(f"鍔犺浇妯[?]瀷: {weights}")
    model = YOLO(weights)

    print(f"鏁版嵁闆? {data}")
    print(f"杈撳叆灏哄[?]: {imgsz}")
    print(f"鎵规[?]澶[?]皬: {batch}")
    print(f"缃[?]俊搴[?]槇鍊? {conf}")
    print(f"IoU 阈值: {iou}")
    print("\n加载模型中...\n")

    # 杩愯[?]楠岃瘉
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

    # 鎻愬彇鎸囨爣
    metrics = {
        "model": str(weights),
        "dataset": str(data),
        "imgsz": imgsz,
        "timestamp": datetime.now().isoformat(),

        # 鏁翠綋鎸囨爣
        "mAP50": float(results.box.map50),
        "mAP50-95": float(results.box.map),
        "precision": float(results.box.mp),
        "recall": float(results.box.mr),

        # 姣忕被鍒[?]寚鏍?
        "per_class_ap50": results.box.ap50.tolist() if hasattr(results.box, 'ap50') else [],
        "per_class_ap": results.box.ap.tolist() if hasattr(results.box, 'ap') else [],

        # 鎺[?]悊閫熷害
        "speed_preprocess": results.speed.get("preprocess", 0),
        "speed_inference": results.speed.get("inference", 0),
        "speed_postprocess": results.speed.get("postprocess", 0),
    }

    return metrics


def print_metrics(metrics: dict):
    """格式化打印评估指标"""
    print("\n" + "=" * 60)
    print("璇勪及缁撴灉")
    print("=" * 60)
    print(f"妯[?]瀷: {metrics['model']}")
    print(f"鏁版嵁闆? {metrics['dataset']}")
    print(f"鏃堕棿: {metrics['timestamp']}")
    print("-" * 60)

    print(f"\n评估完成:")
    print(f"  mAP50:      {metrics['mAP50']:.4f}")
    print(f"  mAP50-95:   {metrics['mAP50-95']:.4f}")
    print(f"  Precision:  {metrics['precision']:.4f}")
    print(f"  Recall:     {metrics['recall']:.4f}")

    print(f"\n评估指标:")
    total_time = (metrics['speed_preprocess'] +
                  metrics['speed_inference'] +
                  metrics['speed_postprocess'])
    fps = 1000.0 / total_time if total_time > 0 else 0
    print(f"  棰勫[?]鐞?     {metrics['speed_preprocess']:.1f} ms")
    print(f"  鎺[?]悊:       {metrics['speed_inference']:.1f} ms")
    print(f"  鍚庡[?]鐞?     {metrics['speed_postprocess']:.1f} ms")
    print(f"  鎬昏[?]:       {total_time:.1f} ms")
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

    print(f"\n璇勪及鎶[?]憡宸蹭繚瀛? {report_file}")
    return report_file


def main():
    parser = argparse.ArgumentParser(
        description="OpenIris YOLOv11 Model Evaluation",
    )

    parser.add_argument(
        "--weights",
        type=str,
        required=True,
        help="妯[?]瀷鏉冮噸鏂囦欢璺[?]緞 (.pt)",
    )
    parser.add_argument(
        "--data",
        type=str,
        required=True,
        help="鏁版嵁闆嗛厤缃[?]枃浠惰矾寰?(YAML)",
    )
    parser.add_argument(
        "--imgsz",
        type=int,
        default=640,
        help="杈撳叆鍥惧儚灏哄[?] (榛樿[?]: 640)",
    )
    parser.add_argument(
        "--batch",
        type=int,
        default=16,
        help="璇勪及鎵规[?]澶[?]皬 (榛樿[?]: 16)",
    )
    parser.add_argument(
        "--device",
        type=str,
        default="0",
        help="璁$畻璁惧[?] (榛樿[?]: 0)",
    )
    parser.add_argument(
        "--conf",
        type=float,
        default=0.001,
        help="缃[?]俊搴[?]槇鍊?(榛樿[?]: 0.001)",
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
        help="淇濆瓨 COCO 鏍煎紡璇勪及 JSON",
    )
    parser.add_argument(
        "--output",
        type=str,
        default="runs/eval",
        help="璇勪及鎶[?]憡杈撳嚭鐩[?]綍 (榛樿[?]: runs/eval)",
    )

    args = parser.parse_args()

    # 杩愯[?]璇勪及
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

    # 鎵撳嵃鎸囨爣
    print_metrics(metrics)

    # 淇濆瓨鎶[?]憡
    save_report(metrics, args.output)

    # 计算并输出
    print("\n性能指标分析:")
    if metrics["mAP50"] >= 0.5:
        print("  > mAP50 >= 0.5, 达到部署标准")
    else:
        print("  鉁?mAP50 < 0.5锛屽缓璁[?]户缁[?]缁冩垨浼樺寲鏁版嵁")

    if metrics["mAP50-95"] >= 0.3:
        print("  > mAP50-95 >= 0.3, 检测精度良好")
    else:
        print("  > mAP50-95 < 0.3, 建议增加训练轮次或数据量")

    total_speed = (
        metrics["speed_preprocess"] +
        metrics["speed_inference"] +
        metrics["speed_postprocess"]
    )
    fps_estimate = 1000.0 / total_speed if total_speed > 0 else 0
    print(f"\n  提示: 实际 FPS 取决于 GPU 评估设备")
    print(f"  Android 实际性能请用 benchmark.py 测试")


if __name__ == "__main__":
    main()
