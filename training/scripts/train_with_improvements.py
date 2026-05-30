#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Training with Algorithm Improvements

支持使用改进模型进行训练的脚本。

使用方法:
    # 使用 SE 注意力
    python training/scripts/train_with_improvements.py --model se

    # 使用 CBAM 注意力
    python training/scripts/train_with_improvements.py --model cbam

    # 使用 GhostConv
    python training/scripts/train_with_improvements.py --model ghost

    # 使用综合改进
    python training/scripts/train_with_improvements.py --model enhanced

    # 自定义模型配置
    python training/scripts/train_with_improvements.py --model-yaml path/to/model.yaml
"""

import warnings
warnings.filterwarnings('ignore')

import argparse
import sys
from pathlib import Path

# 项目根目录
PROJECT_ROOT = Path(__file__).parent.parent.parent
MODELS_DIR = PROJECT_ROOT / 'training' / 'configs' / 'models'

# 预定义的改进模型
MODEL_PRESETS = {
    'original': {
        'yaml': None,
        'weights': 'yolo11n.pt',
        'description': '原始 YOLO11n 模型'
    },
    'se': {
        'yaml': MODELS_DIR / 'yolo11-se.yaml',
        'weights': 'yolo11n.pt',
        'description': 'YOLO11n + SE 注意力'
    },
    'cbam': {
        'yaml': MODELS_DIR / 'yolo11-cbam.yaml',
        'weights': 'yolo11n.pt',
        'description': 'YOLO11n + CBAM 注意力'
    },
    'ghost': {
        'yaml': MODELS_DIR / 'yolo11-ghost.yaml',
        'weights': 'yolo11n.pt',
        'description': 'YOLO11n + GhostConv 轻量级'
    },
    'enhanced': {
        'yaml': MODELS_DIR / 'yolo11-enhanced.yaml',
        'weights': 'yolo11n.pt',
        'description': 'YOLO11n + CBAM + C2fCIB 综合改进'
    }
}


def get_model_config(preset_name, custom_yaml=None):
    """获取模型配置"""
    if custom_yaml:
        return {
            'yaml': Path(custom_yaml),
            'weights': 'yolo11n.pt',
            'description': f'自定义模型: {custom_yaml}'
        }

    if preset_name not in MODEL_PRESETS:
        print(f"错误: 未知的模型预设 '{preset_name}'")
        print(f"可用预设: {', '.join(MODEL_PRESETS.keys())}")
        sys.exit(1)

    return MODEL_PRESETS[preset_name]


def build_train_args(args, model_config):
    """构建训练参数"""
    config = {
        'data': args.data or str(PROJECT_ROOT / 'training' / 'configs' / 'dataset_custom.yaml'),
        'epochs': args.epochs or 200,
        'batch': args.batch or 32,
        'imgsz': 640,

        'optimizer': 'AdamW',
        'lr0': 0.001,
        'lrf': 0.01,
        'weight_decay': 0.0005,

        'warmup_epochs': 5.0,
        'label_smoothing': 0.02,
        'patience': 50,

        'device': args.device or 0,
        'amp': True,
        'workers': 4,

        'project': str(PROJECT_ROOT / 'runs' / 'train'),
        'name': args.name or f'openiris_{args.model}',
        'exist_ok': True,
        'pretrained': True,
        'save': True,
        'save_period': 10,
    }

    return config


def main():
    parser = argparse.ArgumentParser(
        description="OpenIris Training with Algorithm Improvements",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
可用的模型预设:
  original   - 原始 YOLO11n 模型
  se         - YOLO11n + SE 注意力 (轻量级)
  cbam       - YOLO11n + CBAM 注意力 (高精度)
  ghost      - YOLO11n + GhostConv (极致轻量)
  enhanced   - YOLO11n + CBAM + C2fCIB (最高精度)

示例:
  python training/scripts/train_with_improvements.py --model cbam --data configs/dataset_custom.yaml
        """
    )

    parser.add_argument(
        '--model',
        type=str,
        default='original',
        choices=list(MODEL_PRESETS.keys()),
        help='模型预设 (默认: original)'
    )
    parser.add_argument(
        '--model-yaml',
        type=str,
        default=None,
        help='自定义模型 YAML 配置文件路径'
    )
    parser.add_argument(
        '--data',
        type=str,
        default=None,
        help='数据集配置文件路径'
    )
    parser.add_argument(
        '--epochs',
        type=int,
        default=None,
        help='训练轮次 (默认: 200)'
    )
    parser.add_argument(
        '--batch',
        type=int,
        default=None,
        help='批次大小 (默认: 32)'
    )
    parser.add_argument(
        '--device',
        type=str,
        default=None,
        help='设备 (默认: 0)'
    )
    parser.add_argument(
        '--name',
        type=str,
        default=None,
        help='实验名称'
    )
    parser.add_argument(
        '--dry-run',
        action='store_true',
        help='仅显示配置，不执行训练'
    )

    args = parser.parse_args()

    # 获取模型配置
    model_config = get_model_config(args.model, args.model_yaml)

    print("=" * 60)
    print("OpenIris 训练 - 算法改进模式")
    print("=" * 60)

    print("\n【模型配置】")
    print(f"  预设: {args.model}")
    print(f"  描述: {model_config['description']}")
    if model_config['yaml']:
        print(f"  YAML: {model_config['yaml']}")
    print(f"  预训练权重: {model_config['weights']}")

    # 构建训练参数
    train_config = build_train_args(args, model_config)

    print("\n【训练配置】")
    print(f"  数据集: {train_config['data']}")
    print(f"  轮次: {train_config['epochs']}")
    print(f"  批次大小: {train_config['batch']}")
    print(f"  优化器: {train_config['optimizer']}")
    print(f"  学习率: {train_config['lr0']}")
    print(f"  设备: {train_config['device']}")
    print(f"  AMP: {train_config['amp']}")

    if args.dry_run:
        print("\n[Dry Run] 仅显示配置，不执行训练")
        return

    # 开始训练
    try:
        from ultralytics import YOLO

        print("\n加载模型...")
        if model_config['yaml']:
            model = YOLO(str(model_config['yaml']))
            model.load(model_config['weights'])
        else:
            model = YOLO(model_config['weights'])

        print("开始训练...\n")
        results = model.train(**train_config)

        print("\n训练完成!")
        print(f"最优权重: {train_config['project']}/{train_config['name']}/weights/best.pt")

    except ImportError:
        print("\n错误: 未安装 ultralytics")
        print("请先激活环境: D:\\YOLO11Preinit\\Yolo11Pre\\Scripts\\activate")
        sys.exit(1)
    except Exception as e:
        print(f"\n训练失败: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == '__main__':
    main()
