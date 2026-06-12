#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Dataset Format Converter

鏀寔澶氱鏍囨敞鏍煎紡杞崲涓?YOLO 鏍煎紡:
- COCO JSON
- Pascal VOC XML
- LabelMe JSON
- 鑷畾涔?CSV
"""

import json
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import List, Dict, Optional
import shutil


class DatasetConverter:
    """鏁版嵁闆嗘牸寮忚浆鎹㈠櫒"""

    @staticmethod
    def coco_to_yolo(
        coco_json: str,
        output_dir: str,
        image_dir: Optional[str] = None
    ) -> Dict:
        """
        灏?COCO JSON 鏍囨敞杞崲涓?YOLO 鏍煎紡銆?

        Args:
            coco_json: COCO annotations.json 璺緞
            output_dir: 杈撳嚭鐩綍
            image_dir: 鍥惧儚鐩綍锛堝彲閫夛紝榛樿浠?JSON 涓鍙栵級

        Returns:
            杞崲缁熻淇℃伅
        """
        output_dir = Path(output_dir)
        label_dir = output_dir / "labels"
        label_dir.mkdir(parents=True, exist_ok=True)

        with open(coco_json, "r", encoding="utf-8") as f:
            coco = json.load(f)


        # Validate required COCO keys
        for key in ["images", "annotations", "categories"]:
            if key not in coco:
                raise ValueError(f"COCO JSON missing required field: {key}")

        # 鏋勫缓绫诲埆鏄犲皠
        categories = {cat["id"]: idx for idx, cat in enumerate(coco["categories"])}
        cat_names = {idx: cat["name"] for idx, cat in enumerate(coco["categories"])}

        # 鏋勫缓鍥惧儚淇℃伅
        images = {img["id"]: img for img in coco["images"]}

        # 鎸夊浘鍍忓垎缁勬爣娉?
        annotations = {}
        for ann in coco["annotations"]:
            img_id = ann["image_id"]
            if img_id not in annotations:
                annotations[img_id] = []
            annotations[img_id].append(ann)

        stats = {"images": 0, "labels": 0, "categories": len(categories)}

        # 杞崲鏍囨敞
        for img_id, img_info in images.items():
            img_w = img_info["width"]
            img_h = img_info["height"]
            img_name = Path(img_info["file_name"]).stem

            label_file = label_dir / f"{img_name}.txt"

            with open(label_file, "w") as f:
                if img_id in annotations:
                    for ann in annotations[img_id]:
                        # COCO bbox: [x, y, w, h] (缁濆鍧愭爣)
                        x, y, w, h = ann["bbox"]

                        # 杞崲涓?YOLO 鏍煎紡: [x_center, y_center, w, h] (褰掍竴鍖?
                        x_center = (x + w / 2) / img_w
                        y_center = (y + h / 2) / img_h
                        w_norm = w / img_w
                        h_norm = h / img_h

                        class_id = categories[ann["category_id"]]

                        # 瑁佸壀鍒?[0, 1]
                        x_center = max(0, min(1, x_center))
                        y_center = max(0, min(1, y_center))
                        w_norm = max(0, min(1, w_norm))
                        h_norm = max(0, min(1, h_norm))

                        f.write(f"{class_id} {x_center:.6f} {y_center:.6f} {w_norm:.6f} {h_norm:.6f}\n")
                        stats["labels"] += 1

            stats["images"] += 1

        # 澶嶅埗鍥惧儚锛堝鏋滄寚瀹氫簡鐩綍锛?
        if image_dir:
            img_output = output_dir / "images"
            img_output.mkdir(exist_ok=True)
            for img_info in images.values():
                src = Path(image_dir) / img_info["file_name"]
                if src.exists():
                    shutil.copy2(src, img_output / Path(img_info["file_name"]).name)

        # 鐢熸垚 data.yaml
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
        灏?Pascal VOC XML 鏍囨敞杞崲涓?YOLO 鏍煎紡銆?

        Args:
            voc_dir: VOC Annotations 鐩綍
            output_dir: 杈撳嚭鐩綍
            image_dir: 鍥惧儚鐩綍

        Returns:
            杞崲缁熻淇℃伅
        """
        voc_dir = Path(voc_dir)
        output_dir = Path(output_dir)
        label_dir = output_dir / "labels"
        label_dir.mkdir(parents=True, exist_ok=True)

        # 鏀堕泦鎵€鏈夌被鍒?
        all_classes = set()
        xml_files = list(voc_dir.glob("*.xml"))

        for xml_file in xml_files:
            try:
                tree = ET.parse(xml_file)
            except ET.ParseError as e:
                print(f"Warning: Skipping malformed XML {xml_file}: {e}")
                continue
            for obj in root.findall("object"):
                all_classes.add(obj.find("name").text)

        class_map = {name: idx for idx, name in enumerate(sorted(all_classes))}

        stats = {"images": 0, "labels": 0, "categories": len(class_map)}

        # 杞崲鏍囨敞
        for xml_file in xml_files:
            try:
                tree = ET.parse(xml_file)
            except ET.ParseError as e:
                print(f"Warning: Skipping malformed XML {xml_file}: {e}")
                continue

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

                    # 杞崲涓?YOLO 鏍煎紡
                    x_center = ((xmin + xmax) / 2) / img_w
                    y_center = ((ymin + ymax) / 2) / img_h
                    w = (xmax - xmin) / img_w
                    h = (ymax - ymin) / img_h

                    class_id = class_map[class_name]
                    f.write(f"{class_id} {x_center:.6f} {y_center:.6f} {w:.6f} {h:.6f}\n")
                    stats["labels"] += 1

            stats["images"] += 1

        # 鐢熸垚 data.yaml
        cat_names = {idx: name for name, idx in class_map.items()}
        DatasetConverter._generate_data_yaml(output_dir, cat_names, len(class_map))

        return stats

    @staticmethod
    def _generate_data_yaml(
        output_dir: Path,
        class_names: Dict[int, str],
        nc: int
    ):
        """鐢熸垚 data.yaml 閰嶇疆鏂囦欢"""
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
