#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Export Pipeline

模型导出流水线: .pt -> ONNX -> NCNN -> Android Assets
自动处理格式转换和优化，确保模型在 Android 设备上高效运行。

APP 端接口说明
===========
NCNN 模型 zip 包结构:
  model.param  - NCNN 模型结构定义
  model.bin    - NCNN 模型权重数据
  labels.txt   - 类别标签文件 (每行一个类别名)

APP 端加载方式:
  1. 解压 zip 到应用私有目录
  2. 读取 labels.txt 获取类别列表
  3. 使用 ncnn 加载 model.param + model.bin
  4. 输入图像预处理: resize 到 640x640, BGR->RGB, /255.0
  5. 输出: out0 [4+nc, 8400] (ultralytics 已解码格式)
     - channel 0: cx (中心 x，模型输入空间)
     - channel 1: cy (中心 y，模型输入空间)
     - channel 2: w (宽度)
     - channel 3: h (高度)
     - channel 4..4+nc-1: sigmoid 后的分类概率
     注: 后处理(DFL解码+锚点+sigmoid)已在模型图内完成

使用方法:
    # 基本导出 (NCNN 格式，默认)
    python training/tools/export_pipeline.py \\
        --weights training/run/openiris_v1/weights/best.pt

    # 导出并同步到 Android assets
    python training/tools/export_pipeline.py \\
        --weights training/run/openiris_v1/weights/best.pt \\
        --assets buildapk/app/src/main/assets/models/my_model

    # 指定输出目录
    python training/tools/export_pipeline.py \\
        --weights training/run/openiris_v1/weights/best.pt \\
        --output models/my_model

    # 仅导出 ONNX (不转 NCNN)
    python training/tools/export_pipeline.py \\
        --weights training/run/openiris_v1/weights/best.pt \\
        --onnx-only

    # 导出并打包为 zip (默认启用)
    python training/tools/export_pipeline.py \\
        --weights training/run/openiris_v1/weights/best.pt \\
        --zip

导出流程:
    1. 加载 PyTorch 模型 (.pt)
    2. 导出 ONNX 模型
    3. 简化 ONNX 模型 (可选)
    4. 转换为 NCNN 模型 (.param + .bin) (使用 ultralytics 内置导出)
    5. 生成 labels.txt
    6. 验证导出模型 (可选)
    7. 复制到 Android assets (可选)
    8. 打包为 zip (可选)
"""

import argparse
import os
import shutil
import zipfile
import json
import sys
from typing import Optional
from pathlib import Path


def export_ncnn(weights_path: str, imgsz: int = 640) -> tuple:
    """
    使用 ultralytics 内置功能直接导出 NCNN 格式。

    Args:
        weights_path: PyTorch 权重文件路径
        imgsz: 输入图像尺寸

    Returns:
        (param_path, bin_path) 元组，失败返回 (None, None)
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        print("错误: 未安装 ultralytics。请运行: pip install ultralytics")
        return None, None

    print(f"[1/4] 加载模型: {weights_path}")
    model = YOLO(weights_path)

    print(f"[2/4] 导出 NCNN 格式 (imgsz={imgsz})...")
    try:
        export_dir = model.export(format="ncnn", imgsz=imgsz)
        # ultralytics 导出 NCNN 会创建一个目录
        export_dir = Path(export_dir)
        if export_dir.is_dir():
            param_path = export_dir / "model.ncnn.param"
            bin_path = export_dir / "model.ncnn.bin"
        else:
            # 可能直接导出到权重文件旁边
            parent = Path(weights_path).parent
            param_path = parent / f"{Path(weights_path).stem}.ncnn.param"
            bin_path = parent / f"{Path(weights_path).stem}.ncnn.bin"

        if param_path.exists() and bin_path.exists():
            print(f"    NCNN 导出完成:")
            print(f"    - {param_path}")
            print(f"    - {bin_path}")
            return str(param_path), str(bin_path)
        else:
            # 尝试在导出目录中查找
            param_files = list(export_dir.rglob("*.param"))
            bin_files = list(export_dir.rglob("*.bin"))
            if param_files and bin_files:
                print(f"    NCNN 导出完成:")
                print(f"    - {param_files[0]}")
                print(f"    - {bin_files[0]}")
                return str(param_files[0]), str(bin_files[0])

        print("错误: NCNN 导出失败，未找到输出文件")
        return None, None
    except Exception as e:
        print(f"错误: NCNN 导出失败: {e}")
        return None, None


