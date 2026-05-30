#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Dataset Splitter

数据集划分工具，支持:
- 训练/验证/测试集划分
- 分层划分（保持类别比例）
- 按目录结构自动划分
"""

import random
import shutil
from pathlib import Path
from typing import List, Tuple, Optional
from collections import defaultdict


class DatasetSplitter:
    """数据集划分器"""

    @staticmethod
    def split_dataset(
        image_dir: str,
        label_dir: str,
        output_dir: str,
        train_ratio: float = 0.8,
        val_ratio: float = 0.15,
        test_ratio: float = 0.05,
        seed: int = 42,
        stratify: bool = True
    ) -> dict:
        """
        划分数据集为训练/验证/测试集。

        Args:
            image_dir: 图像目录
            label_dir: 标注目录
            output_dir: 输出目录
            train_ratio: 训练集比例
            val_ratio: 验证集比例
            test_ratio: 测试集比例
            seed: 随机种子
            stratify: 是否分层划分（保持类别比例）

        Returns:
            划分统计信息
        """
        image_dir = Path(image_dir)
        label_dir = Path(label_dir)
        output_dir = Path(output_dir)

        # 验证比例
        total_ratio = train_ratio + val_ratio + test_ratio
        if abs(total_ratio - 1.0) > 0.001:
            raise ValueError(f"比例之和必须为 1.0，当前: {total_ratio}")

        # 收集配对的图像和标注
        image_exts = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
        pairs = []

        for img_file in image_dir.iterdir():
            if img_file.suffix.lower() in image_exts:
                label_file = label_dir / f"{img_file.stem}.txt"
                if label_file.exists():
                    pairs.append((img_file, label_file))

        if not pairs:
            raise ValueError(f"未找到有效的图像-标注对: {image_dir}, {label_dir}")

        # 分层划分
        if stratify:
            # 读取每个标注文件的主要类别
            file_classes = DatasetSplitter._get_file_classes(pairs)
            splits = DatasetSplitter._stratified_split(
                pairs, file_classes, train_ratio, val_ratio, seed
            )
        else:
            random.seed(seed)
            random.shuffle(pairs)
            n_train = int(len(pairs) * train_ratio)
            n_val = int(len(pairs) * val_ratio)
            splits = {
                "train": pairs[:n_train],
                "val": pairs[n_train:n_train + n_val],
                "test": pairs[n_train + n_val:]
            }

        # 创建输出目录并复制文件
        stats = {}
        for split_name, split_pairs in splits.items():
            if not split_pairs:
                continue

            img_out = output_dir / "images" / split_name
            lbl_out = output_dir / "labels" / split_name
            img_out.mkdir(parents=True, exist_ok=True)
            lbl_out.mkdir(parents=True, exist_ok=True)

            for img_file, label_file in split_pairs:
                shutil.copy2(img_file, img_out / img_file.name)
                shutil.copy2(label_file, lbl_out / label_file.name)

            stats[split_name] = len(split_pairs)

        return stats

    @staticmethod
    def _get_file_classes(pairs: List[Tuple[Path, Path]]) -> dict:
        """获取每个文件的主要类别"""
        file_classes = {}
        for img_file, label_file in pairs:
            classes = []
            with open(label_file, "r") as f:
                for line in f:
                    parts = line.strip().split()
                    if parts:
                        classes.append(int(parts[0]))
            # 使用出现最多的类别作为主要类别
            if classes:
                file_classes[img_file.stem] = max(set(classes), key=classes.count)
            else:
                file_classes[img_file.stem] = -1
        return file_classes

    @staticmethod
    def _stratified_split(
        pairs: List[Tuple[Path, Path]],
        file_classes: dict,
        train_ratio: float,
        val_ratio: float,
        seed: int
    ) -> dict:
        """分层划分，保持类别比例"""
        random.seed(seed)

        # 按类别分组
        class_files = defaultdict(list)
        for img_file, label_file in pairs:
            cls = file_classes[img_file.stem]
            class_files[cls].append((img_file, label_file))

        splits = {"train": [], "val": [], "test": []}

        for cls, cls_pairs in class_files.items():
            random.shuffle(cls_pairs)
            n = len(cls_pairs)
            n_train = max(1, int(n * train_ratio))
            n_val = max(1, int(n * val_ratio))

            splits["train"].extend(cls_pairs[:n_train])
            splits["val"].extend(cls_pairs[n_train:n_train + n_val])
            splits["test"].extend(cls_pairs[n_train + n_val:])

        # 打乱每个 split
        for split_name in splits:
            random.shuffle(splits[split_name])

        return splits

    @staticmethod
    def merge_datasets(
        dataset_dirs: List[str],
        output_dir: str
    ) -> dict:
        """
        合并多个数据集。

        Args:
            dataset_dirs: 数据集目录列表
            output_dir: 输出目录

        Returns:
            合并统计信息
        """
        output_dir = Path(output_dir)
        stats = {"datasets": len(dataset_dirs), "images": 0, "labels": 0}

        for dataset_dir in dataset_dirs:
            dataset_dir = Path(dataset_dir)

            for split in ["train", "val", "test"]:
                img_src = dataset_dir / "images" / split
                lbl_src = dataset_dir / "labels" / split

                if not img_src.exists():
                    continue

                img_dst = output_dir / "images" / split
                lbl_dst = output_dir / "labels" / split
                img_dst.mkdir(parents=True, exist_ok=True)
                lbl_dst.mkdir(parents=True, exist_ok=True)

                for img_file in img_src.iterdir():
                    if img_file.suffix.lower() in {".jpg", ".jpeg", ".png", ".bmp", ".webp"}:
                        # 添加数据集前缀避免重名
                        new_name = f"{dataset_dir.name}_{img_file.name}"
                        shutil.copy2(img_file, img_dst / new_name)
                        stats["images"] += 1

                        # 复制对应标注
                        lbl_file = lbl_src / f"{img_file.stem}.txt"
                        if lbl_file.exists():
                            new_lbl_name = f"{dataset_dir.name}_{img_file.stem}.txt"
                            shutil.copy2(lbl_file, lbl_dst / new_lbl_name)
                            stats["labels"] += 1

        return stats
