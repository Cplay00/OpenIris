#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
YOLOv11 .pt -> ONNX -> NCNN 转换脚本

使用方法:
    python tools/export_yolov11_ncnn.py --weights path/to/yolov11n.pt --imgsz 640

依赖:
    - ultralytics
    - onnx
    - onnxsim (可选，用于简化 ONNX)
    - ncnn 转换工具 (ncnnoptimize, onnx2ncnn)

输出:
    - models/yolov11n.onnx
    - models/yolov11n.ncnn.param
    - models/yolov11n.ncnn.bin
"""

import argparse
import os
import subprocess
import sys
from pathlib import Path


def export_onnx(weights_path: str, imgsz: int = 640, simplify: bool = True):
    """
    使用 ultralytics 导出 ONNX 模型
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        print("错误: 请安装 ultralytics: pip install ultralytics")
        sys.exit(1)

    print(f"[1/4] 加载模型: {weights_path}")
    model = YOLO(weights_path)

    print(f"[2/4] 导出 ONNX (imgsz={imgsz})...")
    model.export(
        format="onnx",
        imgsz=imgsz,
        simplify=simplify,
        opset=12,
    )

    # 获取导出路径
    onnx_path = Path(weights_path).with_suffix(".onnx")
    if not onnx_path.exists():
        # ultralytics 可能会在文件名中添加 _ncnn 或其他后缀
        possible_paths = list(Path(weights_path).parent.glob("*.onnx"))
        if possible_paths:
            onnx_path = possible_paths[0]

    print(f"[2/4] ONNX 导出完成: {onnx_path}")
    return str(onnx_path)


def simplify_onnx(onnx_path: str):
    """
    使用 onnxsim 简化 ONNX 模型
    """
    try:
        import onnx
        from onnxsim import simplify as onnx_simplify
    except ImportError:
        print("警告: 未安装 onnxsim，跳过简化步骤")
        return onnx_path

    print(f"[3/4] 简化 ONNX 模型...")
    model = onnx.load(onnx_path)
    model_simp, check = onnx_simplify(model)

    if check:
        simplified_path = onnx_path.replace(".onnx", "_sim.onnx")
        onnx.save(model_simp, simplified_path)
        print(f"[3/4] ONNX 简化完成: {simplified_path}")
        return simplified_path
    else:
        print("警告: ONNX 简化失败，使用原始模型")
        return onnx_path


def convert_ncnn(onnx_path: str, output_dir: str):
    """
    使用 onnx2ncnn 将 ONNX 转换为 NCNN 格式
    """
    onnx2ncnn = os.environ.get("ONNX2NCNN", "onnx2ncnn")
    ncnnoptimize = os.environ.get("NCNNOPTIMIZE", "ncnnoptimize")

    # 检查工具是否存在
    if not shutil.which(onnx2ncnn):
        print(f"错误: 找不到 onnx2ncnn 工具")
        print("请下载 NCNN 工具包并添加到 PATH，或设置 ONNX2NCNN 环境变量")
        print("下载地址: https://github.com/Tencent/ncnn/releases")
        sys.exit(1)

    base_name = Path(onnx_path).stem
    param_path = os.path.join(output_dir, f"{base_name}.ncnn.param")
    bin_path = os.path.join(output_dir, f"{base_name}.ncnn.bin")

    print(f"[4/4] 转换 NCNN 模型...")

    # 第一步: onnx -> ncnn
    cmd = [onnx2ncnn, onnx_path, param_path, bin_path]
    result = subprocess.run(cmd, capture_output=True, text=True)

    if result.returncode != 0:
        print(f"错误: ONNX 转 NCNN 失败")
        print(result.stderr)
        sys.exit(1)

    print(f"[4/4] NCNN 模型转换完成:")
    print(f"  - Param: {param_path}")
    print(f"  - Bin: {bin_path}")

    # 第二步: FP16 优化 (可选)
    if shutil.which(ncnnoptimize):
        print(f"[4/4] 应用 FP16 优化...")
        opt_param = os.path.join(output_dir, f"{base_name}.ncnn.opt.param")
        opt_bin = os.path.join(output_dir, f"{base_name}.ncnn.opt.bin")

        cmd = [ncnnoptimize, param_path, bin_path, opt_param, opt_bin, "65536"]
        result = subprocess.run(cmd, capture_output=True, text=True)

        if result.returncode == 0:
            print(f"[4/4] FP16 优化完成:")
            print(f"  - Opt Param: {opt_param}")
            print(f"  - Opt Bin: {opt_bin}")
            # 使用优化后的模型
            param_path = opt_param
            bin_path = opt_bin
        else:
            print("警告: FP16 优化失败，使用未优化模型")

    return param_path, bin_path


def copy_to_assets(param_path: str, bin_path: str, assets_dir: str):
    """
    将 NCNN 模型复制到 Android assets 目录
    """
    import shutil

    model_name = Path(param_path).stem.replace(".ncnn", "").replace(".opt", "")
    target_dir = os.path.join(assets_dir, f"{model_name}_ncnn_model")

    os.makedirs(target_dir, exist_ok=True)

    # 复制 param 和 bin
    shutil.copy2(param_path, os.path.join(target_dir, f"{model_name}.ncnn.param"))
    shutil.copy2(bin_path, os.path.join(target_dir, f"{model_name}.ncnn.bin"))

    print(f"[5/4] 模型已复制到 assets:")
    print(f"  - {target_dir}")

    return target_dir


def main():
    parser = argparse.ArgumentParser(description="YOLOv11 .pt -> ONNX -> NCNN 转换工具")
    parser.add_argument("--weights", type=str, required=True, help="YOLOv11 .pt 权重文件路径")
    parser.add_argument("--imgsz", type=int, default=640, help="输入图像尺寸 (默认: 640)")
    parser.add_argument("--simplify", action="store_true", default=True, help="简化 ONNX 模型")
    parser.add_argument("--output", type=str, default="models", help="输出目录 (默认: models)")
    parser.add_argument("--assets", type=str, default=None, help="Android assets 目录路径，指定则自动复制")
    parser.add_argument("--no-fp16", action="store_true", help="禁用 FP16 优化")

    args = parser.parse_args()

    # 创建输出目录
    os.makedirs(args.output, exist_ok=True)

    # 步骤 1: 导出 ONNX
    onnx_path = export_onnx(args.weights, args.imgsz, args.simplify)

    # 步骤 2: 简化 ONNX (可选)
    if args.simplify:
        onnx_path = simplify_onnx(onnx_path)

    # 步骤 3: 转换 NCNN
    param_path, bin_path = convert_ncnn(onnx_path, args.output)

    # 步骤 4: 复制到 assets (可选)
    if args.assets:
        copy_to_assets(param_path, bin_path, args.assets)

    print("\n✅ 转换流程完成!")
    print(f"模型文件位置: {args.output}")


if __name__ == "__main__":
    import shutil
    main()
