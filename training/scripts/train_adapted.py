#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Adapted Training Script

Adapted for D:\\YOLO11Preinit reference environment.
Optimized for RTX 4060 Laptop (8GB).

Usage:
    # Activate reference environment first
    python training/scripts/train_adapted.py

    # Specify dataset
    python training/scripts/train_adapted.py --data path/to/data.yaml
"""

import warnings
warnings.filterwarnings('ignore')

import argparse
import sys
from pathlib import Path

# Project root
PROJECT_ROOT = Path(__file__).parent.parent.parent


def get_training_config(args):
    """Get training configuration"""
    config = {
        # Model (must use nano for mobile)
        'model': 'yolo11n.pt',

        # Dataset
        'data': args.data or str(PROJECT_ROOT / 'training' / 'configs' / 'dataset_custom.yaml'),

        # Training params
        'epochs': args.epochs or 200,
        'batch': args.batch or 32,
        'imgsz': 640,  # Must be 640

        # Optimizer
        'optimizer': 'AdamW',
        'lr0': 0.001,
        'lrf': 0.01,
        'momentum': 0.937,
        'weight_decay': 0.0005,

        # LR schedule
        'warmup_epochs': 5.0,
        'warmup_momentum': 0.8,
        'warmup_bias_lr': 0.1,

        # Loss
        'box': 7.5,
        'cls': 0.5,
        'dfl': 1.5,

        # Regularization
        'label_smoothing': 0.02,
        'patience': 50,

        # Hardware (RTX 4060 optimized)
        'device': args.device or 0,
        'amp': True,  # Must enable
        'workers': 4,  # Windows recommended

        # Output
        'project': str(PROJECT_ROOT / 'runs' / 'train'),
        'name': args.name or 'openiris_adapted',
        'exist_ok': True,
        'pretrained': True,
        'save': True,
        'save_period': 10,
    }

    return config


def validate_config(config):
    """Validate config meets OpenIris requirements"""
    errors = []
    warns = []

    if 'yolo11n' not in config['model']:
        errors.append(f"Model must be yolo11n, got: {config['model']}")

    if config['imgsz'] != 640:
        errors.append(f"Image size must be 640, got: {config['imgsz']}")

    if not config['amp']:
        warns.append("Recommend enabling AMP for faster training")

    if config['optimizer'] == 'SGD':
        warns.append("Recommend AdamW optimizer for better stability")

    return errors, warns


def print_config(config):
    """Print training configuration"""
    print("\n" + "=" * 60)
    print("OpenIris Training Configuration")
    print("=" * 60)

    print("\n[Model]")
    print(f"  Model: {config['model']}")
    print(f"  Input Size: {config['imgsz']}")

    print("\n[Training]")
    print(f"  Epochs: {config['epochs']}")
    print(f"  Batch Size: {config['batch']}")
    print(f"  Optimizer: {config['optimizer']}")
    print(f"  Learning Rate: {config['lr0']}")

    print("\n[Regularization]")
    print(f"  Label Smoothing: {config['label_smoothing']}")
    print(f"  Patience: {config['patience']}")

    print("\n[Hardware]")
    print(f"  Device: {config['device']}")
    print(f"  AMP: {config['amp']}")
    print(f"  Workers: {config['workers']}")

    print("\n[Output]")
    print(f"  Project: {config['project']}")
    print(f"  Name: {config['name']}")

    print("=" * 60)


def main():
    parser = argparse.ArgumentParser(
        description="OpenIris Adapted Training Script",
    )

    parser.add_argument('--data', type=str, default=None, help='Dataset config (YAML)')
    parser.add_argument('--epochs', type=int, default=None, help='Training epochs (default: 200)')
    parser.add_argument('--batch', type=int, default=None, help='Batch size (default: 32)')
    parser.add_argument('--device', type=str, default=None, help='Device (default: 0)')
    parser.add_argument('--name', type=str, default=None, help='Experiment name')
    parser.add_argument('--dry-run', action='store_true', help='Show config only')

    args = parser.parse_args()

    # Get config
    config = get_training_config(args)

    # Validate
    errors, warns = validate_config(config)

    if errors:
        print("\nConfig Errors:")
        for error in errors:
            print(f"  X {error}")
        sys.exit(1)

    if warns:
        print("\nConfig Warnings:")
        for warning in warns:
            print(f"  ! {warning}")

    # Print config
    print_config(config)

    # Dry run
    if args.dry_run:
        print("\n[Dry Run] Showing config only, no training")
        return

    # Start training
    try:
        from ultralytics import YOLO

        print("\nLoading model...")
        model = YOLO(config['model'])

        print("Starting training...\n")

        # Remove model key (already loaded)
        train_config = {k: v for k, v in config.items() if k != 'model'}
        results = model.train(**train_config)

        print("\nTraining complete!")
        print(f"Best weights: {config['project']}/{config['name']}/weights/best.pt")

    except ImportError:
        print("\nError: ultralytics not installed")
        print("Please activate environment first")
        sys.exit(1)
    except Exception as e:
        print(f"\nTraining failed: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()