def export_onnx_only(weights_path: str, imgsz: int = 640,
                     simplify: bool = True, opset: int = 12) -> str:
    """
    仅导出 ONNX 模型（不转 NCNN）。

    Args:
        weights_path: PyTorch 权重文件路径
        imgsz: 输入图像尺寸
        simplify: 是否简化 ONNX 模型
        opset: ONNX opset 版本

    Returns:
        ONNX 文件路径，失败返回 None
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        print("错误: 未安装 ultralytics。请运行: pip install ultralytics")
        return None

    print(f"[1/2] 加载模型: {weights_path}")
    model = YOLO(weights_path)

    print(f"[2/2] 导出 ONNX (imgsz={imgsz}, opset={opset})...")
    model.export(format="onnx", imgsz=imgsz, simplify=simplify, opset=opset)

    onnx_path = Path(weights_path).with_suffix(".onnx")
    if onnx_path.exists():
        print(f"    ONNX 导出完成: {onnx_path}")
        return str(onnx_path)

    # ultralytics 有时导出到不同位置
    alt_path = Path(weights_path).parent / f"{Path(weights_path).stem}.onnx"
    if alt_path.exists():
        print(f"    ONNX 导出完成: {alt_path}")
        return str(alt_path)

    print("错误: ONNX 文件未找到")
    return None


def generate_labels(weights_path: str, output_dir: str) -> str:
    """
    从训练结果生成 labels.txt。

    Args:
        weights_path: 模型权重文件路径
        output_dir: 输出目录

    Returns:
        labels.txt 文件路径，失败返回 None
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        print("警告: 无法生成 labels.txt (ultralytics 未安装)")
        return None

    model = YOLO(weights_path)
    names = model.names

    os.makedirs(output_dir, exist_ok=True)
    labels_path = os.path.join(output_dir, "labels.txt")
    with open(labels_path, "w", encoding="utf-8") as f:
        for i in range(len(names)):
            name = names.get(i, f"class_{i}")
            f.write(f"{name}\n")

    print(f"[3/4] 生成 labels.txt: {labels_path}")
    print(f"    类别数: {len(names)}")
    return labels_path


def generate_model_meta(model, param_path: str, output_dir: str,
                        reg_max: int = 16) -> Optional[str]:
    """生成模型格式元数据文件 model_meta.json。

    ultralytics NCNN 导出的实际输出格式:
      out0: [4+nc, 8400] (已解码坐标 + sigmoid 概率)
      - channel 0: cx (中心 x，模型输入空间)
      - channel 1: cy (中心 y，模型输入空间)
      - channel 2: w (宽度)
      - channel 3: h (高度)
      - channel 4..4+nc-1: sigmoid 后的分类概率
      注: 后处理(DFL解码+锚点+sigmoid)已在模型图内完成
    """
    try:
        names = model.names
        nc = len(names)
        input_size = 640  # ultralytics 默认
        if hasattr(model, 'model') and hasattr(model.model, 'args'):
            input_size = getattr(model.model.args, 'imgsz', 640)
            if isinstance(input_size, list):
                input_size = input_size[0]

        meta = {
            "format": "ultralytics_decoded",
            "nc": nc,
            "names": [names[i] for i in range(nc)],
            "input_size": input_size,
            "reg_max": reg_max,
            "output_names": ["out0"],
            "output_channels": 4 + nc,
            "description": "ultralytics NCNN export with embedded post-processing",
        }

        meta_path = str(Path(output_dir) / "model_meta.json")
        with open(meta_path, "w", encoding="utf-8") as f:
            json.dump(meta, f, indent=2, ensure_ascii=False)
        print(f"  元数据: {meta_path}")
        return meta_path
    except Exception as e:
        print(f"[Warning] 生成元数据失败: {e}")
        return None


