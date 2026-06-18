#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Dataset Splitter

鏁版嵁闆嗗垝鍒嗗伐鍏凤紝鏀?寔:
- 璁?粌/楠岃瘉/娴嬭瘯闆嗗垝鍒?
- 鍒嗗眰鍒掑垎锛堜繚鎸佺被鍒?瘮渚嬶級
- 鎸夌洰褰曠粨鏋勮嚜鍔?垝鍒?
"""

import random
import shutil
from pathlib import Path
from typing import List, Tuple, Optional
from collections import defaultdict


class DatasetSplitter:
    """鏁版嵁闆嗗垝鍒嗗櫒"""

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
        鍒掑垎鏁版嵁闆嗕负璁?粌/楠岃瘉/娴嬭瘯闆嗐??

        Args:
            image_dir: 鍥惧儚鐩?綍
            label_dir: 鏍囨敞鐩?綍
            output_dir: 杈撳嚭鐩?綍
            train_ratio: 璁?粌闆嗘瘮渚?
            val_ratio: 楠岃瘉闆嗘瘮渚?
            test_ratio: 娴嬭瘯闆嗘瘮渚?
            seed: 闅忔満绉嶅瓙
            stratify: 鏄?惁鍒嗗眰鍒掑垎锛堜繚鎸佺被鍒?瘮渚嬶級

        Returns:
            鍒掑垎缁熻?淇?伅
        """
        image_dir = Path(image_dir)
        label_dir = Path(label_dir)
        output_dir = Path(output_dir)

        # 楠岃瘉姣斾緥
        total_ratio = train_ratio + val_ratio + test_ratio
        if abs(total_ratio - 1.0) > 0.001:
            raise ValueError(f"姣斾緥涔嬪拰蹇呴』涓?1.0锛屽綋鍓? {total_ratio}")

        # 鏀堕泦閰嶅?鐨勫浘鍍忓拰鏍囨敞
        image_exts = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
        pairs = []

        for img_file in image_dir.iterdir():
            if img_file.suffix.lower() in image_exts:
                label_file = label_dir / f"{img_file.stem}.txt"
                if label_file.exists():
                    pairs.append((img_file, label_file))

        if not pairs:
            raise ValueError(f"鏈?壘鍒版湁鏁堢殑鍥惧儚-鏍囨敞瀵? {image_dir}, {label_dir}")

        # 鍒嗗眰鍒掑垎
        if stratify:
            # 璇诲彇姣忎釜鏍囨敞鏂囦欢鐨勪富瑕佺被鍒?
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

        # 鍒涘缓杈撳嚭鐩?綍骞跺?鍒舵枃浠?
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
            with open(label_file, "r", encoding="utf-8") as f:
                for line in f:
                    parts = line.strip().split()
                    if parts:
                        classes.append(int(parts[0]))
            # 浣跨敤鍑虹幇鏈?澶氱殑绫诲埆浣滀负涓昏?绫诲埆
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
        """分层划分, 保持类别比例"""
        random.seed(seed)

        # 鎸夌被鍒?垎缁?
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

        # 鎵撲贡姣忎釜 split
        for split_name in splits:
            random.shuffle(splits[split_name])

        return splits

    @staticmethod
    def merge_datasets(
        dataset_dirs: List[str],
        output_dir: str
    ) -> dict:
        """
        合并多个数据集

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
                        # 娣诲姞鏁版嵁闆嗗墠缂?閬垮厤閲嶅悕
                        new_name = f"{dataset_dir.name}_{img_file.name}"
                        shutil.copy2(img_file, img_dst / new_name)
                        stats["images"] += 1

                        # 澶嶅埗瀵瑰簲鏍囨敞
                        lbl_file = lbl_src / f"{img_file.stem}.txt"
                        if lbl_file.exists():
                            new_lbl_name = f"{dataset_dir.name}_{img_file.stem}.txt"
                            shutil.copy2(lbl_file, lbl_dst / new_lbl_name)
                            stats["labels"] += 1

        return stats

