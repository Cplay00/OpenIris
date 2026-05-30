#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Dataset Validator

校验数据集格式、标注质量和目录结构。
在训练前运行，提前发现并修复数据问题。

使用方法:
    python training/scripts/dataset_validator.py --data configs/dataset_custom.yaml

校验内容:
    1. 目录结构完整性
    2. 图像文件有效性
    3. 标注文件格式
    4. 类别索引范围
    5. 边界框坐标范围 (0~1)
    6. 图像-标注文件配对
    7. 空标注文件检测
"""

import argparse
import sys
from pathlib import Path
from collections import Counter

import yaml


# 支持的图像格式
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp", ".tiff", ".tif"}


class ValidationResult:
    """校验结果容器"""

    def __init__(self):
        self.errors = []
        self.warnings = []
        self.info = []

    def error(self, msg: str):
        self.errors.append(f"[错误] {msg}")

    def warn(self, msg: str):
        self.warnings.append(f"[警告] {msg}")

    def add_info(self, msg: str):
        self.info.append(f"[信息] {msg}")

    @property
    def passed(self) -> bool:
        return len(self.errors) == 0

    def print_report(self):
        print("\n" + "=" * 60)
        print("数据集校验报告")
        print("=" * 60)

        if self.info:
            print("\n--- 信息 ---")
            for msg in self.info:
                print(f"  {msg}")

        if self.warnings:
            print("\n--- 警告 ---")
            for msg in self.warnings:
                print(f"  ⚠ {msg}")

        if self.errors:
            print("\n--- 错误 ---")
            for msg in self.errors:
                print(f"  ✗ {msg}")

        print("\n" + "-" * 60)
        if self.passed:
            print("✓ 数据集校验通过！")
            if self.warnings:
                print(f"  （有 {len(self.warnings)} 个警告，建议处理）")
        else:
            print(f"✗ 数据集校验失败！发现 {len(self.errors)} 个错误。")
            print("  请修复上述错误后重新运行。")
        print("=" * 60)


def load_data_config(path: str) -> dict:
    """加载数据集配置"""
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def validate_directory_structure(data_config: dict, result: ValidationResult) -> dict:
    """
    校验目录结构，返回解析后的路径。

    Returns:
        解析后的路径字典
    """
    base = Path(data_config.get("path", "."))
    paths = {}

    for split in ["train", "val", "test"]:
        if split not in data_config:
            continue

        img_dir = base / data_config[split]
        # 标注目录与图像目录同级，名为 labels
        label_dir = img_dir.parent.parent / "labels" / img_dir.name

        paths[split] = {"images": img_dir, "labels": label_dir}

        if not img_dir.exists():
            result.error(f"{split} 图像目录不存在: {img_dir}")
        else:
            img_count = len([f for f in img_dir.iterdir()
                           if f.suffix.lower() in IMAGE_EXTENSIONS])
            result.add_info(f"{split} 图像目录: {img_dir} ({img_count} 张图像)")

        if not label_dir.exists():
            result.error(f"{split} 标注目录不存在: {label_dir}")
        else:
            txt_count = len([f for f in label_dir.iterdir()
                           if f.suffix == ".txt"])
            result.add_info(f"{split} 标注目录: {label_dir} ({txt_count} 个标注)")

    # 类别信息
    nc = data_config.get("nc", 0)
    names = data_config.get("names", {})
    result.add_info(f"类别数量: {nc}")
    if names:
        result.add_info(f"类别名称: {list(names.values())[:5]}{'...' if len(names) > 5 else ''}")

    return paths


def validate_label_format(label_path: Path, nc: int, result: ValidationResult) -> list:
    """
    校验单个标注文件的格式。

    Returns:
        标注行中的类别列表
    """
    classes = []

    try:
        with open(label_path, "r", encoding="utf-8") as f:
            lines = f.readlines()
    except Exception as e:
        result.error(f"无法读取标注文件 {label_path}: {e}")
        return classes

    if len(lines) == 0:
        result.warn(f"空标注文件: {label_path}")
        return classes

    for line_num, line in enumerate(lines, 1):
        line = line.strip()
        if not line:
            continue

        parts = line.split()
        if len(parts) < 5:
            result.error(
                f"{label_path}:{line_num} 格式错误: "
                f"需要至少 5 个值 (class x y w h)，实际 {len(parts)} 个"
            )
            continue

        try:
            class_id = int(parts[0])
            coords = [float(x) for x in parts[1:5]]
        except ValueError as e:
            result.error(f"{label_path}:{line_num} 数值解析错误: {e}")
            continue

        # 类别索引检查
        if class_id < 0 or class_id >= nc:
            result.error(
                f"{label_path}:{line_num} 类别索引 {class_id} 超出范围 [0, {nc-1}]"
            )
        else:
            classes.append(class_id)

        # 坐标范围检查
        x, y, w, h = coords
        if not (0 <= x <= 1 and 0 <= y <= 1):
            result.error(
                f"{label_path}:{line_num} 中心坐标 ({x:.4f}, {y:.4f}) 超出范围 [0, 1]"
            )
        if not (0 < w <= 1 and 0 < h <= 1):
            result.error(
                f"{label_path}:{line_num} 宽高 ({w:.4f}, {h:.4f}) 超出范围 (0, 1]"
            )

        # 边界框完整性检查
        if x - w/2 < -0.01 or x + w/2 > 1.01 or y - h/2 < -0.01 or y + h/2 > 1.01:
            result.warn(
                f"{label_path}:{line_num} 边界框部分超出图像范围"
            )

    return classes


def validate_pairs(paths: dict, nc: int, result: ValidationResult):
    """校验图像-标注文件配对和标注内容"""

    for split, dirs in paths.items():
        img_dir = dirs["images"]
        lbl_dir = dirs["labels"]

        if not img_dir.exists() or not lbl_dir.exists():
            continue

        # 收集文件名（不含扩展名）
        img_stems = {f.stem for f in img_dir.iterdir()
                    if f.suffix.lower() in IMAGE_EXTENSIONS}
        lbl_stems = {f.stem for f in lbl_dir.iterdir() if f.suffix == ".txt"}

        # 无标注的图像
        missing_labels = img_stems - lbl_stems
        if missing_labels:
            samples = list(missing_labels)[:3]
            result.warn(
                f"{split}: {len(missing_labels)} 张图像缺少标注文件"
                f"（示例: {samples}）"
            )

        # 无图像的标注
        missing_images = lbl_stems - img_stems
        if missing_images:
            samples = list(missing_images)[:3]
            result.error(
                f"{split}: {len(missing_images)} 个标注文件缺少对应图像"
                f"（示例: {samples}）"
            )

        # 校验标注内容
        all_classes = []
        for lbl_file in lbl_dir.glob("*.txt"):
            classes = validate_label_format(lbl_file, nc, result)
            all_classes.extend(classes)

        # 类别分布统计
        if all_classes:
            class_counts = Counter(all_classes)
            total = len(all_classes)
            result.add_info(
                f"{split} 标注总数: {total}，"
                f"覆盖类别: {len(class_counts)}/{nc}"
            )

            # 检查类别不平衡
            if class_counts:
                max_count = max(class_counts.values())
                min_count = min(class_counts.values())
                if max_count > min_count * 10:
                    result.warn(
                        f"{split} 类别严重不平衡: "
                        f"最多 {max_count} 个，最少 {min_count} 个"
                    )


def main():
    parser = argparse.ArgumentParser(
        description="OpenIris Dataset Validator",
    )
    parser.add_argument(
        "--data",
        type=str,
        required=True,
        help="数据集配置文件路径 (YAML)",
    )

    args = parser.parse_args()

    # 加载配置
    try:
        data_config = load_data_config(args.data)
    except Exception as e:
        print(f"错误: 无法加载配置文件 {args.data}: {e}")
        sys.exit(1)

    result = ValidationResult()

    # 1. 目录结构校验
    print("正在校验目录结构...")
    paths = validate_directory_structure(data_config, result)

    # 2. 标注内容校验
    nc = data_config.get("nc", 0)
    if nc > 0:
        print("正在校验标注内容...")
        validate_pairs(paths, nc, result)
    else:
        result.error("配置文件中未指定类别数量 (nc)")

    # 输出报告
    result.print_report()

    # 返回码
    sys.exit(0 if result.passed else 1)


if __name__ == "__main__":
    main()