def copy_to_assets(output_dir: str, assets_dir: str):
    """
    将导出文件复制到 Android assets 目录。

    Args:
        output_dir: 导出输出目录
        assets_dir: Android assets 目标目录
    """
    assets_path = Path(assets_dir)
    assets_path.mkdir(parents=True, exist_ok=True)

    # 复制 NCNN 文件
    output_path = Path(output_dir)
    for ext in [".param", ".bin"]:
        for f in output_path.rglob(f"*ncnn{ext}"):
            target = assets_path / f"model{ext}"
            shutil.copy2(f, target)
            print(f"    复制: {f.name} -> {target}")

    # 复制 labels.txt
    labels_src = output_path / "labels.txt"
    if not labels_src.exists():
        labels_src = output_path.parent / "labels.txt"
    if labels_src.exists():
        target = assets_path / "labels.txt"
        shutil.copy2(labels_src, target)
        print(f"    复制: labels.txt -> {target}")

    print(f"    Android assets 已更新: {assets_dir}")


def create_zip(param_path: str, bin_path: str, labels_path: str,
               weights_path: str, output_dir: str,
               meta_path: Optional[str] = None) -> Path:
    """
    将 NCNN 模型文件打包为 zip (含 model_meta.json 如存在)。

    Args:
        param_path: .param 文件路径
        bin_path: .bin 文件路径
        labels_path: labels.txt 文件路径
        weights_path: 原始权重文件路径（用于命名）
        output_dir: 输出目录

    Returns:
        zip 文件路径
    """
    model_name = Path(weights_path).stem
    zip_name = f"{model_name}_ncnn.zip"
    zip_path = Path(output_dir) / zip_name

    print(f"[4/4] 打包 zip: {zip_path}")
    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        zf.write(str(param_path), "model.param")
        zf.write(str(bin_path), "model.bin")
        if labels_path and Path(labels_path).exists():
            zf.write(str(labels_path), "labels.txt")
        if meta_path and Path(meta_path).exists():
            zf.write(str(meta_path), "model_meta.json")

    zip_size = zip_path.stat().st_size / (1024 * 1024)
    print(f"    zip 打包完成: {zip_path} ({zip_size:.1f} MB)")
    return zip_path


