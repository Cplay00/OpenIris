#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Training GUI Launcher (Legacy)

For direct launch, use: training/run_gui.py
"""

import sys
from pathlib import Path

# Add project root to path
PROJECT_ROOT = Path(__file__).parent.parent.parent
sys.path.insert(0, str(PROJECT_ROOT))

from training.gui.main_window import main as run_gui

if __name__ == "__main__":
    run_gui()
