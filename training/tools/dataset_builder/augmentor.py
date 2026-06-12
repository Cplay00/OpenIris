#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Dataset Augmentor

绂荤嚎鏁版嵁澧炲己宸ュ叿锛岀敤浜庢墿鍏呭皬鏁版嵁闆嗐€?
鏀寔:
- 鍑犱綍鍙樻崲锛堟棆杞€佺炕杞€佺缉鏀撅級
- 棰滆壊鍙樻崲锛堜寒搴︺€佸姣斿害銆侀ケ鍜屽害锛?
- 鍣0娣诲姞
- 鏍囨敞鍚屾鍙樻崲
"""

import random
from pathlib import Path
from typing import List, Tuple, Optional
import json


class DatasetAugmentor:
    """鏁版嵁闆嗙绾垮寮哄櫒"""

    # 鏀寔鐨勫寮烘搷浣?
    AUGMENTATIONS = [
        "hflip",        # 姘村钩缈昏浆
        "vflip",        # 鍨傜洿缈昏浆
        "rotate90",     # 90搴︽棆杞?
        "rotate180",    # 180搴︽棆杞?
        "brightness",   # 浜害璋冩暣
        "contrast",     # 瀵规瘮搴﹁皟鏁?
        "saturation",   # 楗卞拰搴﹁皟鏁?
        "hue",          # 鑹茶皟璋冩暣
        "blur",         # 妯$硦
        "noise",        # 鍣0
        "scale",        # 缂╂斁
        "crop",         # 闅忔満瑁佸壀
    ]

    def __init__(self, seed: int = 42):
        self.seed = seed
        self.rng = random.Random(seed)

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
        瀵规暟鎹泦杩涜绂荤嚎澧炲己銆?

        Args:
            image_dir: 杈撳叆鍥惧儚鐩綍
            label_dir: 杈撳叆鏍囨敞鐩綍
            output_dir: 杈撳嚭鐩綍
            augmentations: 浣跨敤鐨勫寮烘搷浣滃垪琛?
            augment_factor: 澧炲己鍊嶆暟
            keep_original: 鏄惁淇濈暀鍘熷鏁版嵁

        Returns:
            澧炲己缁熻淇℃伅
        """
        try:
            import cv2
            import numpy as np
        except ImportError:
            raise ImportError("闇€瑕?opencv-python: pip install opencv-python")

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

            # 璇诲彇鍥惧儚鍜屾爣娉?
            img = cv2.imread(str(img_file))
            if img is None:
                continue

            labels = self._read_labels(label_file)
            h, w = img.shape[:2]

            # 淇濈暀鍘熷鏁版嵁
            if keep_original:
                cv2.imwrite(str(img_out / img_file.name), img)
                self._write_labels(lbl_out / f"{img_file.stem}.txt", labels)
                stats["original"] += 1

            # 鐢熸垚澧炲己鏁版嵁
            for i in range(augment_factor):
                aug_img = img.copy()
                aug_labels = labels.copy()

                # 闅忔満閫夋嫨澧炲己鎿嶄綔
                selected_augs = self.rng.sample(
                    augmentations,
                    min(self.rng.randint(1, 3), len(augmentations))
                )

                for aug_name in selected_augs:
                    aug_img, aug_labels = self._apply_augmentation(
                        aug_img, aug_labels, aug_name, w, h
                    )
                    stats["operations"][aug_name] = stats["operations"].get(aug_name, 0) + 1

                # 淇濆瓨澧炲己鍚庣殑鏁版嵁
                aug_name = f"{img_file.stem}_aug{i:03d}{img_file.suffix}"
                cv2.imwrite(str(img_out / aug_name), aug_img)
                self._write_labels(lbl_out / f"{img_file.stem}_aug{i:03d}.txt", aug_labels)
                stats["augmented"] += 1

        return stats

    def _read_labels(self, label_file: Path) -> List[List[float]]:
        """璇诲彇 YOLO 鏍煎紡鏍囨敞"""
        labels = []
        with open(label_file, "r", encoding="utf-8") as f:
            for line in f:
                parts = line.strip().split()
                if len(parts) >= 5:
                    labels.append([float(x) for x in parts[:5]])
        return labels

    def _write_labels(self, label_file: Path, labels: List[List[float]]):
        """鍐欏叆 YOLO 鏍煎紡鏍囨敞"""
        with open(label_file, "w", encoding="utf-8") as f:
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
        """搴旂敤鍗曚釜澧炲己鎿嶄綔"""
        try:
            import cv2
            import numpy as np
        except ImportError:
            raise ImportError("闇€瑕?opencv-python")

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
                # 鏃嬭浆90搴? x' = y, y' = 1-x
                new_x = label[2]
                new_y = 1.0 - label[1]
                new_w = label[4]  # w 鍜?h 浜ゆ崲
                new_h = label[3]
                new_labels.append([label[0], new_x, new_y, new_w, new_h])
            labels = new_labels

        elif aug_name == "rotate180":
            img = cv2.rotate(img, cv2.ROTATE_180)
            for label in labels:
                label[1] = 1.0 - label[1]
                label[2] = 1.0 - label[2]

        elif aug_name == "brightness":
            factor = self.rng.uniform(0.7, 1.3)
            img = cv2.convertScaleAbs(img, alpha=factor, beta=0)

        elif aug_name == "contrast":
            factor = self.rng.uniform(0.7, 1.3)
            img = cv2.convertScaleAbs(img, alpha=factor, beta=128 * (1 - factor))

        elif aug_name == "saturation":
            hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV).astype(np.float32)
            hsv[:, :, 1] *= self.rng.uniform(0.7, 1.3)
            hsv[:, :, 1] = np.clip(hsv[:, :, 1], 0, 255)
            img = cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)

        elif aug_name == "hue":
            hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV).astype(np.float32)
            hsv[:, :, 0] += self.rng.uniform(-10, 10)
            hsv[:, :, 0] = hsv[:, :, 0] % 180
            img = cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)

        elif aug_name == "blur":
            ksize = self.rng.choice([3, 5])
            img = cv2.GaussianBlur(img, (ksize, ksize), 0)

        elif aug_name == "noise":
            noise = np.random.normal(0, self.rng.uniform(5, 15), img.shape)
            img = np.clip(img.astype(np.float32) + noise, 0, 255).astype(np.uint8)

        elif aug_name == "scale":
            scale = self.rng.uniform(0.8, 1.2)
            new_w = int(w * scale)
            new_h = int(h * scale)
            img = cv2.resize(img, (new_w, new_h))

            # 濡傛灉鏀惧ぇ锛岃鍓埌鍘熷昂瀵?
            if scale > 1:
                start_x = (new_w - w) // 2
                start_y = (new_h - h) // 2
                img = img[start_y:start_y + h, start_x:start_x + w]
            # 濡傛灉缂╁皬锛屽~鍏呭埌鍘熷昂瀵?
            else:
                pad_x = (w - new_w) // 2
                pad_y = (h - new_h) // 2
                img = cv2.copyMakeBorder(
                    img, pad_y, h - new_h - pad_y, pad_x, w - new_w - pad_x,
                    cv2.BORDER_CONSTANT, value=[0, 0, 0]
                )

        elif aug_name == "crop":
            crop_ratio = self.rng.uniform(0.7, 0.9)
            crop_w = int(w * crop_ratio)
            crop_h = int(h * crop_ratio)
            start_x = self.rng.randint(0, w - crop_w)
            start_y = self.rng.randint(0, h - crop_h)

            img = img[start_y:start_y + crop_h, start_x:start_x + crop_w]
            img = cv2.resize(img, (w, h))

            # 鏇存柊鏍囨敞
            new_labels = []
            for label in labels:
                # 杞崲涓虹粷瀵瑰潗鏍?
                abs_x = label[1] * w
                abs_y = label[2] * h
                abs_w = label[3] * w
                abs_h = label[4] * h

                # 瑁佸壀
                abs_x -= start_x
                abs_y -= start_y

                # 杞崲鍥炲綊涓€鍖?
                new_x = abs_x / crop_w
                new_y = abs_y / crop_h
                new_w = label[3] * w / crop_w
                new_h = label[4] * h / crop_h

                # 妫€鏌ユ槸鍚﹀湪鏈夋晥鑼冨洿鍐?
                if 0 < new_x < 1 and 0 < new_y < 1 and new_w > 0 and new_h > 0:
                    new_labels.append([label[0], new_x, new_y, new_w, new_h])

            labels = new_labels

        return img, labels

    @staticmethod
    def get_recommended_augmentations(data_size: int) -> List[str]:
        """
        鏍规嵁鏁版嵁閲忔帹鑽愬寮虹瓥鐣ャ€?

        Args:
            data_size: 鏁版嵁閲?

        Returns:
            鎺ㄨ崘鐨勫寮烘搷浣滃垪琛?
        """
        if data_size < 100:
            return ["hflip", "brightness", "contrast", "saturation", "noise", "scale", "crop"]
        elif data_size < 500:
            return ["hflip", "brightness", "contrast", "saturation", "noise"]
        elif data_size < 1000:
            return ["hflip", "brightness", "contrast"]
        else:
            return ["hflip"]