def main():
    parser = argparse.ArgumentParser(
        description="OpenIris Model Export Pipeline",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
导出格式:
  ncnn    NCNN 格式 (.param + .bin)，用于移动端部署 (默认)
  onnx    ONNX 格式，用于 Python 推理或其他推理框架

示例:
  %(prog)s --weights best.pt
  %(prog)s --weights best.pt --format onnx
  %(prog)s --weights best.pt --assets ../app/src/main/assets/models/pig
        """
    )
    parser.add_argument("--weights", type=str, required=True,
                        help="PyTorch 权重文件路径 (.pt)")
    parser.add_argument("--output", type=str, default=None,
                        help="输出目录 (默认: 权重文件所在目录)")
    parser.add_argument("--assets", type=str, default=None,
                        help="Android assets 目标目录 (可选)")
    parser.add_argument("--imgsz", type=int, default=640,
                        help="输入图像尺寸 (默认: 640)")
    parser.add_argument("--format", type=str, default="ncnn",
                        choices=["ncnn", "onnx"],
                        help="导出格式: ncnn 或 onnx (默认: ncnn)")
    parser.add_argument("--no-zip", action="store_true",
                        help="不打包为 zip 文件")
    parser.add_argument("--opset", type=int, default=12,
                        help="ONNX opset 版本 (默认: 12)")
    parser.add_argument("--reg-max", type=int, default=16,
                        help="DFL reg_max 参数 (默认: 16, 仅用于元数据生成)")

    args = parser.parse_args()

    # 默认输出目录: 权重文件所在目录
    if args.output is None:
        args.output = str(Path(args.weights).parent)

    # 验证输入
    if not Path(args.weights).exists():
        print(f"错误: 权重文件不存在: {args.weights}")
        sys.exit(1)

    print("=" * 60)
    print("OpenIris Model Export Pipeline")
    print("=" * 60)
    print(f"输入: {args.weights}")
    print(f"输出: {args.output}")
    print(f"格式: {args.format.upper()}")
    print(f"尺寸: {args.imgsz}")
    if args.format == "onnx":
        print(f"ONNX opset: {args.opset}")
    print("=" * 60)

    # Step 1-2: 导出模型
    if args.format == "ncnn":
        # 使用 ultralytics 内置 NCNN 导出
        param_path, bin_path = export_ncnn(args.weights, imgsz=args.imgsz)
        if not param_path or not bin_path:
            print("\nNCNN 导出失败，尝试回退到 ONNX 导出...")
            onnx_path = export_onnx_only(args.weights, imgsz=args.imgsz, opset=args.opset)
            if onnx_path:
                print("\n提示: 可以使用以下命令手动转换 NCNN:")
                print(f"  yolo export model={args.weights} format=ncnn")
            sys.exit(1)
    else:
        # 仅导出 ONNX
        onnx_path = export_onnx_only(args.weights, imgsz=args.imgsz, opset=args.opset)
        if not onnx_path:
            sys.exit(1)
        param_path, bin_path = None, None

    # Step 3: 生成 labels.txt
    labels_path = generate_labels(args.weights, args.output)

    # Step 3.5: 生成 model_meta.json (NCNN 格式元数据)
    meta_path = None
    if args.format == "ncnn" and param_path:
        # export_ncnn 已加载过模型，此处需要重新加载获取元信息
        try:
            from ultralytics import YOLO
            _model = YOLO(args.weights)
            meta_path = generate_model_meta(
                _model, param_path, args.output, reg_max=args.reg_max
            )
            del _model
        except Exception as e:
            print(f"[Warning] 无法生成元数据: {e}")

    # Step 4: 复制 NCNN 文件到输出目录
    os.makedirs(args.output, exist_ok=True)
    if param_path and bin_path:
        # 复制到输出目录
        dst_param = Path(args.output) / "model.param"
        dst_bin = Path(args.output) / "model.bin"
        shutil.copy2(param_path, dst_param)
        shutil.copy2(bin_path, dst_bin)
        param_path = str(dst_param)
        bin_path = str(dst_bin)
        print(f"    复制到输出目录: {args.output}")

    # Step 5: 打包 zip
    zip_path = None
    if args.format == "ncnn" and not args.no_zip and param_path and bin_path:
        zip_path = create_zip(param_path, bin_path, labels_path,
                              args.weights, args.output, meta_path)

    # Step 6: 复制到 Android assets
    if args.assets:
        copy_to_assets(args.output, args.assets)

    # 输出结果
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
        print(f"  类别数:      {nc}")

    if zip_path and zip_path.exists():
        zip_size = zip_path.stat().st_size / (1024 * 1024)
        print(f"  zip 文件:    {zip_path} ({zip_size:.1f} MB)")
        zip_contents = "model.param + model.bin + labels.txt"
        if meta_path:
            zip_contents += " + model_meta.json"
        print(f"  zip 内容:    {zip_contents}")
        print(f"\n  APP 端可直接导入此 zip 文件使用。")

    # 提示用户注意文件命名
    print(f"\n  提示: 所有导出文件已保存到: {args.output}")
    print(f"  请注意为输出文件夹取一个有意义的名称（如模型名称），避免覆盖。")


if __name__ == "__main__":
    main()
