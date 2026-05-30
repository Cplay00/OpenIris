# -*- mode: python ; coding: utf-8 -*-
"""
PyInstaller spec file for OpenIris Training GUI

Build command:
    pyinstaller training/build_gui.spec

Output: dist/OpenIrisTraining/OpenIrisTraining.exe
"""

import sys
from pathlib import Path

block_cipher = None
project_root = Path(SPECPATH).parent

a = Analysis(
    [str(project_root / 'training' / 'run_gui.py')],
    pathex=[str(project_root)],
    binaries=[],
    datas=[
        (str(project_root / 'training' / 'configs'), 'training/configs'),
        (str(project_root / 'training' / 'scripts'), 'training/scripts'),
        (str(project_root / 'training' / 'tools'), 'training/tools'),
    ],
    hiddenimports=[
        'yaml',
        'tkinter',
        'tkinter.ttk',
        'tkinter.filedialog',
        'tkinter.messagebox',
        'tkinter.scrolledtext',
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
    console=False,  # Hide terminal window
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=None,
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
