#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
YOLOv11 .pt -> ONNX -> NCNN 杞崲鑴氭湰

浣跨敤鏂规硶:
    python tools/export_yolov11_ncnn.py --weights path/to/yolov11n.pt --imgsz 640

渚濊禆:
    - ultralytics
    - onnx
    - onnxsim (鍙€夛紝鐢ㄤ簬绠€鍖?ONNX)
    - ncnn 杞崲宸ュ叿 (ncnnoptimize, onnx2ncnn)

杈撳嚭:
    - models/yolov11n.onnx
    - models/yolov11n.ncnn.param
    - models/yolov11n.ncnn.bin
"""

import argparse
import os
import subprocess
import sys
import shutil
from pathlib import Path


def export_onnx(weights_path: str, imgsz: int = 640, simplify: bool = True):
    """
    浣跨敤 ultralytics 瀵煎嚭 ONNX 妯″瀷
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        print("閿欒: 璇峰畨瑁?ultralytics: pip install ultralytics")
        sys.exit(1)

    print(f"[1/4] 鍔犺浇妯″瀷: {weights_path}")
    model = YOLO(weights_path)

    print(f"[2/4] 瀵煎嚭 ONNX (imgsz={imgsz})...")
    model.export(
        format="onnx",
        imgsz=imgsz,
        simplify=simplify,
        opset=12,
    )

    # 鑾峰彇瀵煎嚭璺緞
    onnx_path = Path(weights_path).with_suffix(".onnx")
    if not onnx_path.exists():
        # ultralytics 鍙兘浼氬湪鏂囦欢鍚嶄腑娣诲姞 _ncnn 鎴栧叾浠栧悗缂€
        possible_paths = list(Path(weights_path).parent.glob("*.onnx"))
        if possible_paths:
            onnx_path = possible_paths[0]

    print(f"[2/4] ONNX 瀵煎嚭瀹屾垚: {onnx_path}")
    return str(onnx_path)


def simplify_onnx(onnx_path: str):
    """
    浣跨敤 onnxsim 绠€鍖?ONNX 妯″瀷
    """
    try:
        import onnx
        from onnxsim import simplify as onnx_simplify
    except ImportError:
        print("璀﹀憡: 鏈畨瑁?onnxsim锛岃烦杩囩畝鍖栨楠?)
        return onnx_path

    print(f"[3/4] 绠€鍖?ONNX 妯″瀷...")
    model = onnx.load(onnx_path)
    model_simp, check = onnx_simplify(model)

    if check:
        simplified_path = onnx_path.replace(".onnx", "_sim.onnx")
        onnx.save(model_simp, simplified_path)
        print(f"[3/4] ONNX 绠€鍖栧畬鎴? {simplified_path}")
        return simplified_path
    else:
        print("璀﹀憡: ONNX 绠€鍖栧け璐ワ紝浣跨敤鍘熷妯″瀷")
        return onnx_path


def convert_ncnn(onnx_path: str, output_dir: str):
    """
    浣跨敤 onnx2ncnn 灏?ONNX 杞崲涓?NCNN 鏍煎紡
    """
    onnx2ncnn = os.environ.get("ONNX2NCNN", "onnx2ncnn")
    ncnnoptimize = os.environ.get("NCNNOPTIMIZE", "ncnnoptimize")

    # 妫€鏌ュ伐鍏锋槸鍚﹀瓨鍦?
    if not shutil.which(onnx2ncnn):
        print(f"閿欒: 鎵句笉鍒?onnx2ncnn 宸ュ叿")
        print("璇蜂笅杞?NCNN 宸ュ叿鍖呭苟娣诲姞鍒?PATH锛屾垨璁剧疆 ONNX2NCNN 鐜鍙橀噺")
        print("涓嬭浇鍦板潃: https://github.com/Tencent/ncnn/releases")
        sys.exit(1)

    base_name = Path(onnx_path).stem
    param_path = os.path.join(output_dir, f"{base_name}.ncnn.param")
    bin_path = os.path.join(output_dir, f"{base_name}.ncnn.bin")

    print(f"[4/4] 杞崲 NCNN 妯″瀷...")

    # 绗竴姝? onnx -> ncnn
    cmd = [onnx2ncnn, onnx_path, param_path, bin_path]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)

    if result.returncode != 0:
        print(f"閿欒: ONNX 杞?NCNN 澶辫触")
        print(result.stderr)
        sys.exit(1)

    print(f"[4/4] NCNN 妯″瀷杞崲瀹屾垚:")
    print(f"  - Param: {param_path}")
    print(f"  - Bin: {bin_path}")

    # 绗簩姝? FP16 浼樺寲 (鍙€?
    if shutil.which(ncnnoptimize):
        print(f"[4/4] 搴旂敤 FP16 浼樺寲...")
        opt_param = os.path.join(output_dir, f"{base_name}.ncnn.opt.param")
        opt_bin = os.path.join(output_dir, f"{base_name}.ncnn.opt.bin")

        cmd = [ncnnoptimize, param_path, bin_path, opt_param, opt_bin, "65536"]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)

        if result.returncode == 0:
            print(f"[4/4] FP16 浼樺寲瀹屾垚:")
            print(f"  - Opt Param: {opt_param}")
            print(f"  - Opt Bin: {opt_bin}")
            # 浣跨敤浼樺寲鍚庣殑妯″瀷
            param_path = opt_param
            bin_path = opt_bin
        else:
            print("璀﹀憡: FP16 浼樺寲澶辫触锛屼娇鐢ㄦ湭浼樺寲妯″瀷")

    return param_path, bin_path


