#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris YOLOv11 Model Evaluation Script

璇勪及璁粌濂界殑 YOLO 妯″瀷锛岀敓鎴愯缁嗙殑鎬ц兘鎶ュ憡銆?
鏀寔 mAP銆丳recision銆丷ecall銆佹帹鐞嗛€熷害绛夋寚鏍囥€?

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
    璇勪及妯″瀷鎬ц兘銆?

    Args:
        weights: 妯″瀷鏉冮噸璺緞
        data: 鏁版嵁闆嗛厤缃枃浠?
        imgsz: 杈撳叆鍥惧儚灏哄
        batch: 璇勪及鎵规澶у皬
        device: 璁$畻璁惧
        conf: 缃俊搴﹂槇鍊?
        iou: NMS IoU 闃堝€?
        save_json: 鏄惁淇濆瓨 COCO 鏍煎紡 JSON

    Returns:
        璇勪及鎸囨爣瀛楀吀
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        print("閿欒: 鏈畨瑁?ultralytics銆傝杩愯: pip install ultralytics")
        sys.exit(1)

    print(f"鍔犺浇妯″瀷: {weights}")
    model = YOLO(weights)

    print(f"鏁版嵁闆? {data}")
    print(f"杈撳叆灏哄: {imgsz}")
    print(f"鎵规澶у皬: {batch}")
    print(f"缃俊搴﹂槇鍊? {conf}")
    print(f"IoU 闃堝€? {iou}")
    print("\n寮€濮嬭瘎浼?..\n")

    # 杩愯楠岃瘉
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

        # 姣忕被鍒寚鏍?
        "per_class_ap50": results.box.ap50.tolist() if hasattr(results.box, 'ap50') else [],
        "per_class_ap": results.box.ap.tolist() if hasattr(results.box, 'ap') else [],

        # 鎺ㄧ悊閫熷害
        "speed_preprocess": results.speed.get("preprocess", 0),
        "speed_inference": results.speed.get("inference", 0),
        "speed_postprocess": results.speed.get("postprocess", 0),
    }

    return metrics


def print_metrics(metrics: dict):
    """鏍煎紡鍖栨墦鍗拌瘎浼版寚鏍?""
    print("\n" + "=" * 60)
    print("璇勪及缁撴灉")
    print("=" * 60)
    print(f"妯″瀷: {metrics['model']}")
    print(f"鏁版嵁闆? {metrics['dataset']}")
    print(f"鏃堕棿: {metrics['timestamp']}")
    print("-" * 60)

    print(f"\n銆愭暣浣撴寚鏍囥€?)
    print(f"  mAP50:      {metrics['mAP50']:.4f}")
    print(f"  mAP50-95:   {metrics['mAP50-95']:.4f}")
    print(f"  Precision:  {metrics['precision']:.4f}")
    print(f"  Recall:     {metrics['recall']:.4f}")

    print(f"\n銆愭帹鐞嗛€熷害銆?)
    total_time = (metrics['speed_preprocess'] +
                  metrics['speed_inference'] +
                  metrics['speed_postprocess'])
    fps = 1000.0 / total_time if total_time > 0 else 0
    print(f"  棰勫鐞?     {metrics['speed_preprocess']:.1f} ms")
    print(f"  鎺ㄧ悊:       {metrics['speed_inference']:.1f} ms")
    print(f"  鍚庡鐞?     {metrics['speed_postprocess']:.1f} ms")
    print(f"  鎬昏:       {total_time:.1f} ms")
    print(f"  FPS:        {fps:.1f}")
    print("=" * 60)


def save_report(metrics: dict, output_dir: str):
    """淇濆瓨璇勪及鎶ュ憡鍒?JSON 鏂囦欢"""
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    report_file = output_path / f"eval_report_{timestamp}.json"

    with open(report_file, "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2, ensure_ascii=False)

    print(f"\n璇勪及鎶ュ憡宸蹭繚瀛? {report_file}")
    return report_file


def main():
    parser = argparse.ArgumentParser(
        description="OpenIris YOLOv11 Model Evaluation",
    )

    parser.add_argument(
        "--weights",
        type=str,
        required=True,
        help="妯″瀷鏉冮噸鏂囦欢璺緞 (.pt)",
    )
    parser.add_argument(
        "--data",
        type=str,
        required=True,
        help="鏁版嵁闆嗛厤缃枃浠惰矾寰?(YAML)",
    )
    parser.add_argument(
        "--imgsz",
        type=int,
        default=640,
        help="杈撳叆鍥惧儚灏哄 (榛樿: 640)",
    )
    parser.add_argument(
        "--batch",
        type=int,
        default=16,
        help="璇勪及鎵规澶у皬 (榛樿: 16)",
    )
    parser.add_argument(
        "--device",
        type=str,
        default="0",
        help="璁$畻璁惧 (榛樿: 0)",
    )
    parser.add_argument(
        "--conf",
        type=float,
        default=0.001,
        help="缃俊搴﹂槇鍊?(榛樿: 0.001)",
    )
    parser.add_argument(
        "--iou",
        type=float,
        default=0.6,
        help="NMS IoU 闃堝€?(榛樿: 0.6)",
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
        help="璇勪及鎶ュ憡杈撳嚭鐩綍 (榛樿: runs/eval)",
    )

    args = parser.parse_args()

    # 杩愯璇勪及
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

    # 淇濆瓨鎶ュ憡
    save_report(metrics, args.output)

    # 妫€鏌ユ槸鍚﹁揪鍒伴儴缃叉爣鍑?
    print("\n銆愰儴缃叉鏌ャ€?)
    if metrics["mAP50"] >= 0.5:
        print("  鉁?mAP50 >= 0.5锛岃揪鍒板熀鏈儴缃叉爣鍑?)
    else:
        print("  鉁?mAP50 < 0.5锛屽缓璁户缁缁冩垨浼樺寲鏁版嵁")

    if metrics["mAP50-95"] >= 0.3:
        print("  鉁?mAP50-95 >= 0.3锛屾娴嬬簿搴﹁壇濂?)
    else:
        print("  鉁?mAP50-95 < 0.3锛屽缓璁鍔犺缁冭疆娆℃垨鏁版嵁閲?)

    total_speed = (
        metrics["speed_preprocess"] +
        metrics["speed_inference"] +
        metrics["speed_postprocess"]
    )
    fps_estimate = 1000.0 / total_speed if total_speed > 0 else 0
    print(f"\n  娉ㄦ剰: 涓婅堪 FPS 涓?GPU 璇勪及鍊笺€?)
    print(f"  Android 绔帹鐞嗛€熷害璇蜂娇鐢?benchmark.py 娴嬭瘯銆?)


if __name__ == "__main__":
    main()
