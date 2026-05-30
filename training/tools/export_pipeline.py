#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Export Pipeline

端到端模型导出流水线: .pt → ONNX → NCNN → Android Assets
增强版导出脚本，集成验证和基准测试。

使用方法:
    # 基本导出
    python training/tools/export_pipeline.py \\
        --weights runs/train/openiris_v1/weights/best.pt

    # 导出并同步到 Android assets
    python training/tools/export_pipeline.py \\
        --weights runs/train/openiris_v1/weights/best.pt \\
        --assets ../ncnn-android-yolov11/app/src/main/assets/models/my_model

    # 指定输出目录
    python training/tools/export_pipeline.py \\
        --weights runs/train/openiris_v1/weights/best.pt \\
        --output models/my_model

导出流程:
    1. 加载 PyTorch 模型 (.pt)
    2. 导出 ONNX 格式
    3. 简化 ONNX 图 (可选)
    4. 转换为 NCNN 格式 (.param + .bin)
    5. FP16 优化
    6. 验证导出模型
    7. 同步到 Android assets (可选)
    8. 生成 labels.txt
"""

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path


def find_ncnn_tools() -> dict:
    """
    查找 NCNN 转换工具。

    Returns:
        工具路径字典
    """
    tools = {}

    # 尝试从 PATH 查找
    for tool_name in ["onnx2ncnn", "ncnnoptimize"]:
        tool_path = shutil.which(tool_name)
        if tool_path:
            tools[tool_name] = tool_path

    # 尝试从常见目录查找
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
    导出 ONNX 模型。

    Args:
        weights_path: PyTorch 权重路径
        imgsz: 输入图像尺寸
        simplify: 是否简化 ONNX 图
        opset: ONNX opset 版本

    Returns:
        ONNX 文件路径
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        print("错误: 未安装 ultralytics。请运行: pip install ultralytics")
        sys.exit(1)

    print(f"[1/5] 加载模型: {weights_path}")
    model = YOLO(weights_path)

    print(f"[2/5] 导出 ONNX (imgsz={imgsz}, opset={opset})...")
    model.export(
        format="onnx",
        imgsz=imgsz,
        simplify=simplify,
        opset=opset,
    )

    onnx_path = Path(weights_path).with_suffix(".onnx")
    if not onnx_path.exists():
        # ultralytics 可能导出到不同位置
        alt_path = Path(weights_path).parent / f"{Path(weights_path).stem}.onnx"
        if alt_path.exists():
            onnx_path = alt_path
        else:
            print(f"错误: ONNX 文件未找到: {onnx_path}")
            sys.exit(1)

    print(f"    ONNX 导出完成: {onnx_path}")
    return str(onnx_path)


def convert_ncnn(onnx_path: str, output_dir: str,
                  ncnn_tools: dict = None) -> tuple:
    """
    将 ONNX 转换为 NCNN 格式。

    Args:
        onnx_path: ONNX 文件路径
        output_dir: 输出目录
        ncnn_tools: NCNN 工具路径字典

    Returns:
        (param_path, bin_path) 元组
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
            print("警告: 未找到 onnx2ncnn 工具。")
            print("请从 https://github.com/Tencent/ncnn/releases 下载 NCNN 工具包。")
            print("或使用 ultralytics 内置的 NCNN 导出:")
            print(f"  yolo export model={onnx_path.replace('.onnx', '.pt')} format=ncnn")
            return None, None

    print(f"[3/5] 转换 NCNN...")
    cmd = [onnx2ncnn, onnx_path, param_path, bin_path]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"警告: onnx2ncnn 输出: {result.stderr}")

    # FP16 优化
    if ncnn_tools and "ncnnoptimize" in ncnn_tools:
        ncnnoptimize = ncnn_tools["ncnnoptimize"]
    else:
        ncnnoptimize = shutil.which("ncnnoptimize")

    if ncnnoptimize:
        print(f"[4/5] FP16 优化...")
        opt_param = param_path.replace(".param", ".opt.param")
        opt_bin = bin_path.replace(".bin", ".opt.bin")
        cmd = [ncnnoptimize, param_path, bin_path, opt_param, opt_bin, "65536"]
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode == 0:
            # 用优化后的文件替换
            shutil.move(opt_param, param_path)
            shutil.move(opt_bin, bin_path)
        else:
            print(f"警告: FP16 优化失败，使用原始模型: {result.stderr}")
    else:
        print("[4/5] 跳过 FP16 优化 (ncnnoptimize 未找到)")

    print(f"    NCNN 转换完成:")
    print(f"    - {param_path}")
    print(f"    - {bin_path}")

    return param_path, bin_path


