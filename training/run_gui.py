#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Training GUI Entry Point

Place this file at training/ root for easy access.
Double-click or run: python training/run_gui.py
"""

import sys
import os
from pathlib import Path

# Ensure project root is in path
PROJECT_ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(PROJECT_ROOT))
os.chdir(str(PROJECT_ROOT))

def main():
    # Check dependencies
    missing = []
    try:
        import tkinter
    except ImportError:
        missing.append("tkinter (Python standard library)")
    try:
        import yaml
    except ImportError:
        missing.append("pyyaml (pip install pyyaml)")

    if missing:
        print("Missing dependencies:")
        for m in missing:
            print(f"  - {m}")
        print("\nInstall: pip install pyyaml")
        sys.exit(1)

    from training.gui.main_window import main as run_gui
    run_gui()


if __name__ == "__main__":
    main()
