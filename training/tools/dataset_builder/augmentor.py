#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Dataset Augmentor

离线数据增强工具，用于扩充小数据集。
支持:
- 几何变换（旋转、翻转、缩放）
- 颜色变换（亮度、对比度、饱和度）
- 噪声添加
- 标注同步变换
"""

import random
from pathlib import Path
from typing import List, Tuple, Optional
import json


class DatasetAugmentor:
    """数据集离线增强器"""

    # 支持的增强操作
    AUGMENTATIONS = [
        "hflip",        # 水平翻转
        "vflip",        # 垂直翻转
        "rotate90",     # 90度旋转
        "rotate180",    # 180度旋转
        "brightness",   # 亮度调整
        "contrast",     # 对比度调整
        "saturation",   # 饱和度调整
        "hue",          # 色调调整
        "blur",         # 模糊
        "noise",        # 噪声
        "scale",        # 缩放
        "crop",         # 随机裁剪
    ]

    def __init__(self, seed: int = 42):
        self.seed = seed
        random.seed(seed)

    def augment_dataset(
        self,
        image_dir: str,
        label_dir: str,
        output_dir: str,
        augmentations: List[str],
        augment_factor: int = 2,
        keep_original: bool = True
    ) -> dict:
        """
        对数据集进行离线增强。

        Args:
            image_dir: 输入图像目录
            label_dir: 输入标注目录
            output_dir: 输出目录
            augmentations: 使用的增强操作列表
            augment_factor: 增强倍数
            keep_original: 是否保留原始数据

        Returns:
            增强统计信息
        """
        try:
            import cv2
            import numpy as np
        except ImportError:
            raise ImportError("需要 opencv-python: pip install opencv-python")

        image_dir = Path(image_dir)
        label_dir = Path(label_dir)
        output_dir = Path(output_dir)

        img_out = output_dir / "images"
        lbl_out = output_dir / "labels"
        img_out.mkdir(parents=True, exist_ok=True)
        lbl_out.mkdir(parents=True, exist_ok=True)

        image_exts = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
        stats = {"original": 0, "augmented": 0, "operations": {}}

        for img_file in image_dir.iterdir():
            if img_file.suffix.lower() not in image_exts:
                continue

            label_file = label_dir / f"{img_file.stem}.txt"
            if not label_file.exists():
                continue

            # 读取图像和标注
            img = cv2.imread(str(img_file))
            if img is None:
                continue

            labels = self._read_labels(label_file)
            h, w = img.shape[:2]

            # 保留原始数据
            if keep_original:
                cv2.imwrite(str(img_out / img_file.name), img)
                self._write_labels(lbl_out / f"{img_file.stem}.txt", labels)
                stats["original"] += 1

            # 生成增强数据
            for i in range(augment_factor):
                aug_img = img.copy()
                aug_labels = labels.copy()

                # 随机选择增强操作
                selected_augs = random.sample(
                    augmentations,
                    min(random.randint(1, 3), len(augmentations))
                )

                for aug_name in selected_augs:
                    aug_img, aug_labels = self._apply_augmentation(
                        aug_img, aug_labels, aug_name, w, h
                    )
                    stats["operations"][aug_name] = stats["operations"].get(aug_name, 0) + 1

                # 保存增强后的数据
                aug_name = f"{img_file.stem}_aug{i:03d}{img_file.suffix}"
                cv2.imwrite(str(img_out / aug_name), aug_img)
                self._write_labels(lbl_out / f"{img_file.stem}_aug{i:03d}.txt", aug_labels)
                stats["augmented"] += 1

        return stats

    def _read_labels(self, label_file: Path) -> List[List[float]]:
        """读取 YOLO 格式标注"""
        labels = []
        with open(label_file, "r") as f:
            for line in f:
                parts = line.strip().split()
                if len(parts) >= 5:
                    labels.append([float(x) for x in parts[:5]])
        return labels

    def _write_labels(self, label_file: Path, labels: List[List[float]]):
        """写入 YOLO 格式标注"""
        with open(label_file, "w") as f:
            for label in labels:
                f.write(f"{int(label[0])} {label[1]:.6f} {label[2]:.6f} {label[3]:.6f} {label[4]:.6f}\n")

    def _apply_augmentation(
        self,
        img,
        labels: List[List[float]],
        aug_name: str,
        orig_w: int,
        orig_h: int
    ) -> Tuple:
        """应用单个增强操作"""
        try:
            import cv2
            import numpy as np
        except ImportError:
            raise ImportError("需要 opencv-python")

        h, w = img.shape[:2]

        if aug_name == "hflip":
            img = cv2.flip(img, 1)
            for label in labels:
                label[1] = 1.0 - label[1]

        elif aug_name == "vflip":
            img = cv2.flip(img, 0)
            for label in labels:
                label[2] = 1.0 - label[2]

        elif aug_name == "rotate90":
            img = cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE)
            new_labels = []
            for label in labels:
                # 旋转90度: x' = y, y' = 1-x
                new_x = label[2]
                new_y = 1.0 - label[1]
                new_w = label[4]  # w 和 h 交换
                new_h = label[3]
                new_labels.append([label[0], new_x, new_y, new_w, new_h])
            labels = new_labels

        elif aug_name == "rotate180":
            img = cv2.rotate(img, cv2.ROTATE_180)
            for label in labels:
                label[1] = 1.0 - label[1]
                label[2] = 1.0 - label[2]

        elif aug_name == "brightness":
            factor = random.uniform(0.7, 1.3)
            img = cv2.convertScaleAbs(img, alpha=factor, beta=0)

        elif aug_name == "contrast":
            factor = random.uniform(0.7, 1.3)
            img = cv2.convertScaleAbs(img, alpha=factor, beta=128 * (1 - factor))

        elif aug_name == "saturation":
            hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV).astype(np.float32)
            hsv[:, :, 1] *= random.uniform(0.7, 1.3)
            hsv[:, :, 1] = np.clip(hsv[:, :, 1], 0, 255)
            img = cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)

        elif aug_name == "hue":
            hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV).astype(np.float32)
            hsv[:, :, 0] += random.uniform(-10, 10)
            hsv[:, :, 0] = hsv[:, :, 0] % 180
            img = cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)

        elif aug_name == "blur":
            ksize = random.choice([3, 5])
            img = cv2.GaussianBlur(img, (ksize, ksize), 0)

        elif aug_name == "noise":
            noise = np.random.normal(0, random.uniform(5, 15), img.shape)
            img = np.clip(img.astype(np.float32) + noise, 0, 255).astype(np.uint8)

        elif aug_name == "scale":
            scale = random.uniform(0.8, 1.2)
            new_w = int(w * scale)
            new_h = int(h * scale)
            img = cv2.resize(img, (new_w, new_h))

            # 如果放大，裁剪到原尺寸
            if scale > 1:
                start_x = (new_w - w) // 2
                start_y = (new_h - h) // 2
                img = img[start_y:start_y + h, start_x:start_x + w]
            # 如果缩小，填充到原尺寸
            else:
                pad_x = (w - new_w) // 2
                pad_y = (h - new_h) // 2
                img = cv2.copyMakeBorder(
                    img, pad_y, h - new_h - pad_y, pad_x, w - new_w - pad_x,
                    cv2.BORDER_CONSTANT, value=[0, 0, 0]
                )

        elif aug_name == "crop":
            crop_ratio = random.uniform(0.7, 0.9)
            crop_w = int(w * crop_ratio)
            crop_h = int(h * crop_ratio)
            start_x = random.randint(0, w - crop_w)
            start_y = random.randint(0, h - crop_h)

            img = img[start_y:start_y + crop_h, start_x:start_x + crop_w]
            img = cv2.resize(img, (w, h))

            # 更新标注
            new_labels = []
            for label in labels:
                # 转换为绝对坐标
                abs_x = label[1] * w
                abs_y = label[2] * h
                abs_w = label[3] * w
                abs_h = label[4] * h

                # 裁剪
                abs_x -= start_x
                abs_y -= start_y

                # 转换回归一化
                new_x = abs_x / crop_w
                new_y = abs_y / crop_h
                new_w = label[3] * w / crop_w
                new_h = label[4] * h / crop_h

                # 检查是否在有效范围内
                if 0 < new_x < 1 and 0 < new_y < 1:
                    new_labels.append([label[0], new_x, new_y, new_w, new_h])

            labels = new_labels

        return img, labels

    @staticmethod
    def get_recommended_augmentations(data_size: int) -> List[str]:
        """
        根据数据量推荐增强策略。

        Args:
            data_size: 数据量

        Returns:
            推荐的增强操作列表
        """
        if data_size < 100:
            return ["hflip", "brightness", "contrast", "saturation", "noise", "scale", "crop"]
        elif data_size < 500:
            return ["hflip", "brightness", "contrast", "saturation", "noise"]
        elif data_size < 1000:
            return ["hflip", "brightness", "contrast"]
        else:
            return ["hflip"]