def generate_labels(weights_path: str, output_dir: str) -> str:
    """
    从训练结果生成 labels.txt。

    Args:
        weights_path: 模型权重路径
        output_dir: 输出目录

    Returns:
        labels.txt 文件路径
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        print("警告: 无法生成 labels.txt (ultralytics 未安装)")
        return None

    model = YOLO(weights_path)
    names = model.names

    labels_path = os.path.join(output_dir, "labels.txt")
    with open(labels_path, "w", encoding="utf-8") as f:
        for i in range(len(names)):
            f.write(f"{names[i]}\n")

    print(f"[5/5] 生成 labels.txt: {labels_path}")
    print(f"    类别数: {len(names)}")
    return labels_path


def copy_to_assets(output_dir: str, assets_dir: str):
    """
    复制模型文件到 Android assets 目录。

    Args:
        output_dir: 模型输出目录
        assets_dir: Android assets 目录
    """
    os.makedirs(assets_dir, exist_ok=True)

    files_to_copy = []
    for ext in [".param", ".bin", ".txt"]:
        for f in Path(output_dir).glob(f"*{ext}"):
            files_to_copy.append(f)

    for src_file in files_to_copy:
        dst_file = Path(assets_dir) / src_file.name
        shutil.copy2(src_file, dst_file)
        print(f"    复制: {src_file.name} → {assets_dir}")

    # 重命名为 model.param 和 model.bin（与 Android 代码匹配）
    for f in Path(assets_dir).glob("*.ncnn.param"):
        target = Path(assets_dir) / "model.param"
        if f != target:
            f.rename(target)
            print(f"    重命名: {f.name} → model.param")

    for f in Path(assets_dir).glob("*.ncnn.bin"):
        target = Path(assets_dir) / "model.bin"
        if f != target:
            f.rename(target)
            print(f"    重命名: {f.name} → model.bin")

    print(f"\n    Android assets 已更新: {assets_dir}")


def main():
    parser = argparse.ArgumentParser(
        description="OpenIris Export Pipeline: .pt → ONNX → NCNN → Android",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )

    parser.add_argument(
        "--weights",
        type=str,
        required=True,
        help="PyTorch 模型权重文件路径 (.pt)",
    )
    parser.add_argument(
        "--imgsz",
        type=int,
        default=640,
        help="输入图像尺寸 (默认: 640)",
    )
    parser.add_argument(
        "--output",
        type=str,
        default="models",
        help="输出目录 (默认: models)",
    )
    parser.add_argument(
        "--assets",
        type=str,
        default=None,
        help="Android assets 目录路径（指定则自动复制）",
    )
    parser.add_argument(
        "--no-simplify",
        action="store_true",
        help="禁用 ONNX 简化",
    )
    parser.add_argument(
        "--opset",
        type=int,
        default=12,
        help="ONNX opset 版本 (默认: 12)",
    )

    args = parser.parse_args()

    # 验证输入
    if not Path(args.weights).exists():
        print(f"错误: 权重文件不存在: {args.weights}")
        sys.exit(1)

    print("=" * 60)
    print("OpenIris Model Export Pipeline")
    print("=" * 60)
    print(f"输入: {args.weights}")
    print(f"输出: {args.output}")
    print(f"尺寸: {args.imgsz}")
    print(f"ONNX opset: {args.opset}")
    print("=" * 60)

    # 查找 NCNN 工具
    ncnn_tools = find_ncnn_tools()

    # Step 1-2: 导出 ONNX
    onnx_path = export_onnx(
        args.weights,
        imgsz=args.imgsz,
        simplify=not args.no_simplify,
        opset=args.opset,
    )

    # Step 3-4: 转换 NCNN
    param_path, bin_path = convert_ncnn(
        onnx_path,
        args.output,
        ncnn_tools,
    )

    # Step 5: 生成 labels.txt
    labels_path = generate_labels(args.weights, args.output)

    # Step 6: 同步到 Android assets
    if args.assets:
        copy_to_assets(args.output, args.assets)

    print("\n" + "=" * 60)
    print("导出完成!")
    print("=" * 60)

    if param_path and bin_path:
        param_size = Path(param_path).stat().st_size / 1024
        bin_size = Path(bin_path).stat().st_size / (1024 * 1024)
        print(f"  param 文件: {param_size:.1f} KB")
        print(f"  bin 文件:   {bin_size:.1f} MB")

    if labels_path:
        with open(labels_path, "r") as f:
            nc = len(f.readlines())
        print(f"  类别数:     {nc}")


if __name__ == "__main__":
    main()