def copy_to_assets(param_path: str, bin_path: str, assets_dir: str):
    """
    灏?NCNN 妯″瀷澶嶅埗鍒?Android assets 鐩綍
    """

    model_name = Path(param_path).stem.replace(".ncnn", "").replace(".opt", "")
    target_dir = os.path.join(assets_dir, f"{model_name}_ncnn_model")

    os.makedirs(target_dir, exist_ok=True)

    # 澶嶅埗 param 鍜?bin
    shutil.copy2(param_path, os.path.join(target_dir, f"{model_name}.ncnn.param"))
    shutil.copy2(bin_path, os.path.join(target_dir, f"{model_name}.ncnn.bin"))

    print(f"[5/4] 妯″瀷宸插鍒跺埌 assets:")
    print(f"  - {target_dir}")

    return target_dir


def main():
    parser = argparse.ArgumentParser(description="YOLOv11 .pt -> ONNX -> NCNN 杞崲宸ュ叿")
    parser.add_argument("--weights", type=str, required=True, help="YOLOv11 .pt 鏉冮噸鏂囦欢璺緞")
    parser.add_argument("--imgsz", type=int, default=640, help="杈撳叆鍥惧儚灏哄 (榛樿: 640)")
    parser.add_argument("--simplify", action="store_true", default=True, help="绠€鍖?ONNX 妯″瀷")
    parser.add_argument("--output", type=str, default="models", help="杈撳嚭鐩綍 (榛樿: models)")
    parser.add_argument("--assets", type=str, default=None, help="Android assets 鐩綍璺緞锛屾寚瀹氬垯鑷姩澶嶅埗")
    parser.add_argument("--no-fp16", action="store_true", help="绂佺敤 FP16 浼樺寲")

    args = parser.parse_args()

    # 鍒涘缓杈撳嚭鐩綍
    os.makedirs(args.output, exist_ok=True)

    # 姝ラ 1: 瀵煎嚭 ONNX
    onnx_path = export_onnx(args.weights, args.imgsz, args.simplify)

    # 姝ラ 2: 绠€鍖?ONNX (鍙€?
    if args.simplify:
        onnx_path = simplify_onnx(onnx_path)

    # 姝ラ 3: 杞崲 NCNN
    param_path, bin_path = convert_ncnn(onnx_path, args.output)

    # 姝ラ 4: 澶嶅埗鍒?assets (鍙€?
    if args.assets:
        copy_to_assets(param_path, bin_path, args.assets)

    print("\n鉁?杞崲娴佺▼瀹屾垚!")
    print(f"妯″瀷鏂囦欢浣嶇疆: {args.output}")


if __name__ == "__main__":
    main()

