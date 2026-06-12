#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Export Pipeline

绔埌绔ā鍨嬪鍑烘祦姘寸嚎: .pt 鈫?ONNX 鈫?NCNN 鈫?Android Assets
澧炲己鐗堝鍑鸿剼鏈紝闆嗘垚楠岃瘉鍜屽熀鍑嗘祴璇曘€?

浣跨敤鏂规硶:
    # 鍩烘湰瀵煎嚭
    python training/tools/export_pipeline.py \\
        --weights runs/train/openiris_v1/weights/best.pt

    # 瀵煎嚭骞跺悓姝ュ埌 Android assets
    python training/tools/export_pipeline.py \\
        --weights runs/train/openiris_v1/weights/best.pt \\
        --assets ../buildapk/app/src/main/assets/models/my_model

    # 鎸囧畾杈撳嚭鐩綍
    python training/tools/export_pipeline.py \\
        --weights runs/train/openiris_v1/weights/best.pt \\
        --output models/my_model

瀵煎嚭娴佺▼:
    1. 鍔犺浇 PyTorch 妯″瀷 (.pt)
    2. 瀵煎嚭 ONNX 鏍煎紡
    3. 绠€鍖?ONNX 鍥?(鍙€?
    4. 杞崲涓?NCNN 鏍煎紡 (.param + .bin)
    5. FP16 浼樺寲
    6. 楠岃瘉瀵煎嚭妯″瀷
    7. 鍚屾鍒?Android assets (鍙€?
    8. 鐢熸垚 labels.txt
"""

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path


def find_ncnn_tools() -> dict:
    """
    鏌ユ壘 NCNN 杞崲宸ュ叿銆?

    Returns:
        宸ュ叿璺緞瀛楀吀
    """
    tools = {}

    # 灏濊瘯浠?PATH 鏌ユ壘
    for tool_name in ["onnx2ncnn", "ncnnoptimize"]:
        tool_path = shutil.which(tool_name)
        if tool_path:
            tools[tool_name] = tool_path

    # 灏濊瘯浠庡父瑙佺洰褰曟煡鎵?
    search_dirs = [
        Path.home() / "ncnn" / "build" / "tools" / "onnx",
        Path.home() / "ncnn" / "build" / "tools",
        Path("/usr/local/bin"),
        Path("/opt/ncnn/bin"),
    ]

    for search_dir in search_dirs:
        if not search_dir.exists():
            continue
        for tool_name in ["onnx2ncnn", "ncnnoptimize"]:
            if tool_name not in tools:
                tool_path = search_dir / tool_name
                if tool_path.exists():
                    tools[tool_name] = str(tool_path)

    return tools


def export_onnx(weights_path: str, imgsz: int = 640,
                 simplify: bool = True, opset: int = 12) -> str:
    """
    瀵煎嚭 ONNX 妯″瀷銆?

    Args:
        weights_path: PyTorch 鏉冮噸璺緞
        imgsz: 杈撳叆鍥惧儚灏哄
        simplify: 鏄惁绠€鍖?ONNX 鍥?
        opset: ONNX opset 鐗堟湰

    Returns:
        ONNX 鏂囦欢璺緞
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        print("閿欒: 鏈畨瑁?ultralytics銆傝杩愯: pip install ultralytics")
        sys.exit(1)

    print(f"[1/5] 鍔犺浇妯″瀷: {weights_path}")
    model = YOLO(weights_path)

    print(f"[2/5] 瀵煎嚭 ONNX (imgsz={imgsz}, opset={opset})...")
    model.export(
        format="onnx",
        imgsz=imgsz,
        simplify=simplify,
        opset=opset,
    )

    onnx_path = Path(weights_path).with_suffix(".onnx")
    if not onnx_path.exists():
        # ultralytics 鍙兘瀵煎嚭鍒颁笉鍚屼綅缃?
        alt_path = Path(weights_path).parent / f"{Path(weights_path).stem}.onnx"
        if alt_path.exists():
            onnx_path = alt_path
        else:
            print(f"閿欒: ONNX 鏂囦欢鏈壘鍒? {onnx_path}")
            sys.exit(1)

    print(f"    ONNX 瀵煎嚭瀹屾垚: {onnx_path}")
    return str(onnx_path)


def convert_ncnn(onnx_path: str, output_dir: str,
                  ncnn_tools: dict = None) -> tuple:
    """
    灏?ONNX 杞崲涓?NCNN 鏍煎紡銆?

    Args:
        onnx_path: ONNX 鏂囦欢璺緞
        output_dir: 杈撳嚭鐩綍
        ncnn_tools: NCNN 宸ュ叿璺緞瀛楀吀

    Returns:
        (param_path, bin_path) 鍏冪粍
    """
    os.makedirs(output_dir, exist_ok=True)

    model_name = Path(onnx_path).stem
    param_path = os.path.join(output_dir, f"{model_name}.ncnn.param")
    bin_path = os.path.join(output_dir, f"{model_name}.ncnn.bin")

    if ncnn_tools and "onnx2ncnn" in ncnn_tools:
        onnx2ncnn = ncnn_tools["onnx2ncnn"]
    else:
        onnx2ncnn = shutil.which("onnx2ncnn")
        if not onnx2ncnn:
            print("璀﹀憡: 鏈壘鍒?onnx2ncnn 宸ュ叿銆?)
            print("璇蜂粠 https://github.com/Tencent/ncnn/releases 涓嬭浇 NCNN 宸ュ叿鍖呫€?)
            print("鎴栦娇鐢?ultralytics 鍐呯疆鐨?NCNN 瀵煎嚭:")
            print(f"  yolo export model={onnx_path.replace('.onnx', '.pt')} format=ncnn")
            return None, None

    print(f"[3/5] 杞崲 NCNN...")
    cmd = [onnx2ncnn, onnx_path, param_path, bin_path]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
    if result.returncode != 0:
        print(f"Error: onnx2ncnn failed: {result.stderr}")
        return None, None

    # FP16 浼樺寲
    if ncnn_tools and "ncnnoptimize" in ncnn_tools:
        ncnnoptimize = ncnn_tools["ncnnoptimize"]
    else:
        ncnnoptimize = shutil.which("ncnnoptimize")

    if ncnnoptimize:
        print(f"[4/5] FP16 浼樺寲...")
        opt_param = param_path.replace(".param", ".opt.param")
        opt_bin = bin_path.replace(".bin", ".opt.bin")
        cmd = [ncnnoptimize, param_path, bin_path, opt_param, opt_bin, "65536"]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
        if result.returncode == 0:
            # 鐢ㄤ紭鍖栧悗鐨勬枃浠舵浛鎹?
            shutil.move(opt_param, param_path)
            shutil.move(opt_bin, bin_path)
        else:
            print(f"璀﹀憡: FP16 浼樺寲澶辫触锛屼娇鐢ㄥ師濮嬫ā鍨? {result.stderr}")
    else:
        print("[4/5] 璺宠繃 FP16 浼樺寲 (ncnnoptimize 鏈壘鍒?")

    print(f"    NCNN 杞崲瀹屾垚:")
    print(f"    - {param_path}")
    print(f"    - {bin_path}")

    return param_path, bin_path


def generate_labels(weights_path: str, output_dir: str) -> str:
    """
    浠庤缁冪粨鏋滅敓鎴?labels.txt銆?

    Args:
        weights_path: 妯″瀷鏉冮噸璺緞
        output_dir: 杈撳嚭鐩綍

    Returns:
        labels.txt 鏂囦欢璺緞
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        print("璀﹀憡: 鏃犳硶鐢熸垚 labels.txt (ultralytics 鏈畨瑁?")
        return None

    model = YOLO(weights_path)
    names = model.names

    labels_path = os.path.join(output_dir, "labels.txt")
    with open(labels_path, "w", encoding="utf-8") as f:
        for i in range(len(names)):
            f.write(f"{names[i]}\n")

    print(f"[5/5] 鐢熸垚 labels.txt: {labels_path}")
    print(f"    绫诲埆鏁? {len(names)}")
    return labels_path


def copy_to_assets(output_dir: str, assets_dir: str):
    """
    澶嶅埗妯″瀷鏂囦欢鍒?Android assets 鐩綍銆?

    Args:
        output_dir: 妯″瀷杈撳嚭鐩綍
        assets_dir: Android assets 鐩綍
    """
    os.makedirs(assets_dir, exist_ok=True)

    files_to_copy = []
    for ext in [".param", ".bin", ".txt"]:
        for f in Path(output_dir).glob(f"*{ext}"):
            files_to_copy.append(f)

    for src_file in files_to_copy:
        dst_file = Path(assets_dir) / src_file.name
        shutil.copy2(src_file, dst_file)
        print(f"    澶嶅埗: {src_file.name} 鈫?{assets_dir}")

    # 閲嶅懡鍚嶄负 model.param 鍜?model.bin锛堜笌 Android 浠g爜鍖归厤锛?
    for f in Path(assets_dir).glob("*.ncnn.param"):
        target = Path(assets_dir) / "model.param"
        if f != target:
            f.rename(target)
            print(f"    閲嶅懡鍚? {f.name} 鈫?model.param")

    for f in Path(assets_dir).glob("*.ncnn.bin"):
        target = Path(assets_dir) / "model.bin"
        if f != target:
            f.rename(target)
            print(f"    閲嶅懡鍚? {f.name} 鈫?model.bin")

    print(f"\n    Android assets 宸叉洿鏂? {assets_dir}")


def main():
    parser = argparse.ArgumentParser(
        description="OpenIris Export Pipeline: .pt 鈫?ONNX 鈫?NCNN 鈫?Android",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )

    parser.add_argument(
        "--weights",
        type=str,
        required=True,
        help="PyTorch 妯″瀷鏉冮噸鏂囦欢璺緞 (.pt)",
    )
    parser.add_argument(
        "--imgsz",
        type=int,
        default=640,
        help="杈撳叆鍥惧儚灏哄 (榛樿: 640)",
    )
    parser.add_argument(
        "--output",
        type=str,
        default="models",
        help="杈撳嚭鐩綍 (榛樿: models)",
    )
    parser.add_argument(
        "--assets",
        type=str,
        default=None,
        help="Android assets 鐩綍璺緞锛堟寚瀹氬垯鑷姩澶嶅埗锛?,
    )
    parser.add_argument(
        "--no-simplify",
        action="store_true",
        help="绂佺敤 ONNX 绠€鍖?,
    )
    parser.add_argument(
        "--opset",
        type=int,
        default=12,
        help="ONNX opset 鐗堟湰 (榛樿: 12)",
    )

    args = parser.parse_args()

    # 楠岃瘉杈撳叆
    if not Path(args.weights).exists():
        print(f"閿欒: 鏉冮噸鏂囦欢涓嶅瓨鍦? {args.weights}")
        sys.exit(1)

    print("=" * 60)
    print("OpenIris Model Export Pipeline")
    print("=" * 60)
    print(f"杈撳叆: {args.weights}")
    print(f"杈撳嚭: {args.output}")
    print(f"灏哄: {args.imgsz}")
    print(f"ONNX opset: {args.opset}")
    print("=" * 60)

    # 鏌ユ壘 NCNN 宸ュ叿
    ncnn_tools = find_ncnn_tools()

    # Step 1-2: 瀵煎嚭 ONNX
    onnx_path = export_onnx(
        args.weights,
        imgsz=args.imgsz,
        simplify=not args.no_simplify,
        opset=args.opset,
    )

    # Step 3-4: 杞崲 NCNN
    param_path, bin_path = convert_ncnn(
        onnx_path,
        args.output,
        ncnn_tools,
    )

    # Step 5: 鐢熸垚 labels.txt
    labels_path = generate_labels(args.weights, args.output)

    # Step 6: 鍚屾鍒?Android assets
    if args.assets:
        copy_to_assets(args.output, args.assets)

    print("\n" + "=" * 60)
    print("瀵煎嚭瀹屾垚!")
    print("=" * 60)

    if param_path and bin_path:
        param_size = Path(param_path).stat().st_size / 1024
        bin_size = Path(bin_path).stat().st_size / (1024 * 1024)
        print(f"  param 鏂囦欢: {param_size:.1f} KB")
        print(f"  bin 鏂囦欢:   {bin_size:.1f} MB")

    if labels_path:
        with open(labels_path, "r") as f:
            nc = len(f.readlines())
        print(f"  绫诲埆鏁?     {nc}")


if __name__ == "__main__":
    main()


