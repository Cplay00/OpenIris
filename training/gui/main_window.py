#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Training GUI - Main Window

Modern tkinter-based YOLO training interface with:
- Tooltips for every parameter
- Full-width sliders with editable values
- Local model path selection
- Modern flat UI design
"""

import tkinter as tk
from tkinter import ttk, filedialog, messagebox, scrolledtext
import threading
import subprocess
import sys
import os
from pathlib import Path
import yaml
from datetime import datetime


# ============================================================
# Tooltip Helper
# ============================================================
class ToolTip:
    """Tooltip that appears on hover with delay."""
    def __init__(self, widget, text, delay=500):
        self.widget = widget
        self.text = text
        self.delay = delay
        self.tip_window = None
        self.after_id = None
        widget.bind("<Enter>", self._on_enter)
        widget.bind("<Leave>", self._on_leave)

    def _on_enter(self, event=None):
        self.after_id = self.widget.after(self.delay, self._show_tip)

    def _on_leave(self, event=None):
        if self.after_id:
            self.widget.after_cancel(self.after_id)
            self.after_id = None
        self._hide_tip()

    def _show_tip(self):
        if self.tip_window:
            return
        x = self.widget.winfo_rootx() + 20
        y = self.widget.winfo_rooty() + self.widget.winfo_height() + 5
        self.tip_window = tw = tk.Toplevel(self.widget)
        tw.wm_overrideredirect(True)
        tw.wm_geometry(f"+{x}+{y}")
        try:
            tw.wm_attributes("-topmost", True)
        except Exception:
            pass
        frame = tk.Frame(tw, background="#333333", padx=1, pady=1)
        frame.pack()
        label = tk.Label(
            frame, text=self.text, justify=tk.LEFT,
            background="#ffffdd", foreground="#333333",
            relief=tk.SOLID, borderwidth=1,
            font=("Segoe UI", 9), padx=8, pady=4,
            wraplength=360
        )
        label.pack()

    def _hide_tip(self):
        if self.tip_window:
            self.tip_window.destroy()
            self.tip_window = None


# ============================================================
# Tooltip descriptions for every parameter
# ============================================================
TOOLTIPS = {
    # Model
    "model": "Pretrained model weights file.\n"
             "- yolov11n.pt: Nano, fastest, for mobile\n"
             "- yolov11s.pt: Small, balanced\n"
             "- yolov11m.pt: Medium, higher accuracy\n"
             "For Android NCNN deploy, use yolov11n.pt.",
    "model_path": "Click 'Browse' to select a local .pt weight file.\n"
                  "You can also type the path directly.",
    "data": "Dataset config YAML file.\n"
            "Must contain: train/val paths, nc, names.\n"
            "Use dataset_custom.yaml as template.",
    "epochs": "Total training epochs.\n"
              "- Fast test: 30\n"
              "- Standard: 100\n"
              "- High precision: 200+\n"
              "More epochs = longer training time.",
    "batch": "Batch size (images per step).\n"
             "Depends on GPU VRAM:\n"
             "- 8GB GPU: 16-32\n"
             "- 12GB GPU: 32-48\n"
             "- 24GB GPU: 64-128\n"
             "Effective batch = batch * accumulate.",
    "imgsz": "Input image size in pixels.\n"
             "MUST be 640 for NCNN deployment.\n"
             "Larger = more accurate but slower.\n"
             "Options: 320, 416, 512, 640, 800, 1024.",
    "device": "Training device.\n"
              "- 0: First GPU\n"
              "- 0,1: Multi-GPU\n"
              "- cpu: CPU only (very slow)\n"
              "Auto-detected if available.",
    "name": "Experiment name for output folder.\n"
            "Results saved to: runs/train/<name>/\n"
            "Each run creates a subfolder.",
    # Optimizer
    "optimizer": "Optimization algorithm.\n"
                 "- SGD: Classic, good for large models\n"
                 "- Adam: Adaptive, fast convergence\n"
                 "- AdamW: Adam + weight decay (recommended)\n"
                 "- auto: Auto-select by Ultralytics",
    "lr0": "Initial learning rate.\n"
           "- SGD: 0.01\n"
           "- Adam/AdamW: 0.001\n"
           "Too high = divergence, too low = slow.",
    "weight_decay": "L2 regularization strength.\n"
                    "Prevents overfitting.\n"
                    "Range: 0.0001 ~ 0.001\n"
                    "Recommended: 0.0005.",
    # Regularization
    "patience": "Early stopping patience (epochs).\n"
                "Stop if no improvement for N epochs.\n"
                "0 = disabled.\n"
                "Recommended: 50-100.",
    "label_smoothing": "Softens one-hot labels to reduce overfitting.\n"
                       "Range: 0.0 ~ 0.1\n"
                       "0 = no smoothing (default)\n"
                       "Recommended: 0.01-0.05 for small datasets.",
    # Hardware
    "amp": "Automatic Mixed Precision training.\n"
           "Uses FP16 for speed, FP32 for stability.\n"
           "2-3x faster, 50% less VRAM.\n"
           "MUST enable for NCNN FP16 deployment.",
    "workers": "Data loading worker threads.\n"
               "Higher = faster data loading.\n"
               "Windows: 4 recommended\n"
               "Linux: 8-16\n"
               "0 = main process only.",
    # Augmentation
    "mosaic": "Mosaic augmentation probability.\n"
              "Combines 4 images into one.\n"
              "Great for multi-scale learning.\n"
              "1.0 = always, 0.0 = disabled.",
    "mixup": "Mixup augmentation probability.\n"
             "Blends two images and labels.\n"
             "Reduces overfitting on small datasets.\n"
             "0.0-0.3 recommended.",
    "fliplr": "Horizontal flip probability.\n"
              "0.5 = flip 50% of images.\n"
              "Good for most scenarios.\n"
              "Disable if left/right matters.",
    "degrees": "Random rotation range (degrees).\n"
               "0 = no rotation.\n"
               "10-15 for general use.\n"
               "Disable if orientation matters.",
    "scale": "Random zoom range (gain).\n"
             "0.5 = scale from 0.5x to 1.5x.\n"
             "Simulates distance variation.\n"
             "0.0-0.5 recommended.",
    "hsv_h": "HSV-Hue augmentation range.\n"
             "Shifts color hue randomly.\n"
             "Small values: 0.01-0.02\n"
             "Affects color perception.",
    "hsv_s": "HSV-Saturation augmentation range.\n"
             "Changes color intensity.\n"
             "0.5-0.7 for outdoor scenes.\n"
             "0.3-0.5 for indoor.",
    "hsv_v": "HSV-Value (brightness) augmentation range.\n"
             "Simulates lighting changes.\n"
             "0.3-0.5 recommended.\n"
             "Important for mobile camera.",
    "erasing": "Random erasing probability.\n"
               "Cuts random patches from image.\n"
               "Improves occlusion robustness.\n"
               "0.0-0.4 recommended.",
    "translate": "Random translation range (fraction).\n"
                 "Shifts image horizontally/vertically.\n"
                 "0.1 = 10% of image size.\n"
                 "0.0-0.2 recommended.",
    "shear": "Random shear intensity (degrees).\n"
             "Skews the image.\n"
             "0.0-10.0 recommended.\n"
             "Disable for rigid objects.",
}


# ============================================================
# Labeled Slider with Editable Value
# ============================================================
class LabeledSlider(tk.Frame):
    """Full-width slider with label on left and editable value on right."""
    def __init__(self, parent, label, from_=0.0, to=1.0, value=0.0,
                 resolution=0.01, tooltip=None, is_int=False, **kwargs):
        super().__init__(parent, **kwargs)
        self.is_int = is_int

        # Label (left)
        self.label = tk.Label(self, text=label, width=18, anchor="w",
                              font=("Segoe UI", 9))
        self.label.pack(side=tk.LEFT, padx=(0, 8))
        if tooltip:
            ToolTip(self.label, tooltip)

        # Value entry (right, editable)
        self.var = tk.DoubleVar(value=value) if not is_int else tk.IntVar(value=int(value))
        self.entry = tk.Entry(self, textvariable=self.var, width=8,
                              justify="center", font=("Segoe UI", 9),
                              relief=tk.FLAT, bg="#f0f0f0")
        self.entry.pack(side=tk.RIGHT, padx=(8, 0))
        self.entry.bind("<Return>", self._on_entry_change)
        self.entry.bind("<FocusOut>", self._on_entry_change)
        if tooltip:
            ToolTip(self.entry, tooltip)

        # Slider (fills remaining space)
        self.scale = tk.Scale(
            self, from_=from_, to=to, variable=self.var,
            orient=tk.HORIZONTAL, showvalue=False,
            resolution=resolution, sliderlength=20,
            length=300, font=("Segoe UI", 8)
        )
        self.scale.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=4)
        if tooltip:
            ToolTip(self.scale, tooltip)

    def _on_entry_change(self, event=None):
        """Sync slider when entry value changes."""
        try:
            val = self.var.get()
            if not self.is_int:
                self.var.set(round(val, 4))
        except (tk.TclError, ValueError):
            pass

    def get(self):
        return self.var.get()

    def set(self, value):
        self.var.set(value)


# ============================================================
# Modern Style Configuration
# ============================================================
def configure_modern_style():
    """Apply modern flat UI style to ttk."""
    style = ttk.Style()

    # Try clam theme first
    try:
        style.theme_use("clam")
    except Exception:
        pass

    # Colors
    BG = "#f5f6fa"
    FG = "#2d3436"
    ACCENT = "#0984e3"
    ACCENT_LIGHT = "#74b9ff"
    CARD_BG = "#ffffff"
    BORDER = "#dfe6e9"
    HEADER_BG = "#dfe6e9"

    # General
    style.configure(".", background=BG, foreground=FG, font=("Segoe UI", 9))
    style.configure("TFrame", background=BG)
    style.configure("TLabel", background=BG, foreground=FG)
    style.configure("TButton", padding=6, font=("Segoe UI", 9))
    style.map("TButton",
              background=[("active", ACCENT_LIGHT), ("!active", CARD_BG)],
              foreground=[("active", FG), ("!active", FG)])

    # Accent button
    style.configure("Accent.TButton", background=ACCENT, foreground="white",
                    font=("Segoe UI", 10, "bold"), padding=8)
    style.map("Accent.TButton",
              background=[("active", "#0769c4"), ("!active", ACCENT)],
              foreground=[("active", "white"), ("!active", "white")])

    # Danger button
    style.configure("Danger.TButton", background="#d63031", foreground="white",
                    font=("Segoe UI", 9), padding=6)
    style.map("Danger.TButton",
              background=[("active", "#b71c1c"), ("!active", "#d63031")],
              foreground=[("active", "white"), ("!active", "white")])

    # LabelFrame
    style.configure("TLabelframe", background=CARD_BG, foreground=FG,
                    borderwidth=1, relief="solid", bordercolor=BORDER)
    style.configure("TLabelframe.Label", background=CARD_BG, foreground=ACCENT,
                    font=("Segoe UI", 10, "bold"))

    # Notebook
    style.configure("TNotebook", background=BG, borderwidth=0)
    style.configure("TNotebook.Tab", padding=[16, 6], font=("Segoe UI", 9))
    style.map("TNotebook.Tab",
              background=[("selected", CARD_BG), ("!selected", HEADER_BG)],
              foreground=[("selected", ACCENT), ("!selected", FG)])

    # Entry
    style.configure("TEntry", padding=4, font=("Segoe UI", 9))

    # Combobox
    style.configure("TCombobox", padding=4, font=("Segoe UI", 9))

    # Progressbar
    style.configure("TProgressbar", troughcolor=BORDER, background=ACCENT,
                    thickness=12)

    # Checkbutton
    style.configure("TCheckbutton", background=BG, foreground=FG,
                    font=("Segoe UI", 9))

    # Scrollbar
    style.configure("Vertical.TScrollbar", troughcolor=BG, background=BORDER)

    return BG, CARD_BG, ACCENT, BORDER


# ============================================================
# Main GUI Class
# ============================================================
class TrainingGUI:
    """Modern YOLO Training GUI."""

    def __init__(self, root):
        self.root = root
        self.root.title("OpenIris YOLO Training Platform")
        self.root.geometry("1100x780")
        self.root.minsize(960, 680)

        # Apply modern style
        self.colors = configure_modern_style()

        # Process state
        self.train_process = None
        self.is_training = False

        # Paths
        self.project_root = Path(__file__).parent.parent.parent
        self.config_dir = self.project_root / "training" / "configs"

        # Build UI
        self._build_ui()
        self._load_defaults()

    # ----------------------------------------------------------
    # UI Construction
    # ----------------------------------------------------------
    def _build_ui(self):
        # Top bar
        top = ttk.Frame(self.root)
        top.pack(fill=tk.X, padx=12, pady=(10, 4))
        ttk.Label(top, text="OpenIris YOLO Training Platform",
                  font=("Segoe UI", 14, "bold"),
                  foreground=self.colors[2]).pack(side=tk.LEFT)
        ttk.Label(top, text="v2.0",
                  font=("Segoe UI", 9),
                  foreground="#636e72").pack(side=tk.LEFT, padx=(8, 0))

        # Menu
        self._build_menu()

        # Main paned layout
        main = ttk.PanedWindow(self.root, orient=tk.HORIZONTAL)
        main.pack(fill=tk.BOTH, expand=True, padx=12, pady=(4, 8))

        # Left: config panel
        left = ttk.Frame(main)
        main.add(left, weight=2)

        # Right: log + controls
        right = ttk.Frame(main)
        main.add(right, weight=3)

        self._build_config_panel(left)
        self._build_right_panel(right)

        # Status bar
        self.status_var = tk.StringVar(value="Ready")
        status_bar = ttk.Frame(self.root)
        status_bar.pack(fill=tk.X, side=tk.BOTTOM, padx=12, pady=(0, 6))
        ttk.Label(status_bar, textvariable=self.status_var,
                  font=("Segoe UI", 8), foreground="#636e72").pack(side=tk.LEFT)

    def _build_menu(self):
        menubar = tk.Menu(self.root)
        self.root.config(menu=menubar)

        file_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="File", menu=file_menu)
        file_menu.add_command(label="Load Config", command=self._load_config)
        file_menu.add_command(label="Save Config", command=self._save_config)
        file_menu.add_separator()
        file_menu.add_command(label="Exit", command=self.root.quit)

        dataset_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="Dataset", menu=dataset_menu)
        dataset_menu.add_command(label="Validate Dataset", command=self._validate_dataset)

        tools_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="Tools", menu=tools_menu)
        tools_menu.add_command(label="Export Model", command=self._export_model)
        tools_menu.add_command(label="Visualize Results", command=self._visualize_results)
        tools_menu.add_separator()
        tools_menu.add_command(label="Check Environment", command=self._check_environment)

        help_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="Help", menu=help_menu)
        help_menu.add_command(label="About", command=self._show_about)

    # ----------------------------------------------------------
    # Config Panel (Left)
    # ----------------------------------------------------------
    def _build_config_panel(self, parent):
        notebook = ttk.Notebook(parent)
        notebook.pack(fill=tk.BOTH, expand=True, padx=(0, 6))

        # Tab 1: Basic
        basic = ttk.Frame(notebook, padding=10)
        notebook.add(basic, text=" Basic ")
        self._build_basic_tab(basic)

        # Tab 2: Advanced
        adv = ttk.Frame(notebook, padding=10)
        notebook.add(adv, text=" Advanced ")
        self._build_advanced_tab(adv)

        # Tab 3: Augmentation
        aug = ttk.Frame(notebook, padding=10)
        notebook.add(aug, text=" Augmentation ")
        self._build_augment_tab(aug)

    def _build_basic_tab(self, parent):
        row = 0

        # -- Dataset --
        ttk.Label(parent, text="Dataset Config",
                  font=("Segoe UI", 10, "bold")).grid(
            row=row, column=0, columnspan=3, sticky=tk.W, pady=(0, 4))
        ToolTip(parent.grid_slaves(row=row, column=0)[0], TOOLTIPS["data"])
        row += 1

        self.data_var = tk.StringVar()
        e_data = ttk.Entry(parent, textvariable=self.data_var)
        e_data.grid(row=row, column=0, columnspan=2, sticky=tk.EW, pady=2)
        ToolTip(e_data, TOOLTIPS["data"])
        ttk.Button(parent, text="Browse", command=self._browse_data).grid(
            row=row, column=2, padx=(4, 0), pady=2)
        row += 1

        # -- Model --
        ttk.Label(parent, text="Pretrained Model",
                  font=("Segoe UI", 10, "bold")).grid(
            row=row, column=0, columnspan=3, sticky=tk.W, pady=(8, 4))
        row += 1

        self.model_var = tk.StringVar(value="yolo11n.pt")
        model_combo = ttk.Combobox(parent, textvariable=self.model_var, values=[
            "yolo11n.pt", "yolo11s.pt", "yolo11m.pt", "yolo11l.pt", "yolo11x.pt"
        ], state="normal", width=20)
        model_combo.grid(row=row, column=0, sticky=tk.EW, pady=2)
        ToolTip(model_combo, TOOLTIPS["model"])

        ttk.Button(parent, text="Browse", command=self._browse_model).grid(
            row=row, column=1, padx=(4, 0), pady=2)
        l_path_hint = ttk.Label(parent, text="or type local path",
                                font=("Segoe UI", 8), foreground="#636e72")
        l_path_hint.grid(row=row, column=2, padx=(4, 0), pady=2)
        ToolTip(l_path_hint, TOOLTIPS["model_path"])
        row += 1

        # -- Epochs, Batch, ImgSz --
        ttk.Label(parent, text="Training Parameters",
                  font=("Segoe UI", 10, "bold")).grid(
            row=row, column=0, columnspan=3, sticky=tk.W, pady=(8, 4))
        row += 1

        params = [
            ("Epochs", "epochs_var", 200, TOOLTIPS["epochs"]),
            ("Batch Size", "batch_var", 32, TOOLTIPS["batch"]),
            ("Image Size", "imgsz_var", 640, TOOLTIPS["imgsz"]),
        ]
        for label, varname, default, tip in params:
            lbl = ttk.Label(parent, text=label)
            lbl.grid(row=row, column=0, sticky=tk.W, pady=2)
            ToolTip(lbl, tip)
            var = tk.IntVar(value=default)
            setattr(self, varname, var)
            sp = ttk.Spinbox(parent, from_=1, to=2048, textvariable=var, width=10)
            sp.grid(row=row, column=1, columnspan=2, sticky=tk.W, pady=2)
            ToolTip(sp, tip)
            row += 1

        # -- Device, Name --
        lbl = ttk.Label(parent, text="Device")
        lbl.grid(row=row, column=0, sticky=tk.W, pady=2)
        ToolTip(lbl, TOOLTIPS["device"])
        self.device_var = tk.StringVar(value="0")
        cb = ttk.Combobox(parent, textvariable=self.device_var,
                          values=["0", "0,1", "cpu"], state="readonly", width=10)
        cb.grid(row=row, column=1, columnspan=2, sticky=tk.W, pady=2)
        ToolTip(cb, TOOLTIPS["device"])
        row += 1

        lbl = ttk.Label(parent, text="Experiment Name")
        lbl.grid(row=row, column=0, sticky=tk.W, pady=2)
        ToolTip(lbl, TOOLTIPS["name"])
        self.name_var = tk.StringVar(value="openiris_v1")
        e_name = ttk.Entry(parent, textvariable=self.name_var)
        e_name.grid(row=row, column=1, columnspan=2, sticky=tk.EW, pady=2)
        ToolTip(e_name, TOOLTIPS["name"])
        row += 1

        parent.columnconfigure(1, weight=1)

    def _build_advanced_tab(self, parent):
        row = 0

        # -- Optimizer --
        ttk.Label(parent, text="Optimizer",
                  font=("Segoe UI", 10, "bold")).grid(
            row=row, column=0, columnspan=2, sticky=tk.W, pady=(0, 4))
        row += 1

        lbl = ttk.Label(parent, text="Algorithm")
        lbl.grid(row=row, column=0, sticky=tk.W, pady=2)
        ToolTip(lbl, TOOLTIPS["optimizer"])
        self.optimizer_var = tk.StringVar(value="AdamW")
        cb = ttk.Combobox(parent, textvariable=self.optimizer_var,
                          values=["SGD", "Adam", "AdamW", "auto"],
                          state="readonly", width=12)
        cb.grid(row=row, column=1, sticky=tk.W, pady=2)
        ToolTip(cb, TOOLTIPS["optimizer"])
        row += 1

        for label, varname, default, tip in [
            ("Learning Rate", "lr_var", 0.001, TOOLTIPS["lr0"]),
            ("Weight Decay", "wd_var", 0.0005, TOOLTIPS["weight_decay"]),
            ("Label Smoothing", "smooth_var", 0.02, TOOLTIPS["label_smoothing"]),
        ]:
            lbl = ttk.Label(parent, text=label)
            lbl.grid(row=row, column=0, sticky=tk.W, pady=2)
            ToolTip(lbl, tip)
            var = tk.DoubleVar(value=default)
            setattr(self, varname, var)
            e = ttk.Entry(parent, textvariable=var, width=12)
            e.grid(row=row, column=1, sticky=tk.W, pady=2)
            ToolTip(e, tip)
            row += 1

        # -- Patience --
        lbl = ttk.Label(parent, text="Early Stop Patience")
        lbl.grid(row=row, column=0, sticky=tk.W, pady=2)
        ToolTip(lbl, TOOLTIPS["patience"])
        self.patience_var = tk.IntVar(value=50)
        sp = ttk.Spinbox(parent, from_=0, to=500, textvariable=self.patience_var, width=10)
        sp.grid(row=row, column=1, sticky=tk.W, pady=2)
        ToolTip(sp, TOOLTIPS["patience"])
        row += 1

        # -- Checkboxes --
        self.amp_var = tk.BooleanVar(value=True)
        chk = ttk.Checkbutton(parent, text="AMP (Mixed Precision)", variable=self.amp_var)
        chk.grid(row=row, column=0, columnspan=2, sticky=tk.W, pady=4)
        ToolTip(chk, TOOLTIPS["amp"])
        row += 1

        # -- Workers --
        lbl = ttk.Label(parent, text="Workers")
        lbl.grid(row=row, column=0, sticky=tk.W, pady=2)
        ToolTip(lbl, TOOLTIPS["workers"])
        self.workers_var = tk.IntVar(value=4)
        sp = ttk.Spinbox(parent, from_=0, to=32, textvariable=self.workers_var, width=10)
        sp.grid(row=row, column=1, sticky=tk.W, pady=2)
        ToolTip(sp, TOOLTIPS["workers"])
        row += 1

        parent.columnconfigure(1, weight=1)

    def _build_augment_tab(self, parent):
        """Full-width sliders with editable values."""
        row = 0

        ttk.Label(parent, text="Data Augmentation",
                  font=("Segoe UI", 10, "bold")).grid(
            row=row, column=0, columnspan=2, sticky=tk.W, pady=(0, 6))
        row += 1

        # Full-width sliders: (label, varname, from_, to, default, resolution, tooltip)
        sliders = [
            ("Mosaic",       "mosaic_var",   0, 1, 1.0,   0.05, TOOLTIPS["mosaic"]),
            ("Mixup",        "mixup_var",    0, 1, 0.0,   0.05, TOOLTIPS["mixup"]),
            ("Flip L-R",     "fliplr_var",   0, 1, 0.5,   0.05, TOOLTIPS["fliplr"]),
            ("Rotation",     "degrees_var",  0, 45, 0.0,  1.0,  TOOLTIPS["degrees"]),
            ("Scale",        "scale_var",    0, 1, 0.5,   0.05, TOOLTIPS["scale"]),
            ("Translate",    "trans_var",    0, 0.5, 0.1, 0.01, TOOLTIPS["translate"]),
            ("Shear",        "shear_var",    0, 30, 0.0,  1.0,  TOOLTIPS["shear"]),
            ("HSV-Hue",      "hsv_h_var",    0, 0.1, 0.015, 0.001, TOOLTIPS["hsv_h"]),
            ("HSV-Sat",      "hsv_s_var",    0, 1, 0.7,   0.05, TOOLTIPS["hsv_s"]),
            ("HSV-Val",      "hsv_v_var",    0, 1, 0.4,   0.05, TOOLTIPS["hsv_v"]),
            ("Random Erase", "erasing_var",  0, 1, 0.0,   0.05, TOOLTIPS["erasing"]),
        ]

        # Scrollable frame for sliders
        canvas = tk.Canvas(parent, highlightthickness=0)
        scrollbar = ttk.Scrollbar(parent, orient="vertical", command=canvas.yview)
        slider_frame = ttk.Frame(canvas)
        slider_frame.bind("<Configure>",
                          lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.create_window((0, 0), window=slider_frame, anchor="nw")
        canvas.configure(yscrollcommand=scrollbar.set)

        canvas.grid(row=row, column=0, sticky="nsew")
        scrollbar.grid(row=row, column=1, sticky="ns")
        parent.rowconfigure(row, weight=1)
        parent.columnconfigure(0, weight=1)

        # Mouse wheel scrolling
        def _on_mousewheel(event):
            canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")
        canvas.bind_all("<MouseWheel>", _on_mousewheel)

        for label, varname, from_, to, default, res, tip in sliders:
            sl = LabeledSlider(
                slider_frame, label=label,
                from_=from_, to=to, value=default,
                resolution=res, tooltip=tip
            )
            sl.pack(fill=tk.X, padx=4, pady=3)
            setattr(self, varname, sl.var)

    # ----------------------------------------------------------
    # Right Panel (Controls + Log)
    # ----------------------------------------------------------
    def _build_right_panel(self, parent):
        # Control frame
        ctrl = ttk.LabelFrame(parent, text="Training Control", padding=10)
        ctrl.pack(fill=tk.X, padx=(6, 0), pady=(0, 6))

        btn_row = ttk.Frame(ctrl)
        btn_row.pack(fill=tk.X)

        self.btn_start = ttk.Button(btn_row, text="Start Training",
                                     style="Accent.TButton",
                                     command=self._start_training)
        self.btn_start.pack(side=tk.LEFT, padx=(0, 6))

        self.btn_stop = ttk.Button(btn_row, text="Stop",
                                    style="Danger.TButton",
                                    command=self._stop_training, state=tk.DISABLED)
        self.btn_stop.pack(side=tk.LEFT, padx=(0, 6))

        ttk.Button(btn_row, text="Validate",
                   command=self._validate_dataset).pack(side=tk.LEFT, padx=(0, 6))
        ttk.Button(btn_row, text="Export",
                   command=self._export_model).pack(side=tk.LEFT)

        # Progress
        self.progress_var = tk.DoubleVar(value=0)
        pb = ttk.Progressbar(ctrl, variable=self.progress_var, maximum=100)
        pb.pack(fill=tk.X, pady=(8, 0))

        self.status_label_var = tk.StringVar(value="Ready to train")
        ttk.Label(ctrl, textvariable=self.status_label_var,
                  font=("Segoe UI", 9)).pack(anchor=tk.W, pady=(4, 0))

        # Log frame
        log_frame = ttk.LabelFrame(parent, text="Training Log", padding=10)
        log_frame.pack(fill=tk.BOTH, expand=True, padx=(6, 0))

        self.log_text = scrolledtext.ScrolledText(
            log_frame, height=15, state=tk.DISABLED,
            font=("Consolas", 9), wrap=tk.WORD,
            bg="#1e1e1e", fg="#d4d4d4",
            insertbackground="white",
            selectbackground="#264f78"
        )
        self.log_text.pack(fill=tk.BOTH, expand=True)

        ttk.Button(log_frame, text="Clear Log",
                   command=self._clear_log).pack(anchor=tk.E, pady=(4, 0))

    # ----------------------------------------------------------
    # File Dialogs
    # ----------------------------------------------------------
    def _browse_data(self):
        f = filedialog.askopenfilename(
            title="Select Dataset Config",
            filetypes=[("YAML", "*.yaml"), ("All", "*.*")],
            initialdir=str(self.config_dir)
        )
        if f:
            self.data_var.set(f)

    def _browse_model(self):
        f = filedialog.askopenfilename(
            title="Select Pretrained Model",
            filetypes=[("PyTorch", "*.pt"), ("All", "*.*")],
            initialdir=str(self.project_root)
        )
        if f:
            self.model_var.set(f)

    def _load_config(self):
        f = filedialog.askopenfilename(
            title="Load Config",
            filetypes=[("YAML", "*.yaml"), ("All", "*.*")],
            initialdir=str(self.config_dir)
        )
        if f:
            try:
                with open(f, "r", encoding="utf-8") as fh:
                    cfg = yaml.safe_load(fh)
                self._apply_config(cfg)
                self._log(f"Config loaded: {f}")
            except Exception as e:
                messagebox.showerror("Error", f"Failed to load: {e}")

    def _save_config(self):
        f = filedialog.asksaveasfilename(
            title="Save Config",
            defaultextension=".yaml",
            filetypes=[("YAML", "*.yaml"), ("All", "*.*")],
            initialdir=str(self.config_dir)
        )
        if f:
            cfg = self._get_config()
            with open(f, "w", encoding="utf-8") as fh:
                yaml.dump(cfg, fh, allow_unicode=True, default_flow_style=False)
            self._log(f"Config saved: {f}")

    # ----------------------------------------------------------
    # Config helpers
    # ----------------------------------------------------------
    def _load_defaults(self):
        hyp = self.config_dir / "hyp_train.yaml"
        if hyp.exists():
            try:
                with open(hyp, "r", encoding="utf-8") as f:
                    self._apply_config(yaml.safe_load(f))
            except Exception:
                pass

    def _apply_config(self, cfg):
        mapping = {
            "model": self.model_var, "epochs": self.epochs_var,
            "batch": self.batch_var, "imgsz": self.imgsz_var,
            "optimizer": self.optimizer_var, "lr0": self.lr_var,
            "weight_decay": self.wd_var, "patience": self.patience_var,
            "amp": self.amp_var, "label_smoothing": self.smooth_var,
            "mosaic": self.mosaic_var, "mixup": self.mixup_var,
            "fliplr": self.fliplr_var, "degrees": self.degrees_var,
            "scale": self.scale_var, "hsv_s": self.hsv_s_var,
            "hsv_v": self.hsv_v_var, "erasing": self.erasing_var,
            "name": self.name_var, "device": self.device_var,
            "workers": self.workers_var, "data": self.data_var,
        }
        for key, var in mapping.items():
            if key in cfg:
                try:
                    var.set(cfg[key])
                except Exception:
                    pass

    def _get_config(self):
        return {
            "model": self.model_var.get(), "data": self.data_var.get(),
            "epochs": self.epochs_var.get(), "batch": self.batch_var.get(),
            "imgsz": self.imgsz_var.get(), "device": self.device_var.get(),
            "optimizer": self.optimizer_var.get(), "lr0": self.lr_var.get(),
            "weight_decay": self.wd_var.get(), "patience": self.patience_var.get(),
            "amp": self.amp_var.get(), "label_smoothing": self.smooth_var.get(),
            "workers": self.workers_var.get(), "name": self.name_var.get(),
            "mosaic": self.mosaic_var.get(), "mixup": self.mixup_var.get(),
            "fliplr": self.fliplr_var.get(), "degrees": self.degrees_var.get(),
            "scale": self.scale_var.get(), "hsv_s": self.hsv_s_var.get(),
            "hsv_v": self.hsv_v_var.get(), "erasing": self.erasing_var.get(),
            "project": "runs/train",
        }

    # ----------------------------------------------------------
    # Training
    # ----------------------------------------------------------
    def _start_training(self):
        if not self.data_var.get():
            messagebox.showerror("Error", "Please select a dataset config file.")
            return
        if self.is_training:
            return

        cfg = self._get_config()
        cmd = [
            sys.executable,
            str(self.project_root / "training" / "scripts" / "train.py"),
            "--data", cfg["data"],
            "--model", cfg["model"],
            "--epochs", str(cfg["epochs"]),
            "--batch", str(cfg["batch"]),
            "--imgsz", str(cfg["imgsz"]),
            "--device", str(cfg["device"]),
            "--name", cfg["name"],
        ]

        self._log("=" * 50)
        self._log("Starting training...")
        self._log(f"Command: {' '.join(cmd)}")
        self._log("=" * 50)

        self.is_training = True
        self.btn_start.config(state=tk.DISABLED)
        self.btn_stop.config(state=tk.NORMAL)
        self.status_label_var.set("Training in progress...")

        threading.Thread(target=self._run_process, args=(cmd,), daemon=True).start()

    def _run_process(self, cmd):
        try:
            self.train_process = subprocess.Popen(
                cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                universal_newlines=True, bufsize=1,
                creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0
            )
            for line in self.train_process.stdout:
                self.root.after(0, self._log, line.strip())
            self.train_process.wait()
            rc = self.train_process.returncode
            if rc == 0:
                self.root.after(0, self._log, "\nTraining complete!")
                self.root.after(0, self.status_label_var.set, "Training complete")
            else:
                self.root.after(0, self._log, f"\nTraining failed (exit code {rc})")
                self.root.after(0, self.status_label_var.set, "Training failed")
        except Exception as e:
            self.root.after(0, self._log, f"\nError: {e}")
        finally:
            self.root.after(0, self._training_done)

    def _training_done(self):
        self.is_training = False
        self.btn_start.config(state=tk.NORMAL)
        self.btn_stop.config(state=tk.DISABLED)
        self.train_process = None

    def _stop_training(self):
        if self.train_process and self.is_training:
            if messagebox.askyesno("Confirm", "Stop training?"):
                self.train_process.terminate()
                self._log("Training stopped by user")
                self.status_label_var.set("Stopped")

    # ----------------------------------------------------------
    # Dataset / Export / Viz
    # ----------------------------------------------------------
    def _validate_dataset(self):
        if not self.data_var.get():
            messagebox.showerror("Error", "Please select a dataset config first.")
            return
        cmd = [sys.executable,
               str(self.project_root / "training" / "scripts" / "dataset_validator.py"),
               "--data", self.data_var.get()]
        self._log("Validating dataset...")
        threading.Thread(target=self._run_process, args=(cmd,), daemon=True).start()

    def _export_model(self):
        weights = filedialog.askopenfilename(
            title="Select Model Weights",
            filetypes=[("PyTorch", "*.pt"), ("All", "*.*")],
            initialdir=str(self.project_root / "runs" / "train")
        )
        if not weights:
            return
        assets = filedialog.askdirectory(
            title="Select Android assets directory",
            initialdir=str(self.project_root / "ncnn-android-yolov11" /
                           "app" / "src" / "main" / "assets" / "models")
        )
        if assets:
            cmd = [sys.executable,
                   str(self.project_root / "training" / "tools" / "export_pipeline.py"),
                   "--weights", weights, "--assets", assets]
            self._log("Exporting model...")
            threading.Thread(target=self._run_process, args=(cmd,), daemon=True).start()

    def _visualize_results(self):
        d = filedialog.askdirectory(
            title="Select Training Run Directory",
            initialdir=str(self.project_root / "runs" / "train")
        )
        if d:
            cmd = [sys.executable,
                   str(self.project_root / "training" / "tools" / "visualize_results.py"),
                   "--run-dir", d]
            self._log("Generating visualizations...")
            threading.Thread(target=self._run_process, args=(cmd,), daemon=True).start()

    def _check_environment(self):
        self._log("Checking environment...")
        checks = [f"Python: {sys.version}"]
        for name in ["torch", "ultralytics", "cv2"]:
            try:
                m = __import__(name)
                checks.append(f"{name}: {getattr(m, '__version__', 'installed')}")
            except ImportError:
                checks.append(f"{name}: NOT INSTALLED")
        try:
            import torch
            if torch.cuda.is_available():
                checks.append(f"GPU: {torch.cuda.get_device_name(0)}")
                checks.append(f"CUDA: {torch.version.cuda}")
            else:
                checks.append("GPU: Not available")
        except Exception:
            pass
        self._log("\n" + "\n".join(checks))

    def _show_about(self):
        messagebox.showinfo("About",
            "OpenIris YOLO Training Platform\n\n"
            "Version: 2.0\n"
            "Based on: Ultralytics YOLOv11\n"
            "Target: Android NCNN Deployment\n\n"
            "(c) 2024 OpenIris Team")

    # ----------------------------------------------------------
    # Logging
    # ----------------------------------------------------------
    def _log(self, msg):
        self.log_text.config(state=tk.NORMAL)
        ts = datetime.now().strftime("%H:%M:%S")
        self.log_text.insert(tk.END, f"[{ts}] {msg}\n")
        self.log_text.see(tk.END)
        self.log_text.config(state=tk.DISABLED)

    def _clear_log(self):
        self.log_text.config(state=tk.NORMAL)
        self.log_text.delete("1.0", tk.END)
        self.log_text.config(state=tk.DISABLED)


# ============================================================
# Entry Point
# ============================================================
def main():
    root = tk.Tk()

    # Try to set DPI awareness on Windows
    try:
        from ctypes import windll
        windll.shcore.SetProcessDpiAwareness(1)
    except Exception:
        pass

    app = TrainingGUI(root)
    root.mainloop()


if __name__ == "__main__":
    main()
