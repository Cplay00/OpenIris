#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Convert Android PNG icon to Windows ICO format.
Uses only standard library + tkinter (no PIL needed).
"""

import struct
import sys
from pathlib import Path


def png_to_ico(png_path: str, ico_path: str, sizes=None):
    """
    Convert PNG to ICO using tkinter (no PIL required).
    Creates multi-resolution ICO from single PNG.
    """
    import tkinter as tk

    if sizes is None:
        sizes = [16, 32, 48, 64, 128, 256]

    root = tk.Tk()
    root.withdraw()

    # Use tkinter PhotoImage to read PNG
    try:
        img = tk.PhotoImage(file=png_path)
    except tk.TclError:
        print(f"Error: Cannot read PNG with tkinter: {png_path}")
        print("Falling back to simple copy method...")
        _simple_ico_convert(png_path, ico_path)
        root.destroy()
        return

    orig_w = img.width()
    orig_h = img.height()
    print(f"Source image: {orig_w}x{orig_h}")

    # Read PNG raw data
    with open(png_path, "rb") as f:
        png_data = f.read()

    # Create ICO with embedded PNG (supported since Vista)
    # ICO format: header + directory entries + embedded PNG data
    num_images = 1  # Just embed the original PNG

    # ICO Header: 6 bytes
    header = struct.pack("<HHH", 0, 1, num_images)

    # For sizes that need resizing, we embed the original PNG
    # Windows will scale it automatically
    # ICO Directory Entry: 16 bytes each
    width_byte = 0 if orig_w >= 256 else orig_w
    height_byte = 0 if orig_h >= 256 else orig_h

    png_size = len(png_data)
    entry_offset = 6 + 16  # header + 1 entry
    entry = struct.pack("<BBBBHHII",
                        width_byte, height_byte,
                        0, 0,  # color count, reserved
                        1, 32,  # planes, bits per pixel
                        png_size, entry_offset)

    with open(ico_path, "wb") as f:
        f.write(header)
        f.write(entry)
        f.write(png_data)

    print(f"ICO created: {ico_path} ({orig_w}x{orig_h}, {png_size} bytes)")
    root.destroy()


def _simple_ico_convert(png_path: str, ico_path: str):
    """Fallback: embed PNG directly in ICO container."""
    with open(png_path, "rb") as f:
        png_data = f.read()

    # Read PNG dimensions
    if png_data[0:8] != b'\x89PNG\r\n\x1a\n':
        print("Error: Not a valid PNG file")
        return

    w, h = struct.unpack(">II", png_data[16:24])
    print(f"Source image: {w}x{h}")

    width_byte = 0 if w >= 256 else w
    height_byte = 0 if h >= 256 else h

    num_images = 1
    header = struct.pack("<HHH", 0, 1, num_images)
    entry_offset = 6 + 16
    entry = struct.pack("<BBBBHHII",
                        width_byte, height_byte,
                        0, 0, 1, 32,
                        len(png_data), entry_offset)

    with open(ico_path, "wb") as f:
        f.write(header)
        f.write(entry)
        f.write(png_data)

    print(f"ICO created: {ico_path} ({w}x{h})")


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python convert_icon.py <input.png> <output.ico>")
        sys.exit(1)

    png_to_ico(sys.argv[1], sys.argv[2])
