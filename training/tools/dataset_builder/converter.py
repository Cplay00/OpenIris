#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Dataset Format Converter

支持多种标注格式转换为 YOLO 格式:
- COCO JSON
- Pascal VOC XML
- LabelMe JSON
- 自定义 CSV
"""

import json
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import List, Dict, Optional
import shutil


class DatasetConverter:
    """数据集格式转换器"""

    @staticmethod
    def coco_to_yolo(
        coco_json: str,
        output_dir: str,
        image_dir: Optional[str] = None
    ) -> Dict:
        """
        将 COCO JSON 标注转换为 YOLO 格式。

        Args:
            coco_json: COCO annotations.json 路径
            output_dir: 输出目录
            image_dir: 图像目录（可选，默认从 JSON 中读取）

        Returns:
            转换统计信息
        """
        output_dir = Path(output_dir)
        label_dir = output_dir / "labels"
        label_dir.mkdir(parents=True, exist_ok=True)

        with open(coco_json, "r", encoding="utf-8") as f:
            coco = json.load(f)

        # 构建类别映射
        categories = {cat["id"]: idx for idx, cat in enumerate(coco["categories"])}
        cat_names = {idx: cat["name"] for idx, cat in enumerate(coco["categories"])}

        # 构建图像信息
        images = {img["id"]: img for img in coco["images"]}

        # 按图像分组标注
        annotations = {}
        for ann in coco["annotations"]:
            img_id = ann["image_id"]
            if img_id not in annotations:
                annotations[img_id] = []
            annotations[img_id].append(ann)

        stats = {"images": 0, "labels": 0, "categories": len(categories)}

        # 转换标注
        for img_id, img_info in images.items():
            img_w = img_info["width"]
            img_h = img_info["height"]
            img_name = Path(img_info["file_name"]).stem

            label_file = label_dir / f"{img_name}.txt"

            with open(label_file, "w") as f:
                if img_id in annotations:
                    for ann in annotations[img_id]:
                        # COCO bbox: [x, y, w, h] (绝对坐标)
                        x, y, w, h = ann["bbox"]

                        # 转换为 YOLO 格式: [x_center, y_center, w, h] (归一化)
                        x_center = (x + w / 2) / img_w
                        y_center = (y + h / 2) / img_h
                        w_norm = w / img_w
                        h_norm = h / img_h

                        class_id = categories[ann["category_id"]]

                        # 裁剪到 [0, 1]
                        x_center = max(0, min(1, x_center))
                        y_center = max(0, min(1, y_center))
                        w_norm = max(0, min(1, w_norm))
                        h_norm = max(0, min(1, h_norm))

                        f.write(f"{class_id} {x_center:.6f} {y_center:.6f} {w_norm:.6f} {h_norm:.6f}\n")
                        stats["labels"] += 1

            stats["images"] += 1

        # 复制图像（如果指定了目录）
        if image_dir:
            img_output = output_dir / "images"
            img_output.mkdir(exist_ok=True)
            for img_info in images.values():
                src = Path(image_dir) / img_info["file_name"]
                if src.exists():
                    shutil.copy2(src, img_output / Path(img_info["file_name"]).name)

        # 生成 data.yaml
        DatasetConverter._generate_data_yaml(
            output_dir,
            cat_names,
            len(categories)
        )

        return stats

    @staticmethod
    def voc_to_yolo(
        voc_dir: str,
        output_dir: str,
        image_dir: Optional[str] = None
    ) -> Dict:
        """
        将 Pascal VOC XML 标注转换为 YOLO 格式。

        Args:
            voc_dir: VOC Annotations 目录
            output_dir: 输出目录
            image_dir: 图像目录

        Returns:
            转换统计信息
        """
        voc_dir = Path(voc_dir)
        output_dir = Path(output_dir)
        label_dir = output_dir / "labels"
        label_dir.mkdir(parents=True, exist_ok=True)

        # 收集所有类别
        all_classes = set()
        xml_files = list(voc_dir.glob("*.xml"))

        for xml_file in xml_files:
            tree = ET.parse(xml_file)
            root = tree.getroot()
            for obj in root.findall("object"):
                all_classes.add(obj.find("name").text)

        class_map = {name: idx for idx, name in enumerate(sorted(all_classes))}

        stats = {"images": 0, "labels": 0, "categories": len(class_map)}

        # 转换标注
        for xml_file in xml_files:
            tree = ET.parse(xml_file)
            root = tree.getroot()

            img_w = int(root.find("size/width").text)
            img_h = int(root.find("size/height").text)
            img_name = xml_file.stem

            label_file = label_dir / f"{img_name}.txt"

            with open(label_file, "w") as f:
                for obj in root.findall("object"):
                    class_name = obj.find("name").text
                    bbox = obj.find("bndbox")

                    xmin = float(bbox.find("xmin").text)
                    ymin = float(bbox.find("ymin").text)
                    xmax = float(bbox.find("xmax").text)
                    ymax = float(bbox.find("ymax").text)

                    # 转换为 YOLO 格式
                    x_center = ((xmin + xmax) / 2) / img_w
                    y_center = ((ymin + ymax) / 2) / img_h
                    w = (xmax - xmin) / img_w
                    h = (ymax - ymin) / img_h

                    class_id = class_map[class_name]
                    f.write(f"{class_id} {x_center:.6f} {y_center:.6f} {w:.6f} {h:.6f}\n")
                    stats["labels"] += 1

            stats["images"] += 1

        # 生成 data.yaml
        cat_names = {idx: name for name, idx in class_map.items()}
        DatasetConverter._generate_data_yaml(output_dir, cat_names, len(class_map))

        return stats

    @staticmethod
    def _generate_data_yaml(
        output_dir: Path,
        class_names: Dict[int, str],
        nc: int
    ):
        """生成 data.yaml 配置文件"""
        yaml_content = f"""# Auto-generated by OpenIris Dataset Builder
path: {output_dir.absolute()}
train: images/train
val: images/val

nc: {nc}
names:
"""
        for idx in range(nc):
            name = class_names.get(idx, f"class_{idx}")
            yaml_content += f"  {idx}: {name}\n"

        yaml_file = output_dir / "data.yaml"
        with open(yaml_file, "w", encoding="utf-8") as f:
            f.write(yaml_content)
