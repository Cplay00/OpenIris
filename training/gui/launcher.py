#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Training GUI Launcher

启动训练图形界面的入口脚本。
"""

import sys
import os
from pathlib import Path


def check_dependencies():
    """检查依赖"""
    missing = []

    try:
        import tkinter
    except ImportError:
        missing.append("tkinter (Python 标准库，通常随 Python 一起安装)")

    try:
        import yaml
    except ImportError:
        missing.append("PyYAML (pip install pyyaml)")

    return missing


def main():
    """主函数"""
    # 检查依赖
    missing = check_dependencies()

    if missing:
        print("=" * 60)
        print("缺少以下依赖:")
        print("=" * 60)
        for dep in missing:
            print(f"  - {dep}")
        print("\n请先安装缺少的依赖:")
        print("  pip install pyyaml")
        print("=" * 60)
        sys.exit(1)

    # 添加项目根目录到 Python 路径
    project_root = Path(__file__).parent.parent.parent
    sys.path.insert(0, str(project_root))

    # 启动 GUI
    from training.gui.main_window import main as run_gui
    run_gui()


if __name__ == "__main__":
    main()
