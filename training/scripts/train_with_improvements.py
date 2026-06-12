#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Training with Algorithm Improvements

鏀寔浣跨敤鏀硅繘妯″瀷杩涜璁粌鐨勮剼鏈€?

浣跨敤鏂规硶:
    # 浣跨敤 SE 娉ㄦ剰鍔?
    python training/scripts/train_with_improvements.py --model se

    # 浣跨敤 CBAM 娉ㄦ剰鍔?
    python training/scripts/train_with_improvements.py --model cbam

    # 浣跨敤 GhostConv
    python training/scripts/train_with_improvements.py --model ghost

    # 浣跨敤缁煎悎鏀硅繘
    python training/scripts/train_with_improvements.py --model enhanced

    # 鑷畾涔夋ā鍨嬮厤缃?
    python training/scripts/train_with_improvements.py --model-yaml path/to/model.yaml
"""

import warnings
warnings.filterwarnings('ignore', category=UserWarning, module='ultralytics')

import argparse
import sys
from pathlib import Path

# 椤圭洰鏍圭洰褰?
PROJECT_ROOT = Path(__file__).parent.parent.parent
MODELS_DIR = PROJECT_ROOT / 'training' / 'configs' / 'models'

# 棰勫畾涔夌殑鏀硅繘妯″瀷
MODEL_PRESETS = {
    'original': {
        'yaml': None,
        'weights': 'yolo11n.pt',
        'description': '鍘熷 YOLO11n 妯″瀷'
    },
    'se': {
        'yaml': MODELS_DIR / 'yolo11-se.yaml',
        'weights': 'yolo11n.pt',
        'description': 'YOLO11n + SE 娉ㄦ剰鍔?
    },
    'cbam': {
        'yaml': MODELS_DIR / 'yolo11-cbam.yaml',
        'weights': 'yolo11n.pt',
        'description': 'YOLO11n + CBAM 娉ㄦ剰鍔?
    },
    'ghost': {
        'yaml': MODELS_DIR / 'yolo11-ghost.yaml',
        'weights': 'yolo11n.pt',
        'description': 'YOLO11n + GhostConv 杞婚噺绾?
    },
    'enhanced': {
        'yaml': MODELS_DIR / 'yolo11-enhanced.yaml',
        'weights': 'yolo11n.pt',
        'description': 'YOLO11n + CBAM + C2fCIB 缁煎悎鏀硅繘'
    }
}


def get_model_config(preset_name, custom_yaml=None):
    """鑾峰彇妯″瀷閰嶇疆"""
    if custom_yaml:
        return {
            'yaml': Path(custom_yaml),
            'weights': 'yolo11n.pt',
            'description': f'鑷畾涔夋ā鍨? {custom_yaml}'
        }

    if preset_name not in MODEL_PRESETS:
        print(f"閿欒: 鏈煡鐨勬ā鍨嬮璁?'{preset_name}'")
        print(f"鍙敤棰勮: {', '.join(MODEL_PRESETS.keys())}")
        sys.exit(1)

    return MODEL_PRESETS[preset_name]


def build_train_args(args, model_config):
    """鏋勫缓璁粌鍙傛暟"""
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
鍙敤鐨勬ā鍨嬮璁?
  original   - 鍘熷 YOLO11n 妯″瀷
  se         - YOLO11n + SE 娉ㄦ剰鍔?(杞婚噺绾?
  cbam       - YOLO11n + CBAM 娉ㄦ剰鍔?(楂樼簿搴?
  ghost      - YOLO11n + GhostConv (鏋佽嚧杞婚噺)
  enhanced   - YOLO11n + CBAM + C2fCIB (鏈€楂樼簿搴?

绀轰緥:
  python training/scripts/train_with_improvements.py --model cbam --data configs/dataset_custom.yaml
        """
    )

    parser.add_argument(
        '--model',
        type=str,
        default='original',
        choices=list(MODEL_PRESETS.keys()),
        help='妯″瀷棰勮 (榛樿: original)'
    )
    parser.add_argument(
        '--model-yaml',
        type=str,
        default=None,
        help='鑷畾涔夋ā鍨?YAML 閰嶇疆鏂囦欢璺緞'
    )
    parser.add_argument(
        '--data',
        type=str,
        default=None,
        help='鏁版嵁闆嗛厤缃枃浠惰矾寰?
    )
    parser.add_argument(
        '--epochs',
        type=int,
        default=None,
        help='璁粌杞 (榛樿: 200)'
    )
    parser.add_argument(
        '--batch',
        type=int,
        default=None,
        help='鎵规澶у皬 (榛樿: 32)'
    )
    parser.add_argument(
        '--device',
        type=str,
        default=None,
        help='璁惧 (榛樿: 0)'
    )
    parser.add_argument(
        '--name',
        type=str,
        default=None,
        help='瀹為獙鍚嶇О'
    )
    parser.add_argument(
        '--dry-run',
        action='store_true',
        help='浠呮樉绀洪厤缃紝涓嶆墽琛岃缁?
    )

    args = parser.parse_args()

    # 鑾峰彇妯″瀷閰嶇疆
    model_config = get_model_config(args.model, args.model_yaml)

    print("=" * 60)
    print("OpenIris 璁粌 - 绠楁硶鏀硅繘妯″紡")
    print("=" * 60)

    print("\n銆愭ā鍨嬮厤缃€?)
    print(f"  棰勮: {args.model}")
    print(f"  鎻忚堪: {model_config['description']}")
    if model_config['yaml']:
        print(f"  YAML: {model_config['yaml']}")
    print(f"  棰勮缁冩潈閲? {model_config['weights']}")

    # 鏋勫缓璁粌鍙傛暟
    train_config = build_train_args(args, model_config)

    print("\n銆愯缁冮厤缃€?)
    print(f"  鏁版嵁闆? {train_config['data']}")
    print(f"  杞: {train_config['epochs']}")
    print(f"  鎵规澶у皬: {train_config['batch']}")
    print(f"  浼樺寲鍣? {train_config['optimizer']}")
    print(f"  瀛︿範鐜? {train_config['lr0']}")
    print(f"  璁惧: {train_config['device']}")
    print(f"  AMP: {train_config['amp']}")

    if args.dry_run:
        print("\n[Dry Run] 浠呮樉绀洪厤缃紝涓嶆墽琛岃缁?)
        return

    # 寮€濮嬭缁?
    try:
        from ultralytics import YOLO

        print("\n鍔犺浇妯″瀷...")
        if model_config['yaml']:
            model = YOLO(str(model_config['yaml']))
            model.load(model_config['weights'])
        else:
            model = YOLO(model_config['weights'])

        print("寮€濮嬭缁?..\n")
        results = model.train(**train_config)

        print("\n璁粌瀹屾垚!")
        print(f"鏈€浼樻潈閲? {train_config['project']}/{train_config['name']}/weights/best.pt")

    except ImportError:
        print("\n閿欒: 鏈畨瑁?ultralytics")
        print("璇峰厛婵€娲荤幆澧? your training environment")
        sys.exit(1)
    except Exception as e:
        print(f"\n璁粌澶辫触: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == '__main__':
    main()


