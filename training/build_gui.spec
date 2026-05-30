# -*- mode: python ; coding: utf-8 -*-
"""
PyInstaller spec file for OpenIris Training GUI
Output: training/dist/OpenIrisTraining/OpenIrisTraining.exe
"""

import sys
from pathlib import Path

block_cipher = None
# SPECPATH = training/ folder when spec is in training/
training_dir = Path(SPECPATH)
project_root = training_dir.parent

a = Analysis(
    [str(training_dir / 'run_gui.py')],
    pathex=[str(project_root)],
    binaries=[],
    datas=[
        (str(training_dir / 'configs'), 'training/configs'),
        (str(training_dir / 'scripts'), 'training/scripts'),
        (str(training_dir / 'tools'), 'training/tools'),
        (str(training_dir / 'gui' / 'openiris.ico'), 'training/gui'),
    ],
    hiddenimports=[
        'yaml', 'tkinter', 'tkinter.ttk', 'tkinter.filedialog',
        'tkinter.messagebox', 'tkinter.scrolledtext',
        'training', 'training.gui', 'training.gui.i18n',
        'training.gui.main_window',
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[
        'matplotlib', 'numpy', 'torch', 'torchvision',
        'ultralytics', 'cv2', 'PIL', 'scipy',
    ],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name='OpenIrisTraining',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,  # Hide terminal
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=str(training_dir / 'gui' / 'openiris.ico'),
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='OpenIrisTraining',
)
