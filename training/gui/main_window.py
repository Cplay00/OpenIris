#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Training GUI - Main Window (i18n: zh_CN default, en_US fallback)
Modern tkinter-based YOLO training interface.
Handles both normal Python and PyInstaller packaged exe execution.
"""

import tkinter as tk
from tkinter import ttk, filedialog, messagebox, scrolledtext
import threading
import subprocess
import sys
import os
import re
import ctypes
from pathlib import Path
import yaml
from datetime import datetime

from training.gui.i18n import t, set_language, get_language, get_available_languages

# Detect if running as PyInstaller packaged exe
IS_PACKAGED = getattr(sys, 'frozen', False) and hasattr(sys, '_MEIPASS')


# ============================================================
# DPI Awareness (must be called before Tk())
# ============================================================
def setup_dpi():
    """Set DPI awareness for Windows to match system scaling."""
    if sys.platform == "win32":
        try:
            from ctypes import windll
            # Try Per-Monitor DPI awareness (Windows 10+)
            try:
                windll.shcore.SetProcessDpiAwareness(2)
            except Exception:
                try:
                    windll.shcore.SetProcessDpiAwareness(1)
                except Exception:
                    windll.user32.SetProcessDPIAware()
        except Exception:
            pass


# ============================================================
# Find Python executable for subprocess
# ============================================================
def find_python():
    """Find a usable Python interpreter."""
    # If running as normal Python script
    if not IS_PACKAGED:
        return sys.executable

    # If packaged, look for Python in _internal or system
    exe_dir = Path(sys.executable).parent

    # Check for embedded Python in _internal
    for candidate in [
        exe_dir / "_internal" / "python.exe",
        exe_dir / "_internal" / "python3.exe",
        exe_dir / "python.exe",
    ]:
        if candidate.exists():
            return str(candidate)

    # Fallback: try system Python
    import shutil
    system_python = shutil.which("python")
    if system_python:
        return system_python

    return None


# ============================================================
# Direct training function (for packaged exe)
# ============================================================
def run_training_direct(config, log_callback=None):
    """
    Run training directly using ultralytics API.
    Used when running as packaged exe (no subprocess needed).
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        msg = "ultralytics not installed. Please install: pip install ultralytics"
        if log_callback:
            log_callback(msg)
        return False, msg

    try:
        model_path = config.get("model", "yolo11n.pt")
        if log_callback:
            log_callback(f"Loading model: {model_path}")

        model = YOLO(model_path)

        train_args = {
            "data": config["data"],
            "epochs": config.get("epochs", 200),
            "batch": config.get("batch", 32),
            "imgsz": config.get("imgsz", 640),
            "device": config.get("device", 0),
            "optimizer": config.get("optimizer", "AdamW"),
            "lr0": config.get("lr0", 0.001),
            "weight_decay": config.get("weight_decay", 0.0005),
            "patience": config.get("patience", 50),
            "amp": config.get("amp", True),
            "workers": config.get("workers", 4),
            "label_smoothing": config.get("label_smoothing", 0.02),
            "project": config.get("project", "runs/train"),
            "name": config.get("name", "openiris"),
            "exist_ok": True,
            "pretrained": True,
            "save": True,
            "save_period": 10,
            # Auto-adapt image sizes (handles non-standard resolutions)
            "rect": True,  # Rectangular training (adapts to aspect ratio)
            "cos_lr": True,  # Cosine LR schedule
        }

        # Add augmentation params if present
        aug_keys = ["mosaic", "mixup", "fliplr", "degrees", "scale",
                     "hsv_h", "hsv_s", "hsv_v", "erasing"]
        for k in aug_keys:
            if k in config:
                train_args[k] = config[k]

        if log_callback:
            log_callback(f"Starting training with rect=True (auto-adapt image sizes)")
            log_callback(f"Config: epochs={train_args['epochs']}, batch={train_args['batch']}, "
                        f"imgsz={train_args['imgsz']}, device={train_args['device']}")

        results = model.train(**train_args)

        if log_callback:
            best_path = f"{train_args['project']}/{train_args['name']}/weights/best.pt"
            log_callback(f"Training complete! Best weights: {best_path}")

        return True, "Training complete"

    except Exception as e:
        error_msg = f"Training failed: {e}"
        if log_callback:
            log_callback(error_msg)
        return False, error_msg


# ============================================================
# Tooltip
# ============================================================
class ToolTip:
    def __init__(self, widget, text, delay=500):
        self.widget = widget
        self._text = text
        self.delay = delay
        self.tw = None
        self._after = None
        widget.bind("<Enter>", self._enter)
        widget.bind("<Leave>", self._leave)

    def text(self, new_text):
        self._text = new_text

    def _enter(self, e=None):
        self._after = self.widget.after(self.delay, self._show)

    def _leave(self, e=None):
        if self._after:
            self.widget.after_cancel(self._after)
            self._after = None
        self._hide()

    def _show(self):
        if self.tw:
            return
        x = self.widget.winfo_rootx() + 20
        y = self.widget.winfo_rooty() + self.widget.winfo_height() + 5
        self.tw = tw = tk.Toplevel(self.widget)
        tw.wm_overrideredirect(True)
        tw.wm_geometry(f"+{x}+{y}")
        try:
            tw.wm_attributes("-topmost", True)
        except Exception:
            pass
        f = tk.Frame(tw, background="#333", padx=1, pady=1)
        f.pack()
        display_text = self._text
        try:
            tk.Label(f, text=display_text, justify=tk.LEFT,
                     background="#ffffdd", foreground="#333",
                     relief=tk.SOLID, borderwidth=1,
                     font=("Segoe UI", 9), padx=8, pady=4,
                     wraplength=360).pack()
        except (UnicodeEncodeError, UnicodeDecodeError):
            safe_text = display_text.encode("utf-8", errors="replace").decode("utf-8", errors="replace")
            tk.Label(f, text=safe_text, justify=tk.LEFT,
                     background="#ffffdd", foreground="#333",
                     relief=tk.SOLID, borderwidth=1,
                     font=("Segoe UI", 9), padx=8, pady=4,
                     wraplength=360).pack()

    def _hide(self):
        if self.tw:
            self.tw.destroy()
            self.tw = None


# ============================================================
# Labeled Slider (full-width + editable value)
# ============================================================
class LabeledSlider(tk.Frame):
    def __init__(self, parent, label, from_=0.0, to=1.0, value=0.0,
                 resolution=0.01, tooltip=None, is_int=False, **kw):
        super().__init__(parent, **kw)
        self.is_int = is_int
        self.lbl = tk.Label(self, text=label, width=12, anchor="w", font=("Segoe UI", 9))
        self.lbl.pack(side=tk.LEFT, padx=(0, 4))
        if tooltip:
            ToolTip(self.lbl, tooltip)
        self.var = tk.IntVar(value=int(value)) if is_int else tk.DoubleVar(value=value)
        self.entry = tk.Entry(self, textvariable=self.var, width=6,
                              justify="center", font=("Segoe UI", 9), relief=tk.FLAT, bg="#f0f0f0")
        self.entry.pack(side=tk.RIGHT, padx=(8, 0))
        self.entry.bind("<Return>", self._sync)
        self.entry.bind("<FocusOut>", self._sync)
        if tooltip:
            ToolTip(self.entry, tooltip)
        self.scale = tk.Scale(self, from_=from_, to=to, variable=self.var,
                              orient=tk.HORIZONTAL, showvalue=False,
                              resolution=resolution, sliderlength=20, font=("Segoe UI", 8))
        self.scale.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=4)
        if tooltip:
            ToolTip(self.scale, tooltip)

    def _sync(self, e=None):
        try:
            v = self.var.get()
            if not self.is_int:
                self.var.set(round(v, 4))
        except (tk.TclError, ValueError):
            pass

    def get(self): return self.var.get()
    def set(self, v): self.var.set(v)


# ============================================================
# Modern Style
# ============================================================
def apply_modern_style():
    s = ttk.Style()
    try: s.theme_use("clam")
    except Exception: pass
    BG, FG, ACCENT, CARD = "#f5f6fa", "#2d3436", "#0984e3", "#ffffff"
    BORDER = "#dfe6e9"
    s.configure(".", background=BG, foreground=FG, font=("Segoe UI", 9))
    s.configure("TFrame", background=BG)
    s.configure("TLabel", background=BG, foreground=FG)
    s.configure("TButton", padding=6, font=("Segoe UI", 9))
    s.map("TButton", background=[("active", "#74b9ff"), ("!active", CARD)])
    s.configure("Accent.TButton", background=ACCENT, foreground="white",
                font=("Segoe UI", 10, "bold"), padding=8)
    s.map("Accent.TButton", background=[("active", "#0769c4"), ("!active", ACCENT)],
          foreground=[("active", "white"), ("!active", "white")])
    s.configure("Danger.TButton", background="#d63031", foreground="white",
                font=("Segoe UI", 9), padding=6)
    s.map("Danger.TButton", background=[("active", "#b71c1c"), ("!active", "#d63031")],
          foreground=[("active", "white"), ("!active", "white")])
    s.configure("TLabelframe", background=CARD, foreground=FG, borderwidth=1, relief="solid", bordercolor=BORDER)
    s.configure("TLabelframe.Label", background=CARD, foreground=ACCENT, font=("Segoe UI", 10, "bold"))
    s.configure("TNotebook", background=BG, borderwidth=0)
    s.configure("TNotebook.Tab", padding=[16, 6], font=("Segoe UI", 9))
    s.map("TNotebook.Tab", background=[("selected", CARD), ("!selected", BORDER)],
          foreground=[("selected", ACCENT), ("!selected", FG)])
    s.configure("TProgressbar", troughcolor=BORDER, background=ACCENT, thickness=12)
    s.configure("TCheckbutton", background=BG, foreground=FG, font=("Segoe UI", 9))
    return BG, CARD, ACCENT, BORDER


# ============================================================
# Main GUI
# ============================================================
class TrainingGUI:
    def __init__(self, root):
        self.root = root
        self.colors = apply_modern_style()
        self.root.title(t("app_title"))

        # Scale window size based on DPI
        self.root.geometry("1100x780")
        self.root.minsize(960, 680)

        self.train_proc = None
        self.is_training = False
        self._stop_event = threading.Event()
        self._proc_lock = threading.Lock()
        self.project_root = self._detect_project_root()
        self.config_dir = self.project_root / "training" / "configs"
        self._tooltips = {}

        self._build_ui()
        self._load_defaults()

        # Set icon
        ico = Path(__file__).parent / "openiris.ico"
        if ico.exists():
            try: self.root.iconbitmap(str(ico))
            except Exception: pass

        # Log execution mode
        mode = "Packaged EXE" if IS_PACKAGED else "Python Script"
        self._log(f"Mode: {mode}")
        self._log(f"Project root: {self.project_root}")

    def _detect_project_root(self):
        """Detect project root for both packaged and script modes."""
        if IS_PACKAGED:
            # Packaged: project root is where the exe is
            return Path(sys.executable).parent
        else:
            # Script: project root is 3 levels up from gui/main_window.py
            return Path(__file__).parent.parent.parent

    # ---- UI ----
    def _build_ui(self):
        top = ttk.Frame(self.root)
        top.pack(fill=tk.X, padx=12, pady=(10, 4))
        ttk.Label(top, text=t("app_title"), font=("Segoe UI", 14, "bold"),
                  foreground=self.colors[2]).pack(side=tk.LEFT)
        ttk.Label(top, text=f"{t('version')} 2.1", font=("Segoe UI", 9),
                  foreground="#636e72").pack(side=tk.LEFT, padx=(8, 0))

        lang_frame = ttk.Frame(top)
        lang_frame.pack(side=tk.RIGHT)
        ttk.Label(lang_frame, text="Language:", font=("Segoe UI", 9)).pack(side=tk.LEFT)
        self.lang_var = tk.StringVar(value=get_language())
        lang_cb = ttk.Combobox(lang_frame, textvariable=self.lang_var,
                               values=list(get_available_languages().keys()), state="readonly", width=6)
        lang_cb.pack(side=tk.LEFT, padx=(4, 0))
        lang_cb.bind("<<ComboboxSelected>>", self._switch_language)
        ToolTip(lang_cb, "zh_CN: 绠€浣撲腑鏂嘰nen_US: English")

        self._build_menu()

        main = ttk.PanedWindow(self.root, orient=tk.HORIZONTAL)
        main.pack(fill=tk.BOTH, expand=True, padx=12, pady=(4, 8))
        left, right = ttk.Frame(main), ttk.Frame(main)
        main.add(left, weight=2)
        main.add(right, weight=3)
        self._build_config(left)
        self._build_right(right)

        self.status_var = tk.StringVar(value=t("status_ready"))
        sb = ttk.Frame(self.root)
        sb.pack(fill=tk.X, side=tk.BOTTOM, padx=12, pady=(0, 6))
        ttk.Label(sb, textvariable=self.status_var, font=("Segoe UI", 8), foreground="#636e72").pack(side=tk.LEFT)
        ttk.Label(sb, text="OpenIris v2.1", font=("Segoe UI", 8), foreground="#b2bec3").pack(side=tk.RIGHT)

    def _build_menu(self):
        mb = tk.Menu(self.root)
        self.root.config(menu=mb)
        fm = tk.Menu(mb, tearoff=0)
        mb.add_cascade(label=t("menu_file"), menu=fm)
        fm.add_command(label=t("menu_load_config"), command=self._load_config)
        fm.add_command(label=t("menu_save_config"), command=self._save_config)
        fm.add_separator()
        fm.add_command(label=t("menu_exit"), command=self.root.quit)
        dm = tk.Menu(mb, tearoff=0)
        mb.add_cascade(label=t("menu_dataset"), menu=dm)
        dm.add_command(label=t("menu_validate"), command=self._validate_dataset)
        tm = tk.Menu(mb, tearoff=0)
        mb.add_cascade(label=t("menu_tools"), menu=tm)
        tm.add_command(label=t("menu_export"), command=self._export_model)
        tm.add_command(label=t("menu_visualize"), command=self._visualize)
        tm.add_separator()
        tm.add_command(label=t("menu_env_check"), command=self._check_env)
        tm.add_separator()
        tm.add_command(label=t("menu_install_deps"), command=self._install_deps)
        tm.add_command(label=t("menu_check_deps"), command=self._check_deps_status)
        hm = tk.Menu(mb, tearoff=0)
        mb.add_cascade(label=t("menu_help"), menu=hm)
        hm.add_command(label=t("menu_about"), command=self._show_about)

    def _build_config(self, parent):
        nb = ttk.Notebook(parent)
        nb.pack(fill=tk.BOTH, expand=True, padx=(0, 6))
        f1 = ttk.Frame(nb, padding=10); nb.add(f1, text=f" {t('tab_basic')} "); self._build_basic(f1)
        f2 = ttk.Frame(nb, padding=10); nb.add(f2, text=f" {t('tab_advanced')} "); self._build_advanced(f2)
        f3 = ttk.Frame(nb, padding=10); nb.add(f3, text=f" {t('tab_augment')} "); self._build_augment(f3)

    def _build_basic(self, p):
        r = 0
        ttk.Label(p, text=t("dataset_config"), font=("Segoe UI", 10, "bold")).grid(row=r, column=0, columnspan=3, sticky=tk.W, pady=(0, 4)); r += 1
        self.data_var = tk.StringVar()
        e = ttk.Entry(p, textvariable=self.data_var); e.grid(row=r, column=0, columnspan=2, sticky=tk.EW, pady=2)
        self._tip(e, "tip_dataset")
        ttk.Button(p, text=t("browse"), command=self._browse_data).grid(row=r, column=2, padx=(4, 0), pady=2); r += 1

        ttk.Label(p, text=t("pretrained_model"), font=("Segoe UI", 10, "bold")).grid(row=r, column=0, columnspan=3, sticky=tk.W, pady=(8, 4)); r += 1
        self.model_var = tk.StringVar(value="yolo11n.pt")
        cb = ttk.Combobox(p, textvariable=self.model_var, values=["yolo11n.pt", "yolo11s.pt", "yolo11m.pt", "yolo11l.pt", "yolo11x.pt"], state="normal", width=20)
        cb.grid(row=r, column=0, sticky=tk.EW, pady=2); self._tip(cb, "tip_model")
        ttk.Button(p, text=t("browse"), command=self._browse_model).grid(row=r, column=1, padx=(4, 0), pady=2)
        hint = ttk.Label(p, text=t("or_type_path"), font=("Segoe UI", 8), foreground="#636e72")
        hint.grid(row=r, column=2, padx=(4, 0), pady=2); self._tip(hint, "tip_model_path"); r += 1

        ttk.Label(p, text=t("training_params"), font=("Segoe UI", 10, "bold")).grid(row=r, column=0, columnspan=3, sticky=tk.W, pady=(8, 4)); r += 1
        for lk, vn, df, tk_ in [("epochs", "epochs_var", 200, "tip_epochs"), ("batch_size", "batch_var", 32, "tip_batch"), ("image_size", "imgsz_var", 640, "tip_imgsz")]:
            lbl = ttk.Label(p, text=t(lk)); lbl.grid(row=r, column=0, sticky=tk.W, pady=2); self._tip(lbl, tk_)
            var = tk.IntVar(value=df); setattr(self, vn, var)
            sp = ttk.Spinbox(p, from_=1, to=2048, textvariable=var, width=10); sp.grid(row=r, column=1, columnspan=2, sticky=tk.W, pady=2); self._tip(sp, tk_); r += 1

        lbl = ttk.Label(p, text=t("device")); lbl.grid(row=r, column=0, sticky=tk.W, pady=2); self._tip(lbl, "tip_device")
        self.device_var = tk.StringVar(value="0")
        cb2 = ttk.Combobox(p, textvariable=self.device_var, values=["0", "0,1", "cpu"], state="readonly", width=10); cb2.grid(row=r, column=1, columnspan=2, sticky=tk.W, pady=2); self._tip(cb2, "tip_device"); r += 1

        lbl = ttk.Label(p, text=t("experiment_name")); lbl.grid(row=r, column=0, sticky=tk.W, pady=2); self._tip(lbl, "tip_name")
        self.name_var = tk.StringVar(value="openiris_v1")
        en = ttk.Entry(p, textvariable=self.name_var); en.grid(row=r, column=1, columnspan=2, sticky=tk.EW, pady=2); self._tip(en, "tip_name"); r += 1
        p.columnconfigure(1, weight=1)

    def _build_advanced(self, p):
        r = 0
        ttk.Label(p, text=t("optimizer"), font=("Segoe UI", 10, "bold")).grid(row=r, column=0, columnspan=2, sticky=tk.W, pady=(0, 4)); r += 1
        lbl = ttk.Label(p, text=t("algorithm")); lbl.grid(row=r, column=0, sticky=tk.W, pady=2); self._tip(lbl, "tip_optimizer")
        self.optimizer_var = tk.StringVar(value="AdamW")
        cb = ttk.Combobox(p, textvariable=self.optimizer_var, values=["SGD", "Adam", "AdamW", "auto"], state="readonly", width=12); cb.grid(row=r, column=1, sticky=tk.W, pady=2); self._tip(cb, "tip_optimizer"); r += 1

        for lk, vn, df, tk_ in [("learning_rate", "lr_var", 0.001, "tip_lr"), ("weight_decay", "wd_var", 0.0005, "tip_weight_decay"), ("label_smoothing", "smooth_var", (0.02 if False else 0.02), "tip_label_smoothing")]:
            lbl = ttk.Label(p, text=t(lk)); lbl.grid(row=r, column=0, sticky=tk.W, pady=2); self._tip(lbl, tk_)
            var = tk.DoubleVar(value=df); setattr(self, vn, var)
            e = ttk.Entry(p, textvariable=var, width=12); e.grid(row=r, column=1, sticky=tk.W, pady=2); self._tip(e, tk_); r += 1

        lbl = ttk.Label(p, text=t("early_stop_patience")); lbl.grid(row=r, column=0, sticky=tk.W, pady=2); self._tip(lbl, "tip_patience")
        self.patience_var = tk.IntVar(value=50)
        sp = ttk.Spinbox(p, from_=0, to=500, textvariable=self.patience_var, width=10); sp.grid(row=r, column=1, sticky=tk.W, pady=2); self._tip(sp, "tip_patience"); r += 1

        self.amp_var = tk.BooleanVar(value=True)
        chk = ttk.Checkbutton(p, text=t("amp_mixed_precision"), variable=self.amp_var); chk.grid(row=r, column=0, columnspan=2, sticky=tk.W, pady=4); self._tip(chk, "tip_amp"); r += 1

        lbl = ttk.Label(p, text=t("workers")); lbl.grid(row=r, column=0, sticky=tk.W, pady=2); self._tip(lbl, "tip_workers")
        self.workers_var = tk.IntVar(value=4)
        sp = ttk.Spinbox(p, from_=0, to=32, textvariable=self.workers_var, width=10); sp.grid(row=r, column=1, sticky=tk.W, pady=2); self._tip(sp, "tip_workers"); r += 1
        p.columnconfigure(1, weight=1)

    def _build_augment(self, p):
        r = 0
        ttk.Label(p, text=t("data_augmentation"), font=("Segoe UI", 10, "bold")).grid(row=r, column=0, columnspan=2, sticky=tk.W, pady=(0, 6)); r += 1
        canvas = tk.Canvas(p, highlightthickness=0)
        sb = ttk.Scrollbar(p, orient="vertical", command=canvas.yview)
        sf = ttk.Frame(canvas)
        sf.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas_window = canvas.create_window((0, 0), window=sf, anchor="nw")
        def _on_canvas_configure(e):
            canvas.itemconfigure(canvas_window, width=e.width)
        canvas.bind('<Configure>', _on_canvas_configure)
        canvas.configure(yscrollcommand=sb.set)
        canvas.grid(row=r, column=0, sticky="nsew"); sb.grid(row=r, column=1, sticky="ns")
        p.rowconfigure(r, weight=1); p.columnconfigure(0, weight=1)
        canvas.bind_all("<MouseWheel>", lambda e: canvas.yview_scroll(int(-1 * (e.delta / 120)), "units"))

        for label, vn, fr, to, df, res, tk_ in [
            ("Mosaic", "mosaic_var", 0, 1, 1.0, 0.05, "tip_mosaic"), ("Mixup", "mixup_var", 0, 1, 0.0, 0.05, "tip_mixup"),
            ("Flip L-R", "fliplr_var", 0, 1, 0.5, 0.05, "tip_fliplr"), ("Rotation", "degrees_var", 0, 45, 0.0, 1.0, "tip_degrees"),
            ("Scale", "scale_var", 0, 1, 0.5, 0.05, "tip_scale"), ("Translate", "trans_var", 0, 0.5, 0.1, 0.01, "tip_translate"),
            ("Shear", "shear_var", 0, 30, 0.0, 1.0, "tip_shear"), ("HSV-Hue", "hsv_h_var", 0, 0.1, 0.015, 0.001, "tip_hsv_h"),
            ("HSV-Sat", "hsv_s_var", 0, 1, 0.7, 0.05, "tip_hsv_s"), ("HSV-Val", "hsv_v_var", 0, 1, 0.4, 0.05, "tip_hsv_v"),
            ("Random Erase", "erasing_var", 0, 1, 0.0, 0.05, "tip_erasing"),
        ]:
            sl = LabeledSlider(sf, label=label, from_=fr, to=to, value=df, resolution=res, tooltip=t(tk_))
            sl.pack(fill=tk.X, padx=4, pady=3)
            setattr(self, vn, sl.var)
            self._tooltips[f"slider_{vn}"] = (sl, tk_)

    def _build_right(self, parent):
        ctrl = ttk.LabelFrame(parent, text=t("training_control"), padding=10)
        ctrl.pack(fill=tk.X, padx=(6, 0), pady=(0, 6))
        br = ttk.Frame(ctrl); br.pack(fill=tk.X)
        self.btn_start = ttk.Button(br, text=t("start_training"), style="Accent.TButton", command=self._start, width=10)
        self.btn_start.pack(side=tk.LEFT, padx=(0, 6))
        self._tip(self.btn_start, "tip_start")
        self.btn_stop = ttk.Button(br, text=t("stop"), style="Danger.TButton", command=self._stop, state=tk.DISABLED, width=10)
        self.btn_stop.pack(side=tk.LEFT, padx=(0, 6))
        self._tip(self.btn_stop, "tip_stop")
        self.btn_validate = ttk.Button(br, text=t("validate"), command=self._validate_dataset, width=10)
        self.btn_validate.pack(side=tk.LEFT, padx=(0, 6))
        self._tip(self.btn_validate, "tip_validate")
        self.btn_export = ttk.Button(br, text=t("export"), command=self._export_model, width=10)
        self.btn_export.pack(side=tk.LEFT)
        self._tip(self.btn_export, "tip_export")
        ttk.Label(br, text=t("export_format"), font=("Segoe UI", 9)).pack(side=tk.LEFT, padx=(12, 4))
        self.export_format_var = tk.StringVar(value=t("export_format_ncnn"))
        format_cb = ttk.Combobox(br, textvariable=self.export_format_var,
                                 values=[t("export_format_ncnn"), t("export_format_pt")],
                                 state="readonly", width=22)
        format_cb.pack(side=tk.LEFT)
        self.progress_var = tk.DoubleVar(value=0)
        ttk.Progressbar(ctrl, variable=self.progress_var, maximum=100).pack(fill=tk.X, pady=(8, 0))
        self.status_lbl_var = tk.StringVar(value=t("ready_to_train"))
        ttk.Label(ctrl, textvariable=self.status_lbl_var, font=("Segoe UI", 9)).pack(anchor=tk.W, pady=(4, 0))

        lf = ttk.LabelFrame(parent, text=t("training_log"), padding=10)
        lf.pack(fill=tk.BOTH, expand=True, padx=(6, 0))
        self.log_text = scrolledtext.ScrolledText(lf, height=15, state=tk.DISABLED, font=("Consolas", 9),
            wrap=tk.WORD, bg="#1e1e1e", fg="#d4d4d4", insertbackground="white", selectbackground="#264f78")
        self.log_text.pack(fill=tk.BOTH, expand=True)
        self.btn_clear = ttk.Button(lf, text=t("clear_log"), command=self._clear_log)
        self.btn_clear.pack(anchor=tk.E, pady=(4, 0))
        self._tip(self.btn_clear, "tip_clear_log")

    def _tip(self, widget, key):
        tt = ToolTip(widget, t(key))
        self._tooltips[key] = tt
        return tt

    def _switch_language(self, event=None):
        set_language(self.lang_var.get())
        for w in self.root.winfo_children(): w.destroy()
        self._build_ui()
        self._load_defaults()

    def _browse_data(self):
        f = filedialog.askopenfilename(title=t("dataset_config"), filetypes=[("YAML", "*.yaml"), ("All", "*.*")], initialdir=str(self.config_dir))
        if f: self.data_var.set(f)

    def _browse_model(self):
        f = filedialog.askopenfilename(title=t("pretrained_model"), filetypes=[("PyTorch", "*.pt"), ("All", "*.*")], initialdir=str(self.project_root))
        if f: self.model_var.set(f)

    def _load_config(self):
        f = filedialog.askopenfilename(title=t("menu_load_config"), filetypes=[("YAML", "*.yaml"), ("All", "*.*")], initialdir=str(self.config_dir))
        if f:
            try:
                with open(f, "r", encoding="utf-8") as fh: self._apply_cfg(yaml.safe_load(fh))
                self._log(f"Config loaded: {f}")
            except Exception as e: messagebox.showerror(t("error_load_config"), str(e))

    def _save_config(self):
        f = filedialog.asksaveasfilename(title=t("menu_save_config"), defaultextension=".yaml", filetypes=[("YAML", "*.yaml"), ("All", "*.*")], initialdir=str(self.config_dir))
        if f:
            try:
                with open(f, "w", encoding="utf-8") as fh: yaml.dump(self._get_cfg(), fh, allow_unicode=True, default_flow_style=False)
                self._log(f"Config saved: {f}")
            except Exception as e:
                messagebox.showerror(t("error_save_config"), str(e))

    def _load_defaults(self):
        hyp = self.config_dir / "hyp_train.yaml"
        if hyp.exists():
            try:
                with open(hyp, "r", encoding="utf-8") as f: self._apply_cfg(yaml.safe_load(f))
            except Exception: pass

    def _apply_cfg(self, cfg):
        m = {"model": self.model_var, "epochs": self.epochs_var, "batch": self.batch_var, "imgsz": self.imgsz_var,
             "optimizer": self.optimizer_var, "lr0": self.lr_var, "weight_decay": self.wd_var, "patience": self.patience_var,
             "amp": self.amp_var, "label_smoothing": self.smooth_var, "mosaic": self.mosaic_var, "mixup": self.mixup_var,
             "fliplr": self.fliplr_var, "degrees": self.degrees_var, "scale": self.scale_var, "hsv_s": self.hsv_s_var,
             "hsv_v": self.hsv_v_var, "erasing": self.erasing_var, "name": self.name_var, "device": self.device_var,
             "workers": self.workers_var, "data": self.data_var}
        for k, v in m.items():
            if k in cfg:
                try: v.set(cfg[k])
                except Exception: pass

    def _get_cfg(self):
        return {"model": self.model_var.get(), "data": self.data_var.get(), "epochs": self.epochs_var.get(),
                "batch": self.batch_var.get(), "imgsz": self.imgsz_var.get(), "device": self.device_var.get(),
                "optimizer": self.optimizer_var.get(), "lr0": self.lr_var.get(), "weight_decay": self.wd_var.get(),
                "patience": self.patience_var.get(), "amp": self.amp_var.get(), "label_smoothing": self.smooth_var.get(),
                "workers": self.workers_var.get(), "name": self.name_var.get(), "mosaic": self.mosaic_var.get(),
                "mixup": self.mixup_var.get(), "fliplr": self.fliplr_var.get(), "degrees": self.degrees_var.get(),
                "scale": self.scale_var.get(), "hsv_s": self.hsv_s_var.get(), "hsv_v": self.hsv_v_var.get(),
                "erasing": self.erasing_var.get(), "project": "runs/train"}

    # ---- Training (handles both packaged exe and script mode) ----
    def _start(self):
        if not self.data_var.get():
            messagebox.showerror("Error", t("error_no_dataset"))
            return
        if self.is_training:
            return

        cfg = self._get_cfg()
        self._total_epochs = int(cfg.get("epochs", 200))
        self._stop_event = threading.Event()

        self._log("=" * 50)
        self._log(f"{t('start_training')}...")
        self._log(f"Mode: {'EXE (direct)' if IS_PACKAGED else 'Python (subprocess)'}")
        self._log("=" * 50)

        self.is_training = True
        self.btn_start.config(state=tk.DISABLED)
        self.btn_stop.config(state=tk.NORMAL)
        self.status_lbl_var.set(t("training_in_progress"))

        if IS_PACKAGED:
            # Direct call (no subprocess needed)
            threading.Thread(target=self._run_direct, args=(cfg,), daemon=True).start()
        else:
            # Subprocess call
            cmd = [sys.executable, str(self.project_root / "training" / "scripts" / "train.py"),
                   "--data", cfg["data"], "--model", cfg["model"],
                   "--epochs", str(cfg["epochs"]), "--batch", str(cfg["batch"]),
                   "--imgsz", str(cfg["imgsz"]), "--device", str(cfg["device"]),
                   "--name", cfg["name"]]
            self._log(f"CMD: {' '.join(cmd)}")
            threading.Thread(target=self._run_subprocess, args=(cmd,), daemon=True).start()

    def _run_direct(self, cfg):
        """Run training directly (for packaged exe)."""
        def log_cb(msg):
            if not self._stop_event.is_set():
                self.root.after(0, self._log, msg)

        success, msg = run_training_direct(cfg, log_callback=log_cb)

        if not self._stop_event.is_set():
            status_msg = t("training_complete") if success else t("training_failed")
            self.root.after(0, self._log, f"\n{status_msg}")
            self.root.after(0, self.status_lbl_var.set, status_msg)
        self.root.after(0, self._done)

    def _run_subprocess(self, cmd):
        """Run training via subprocess (for Python script mode)."""
        try:
            flags = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0
            env = os.environ.copy()
            env["PYTHONIOENCODING"] = "utf-8"
            with self._proc_lock:
                self.train_proc = subprocess.Popen(
                    cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                    bufsize=1, creationflags=flags, encoding="utf-8", errors="replace", env=env)
            for line in self.train_proc.stdout:
                if self._stop_event.is_set():
                    break
                self.root.after(0, self._log, line.strip())
                # Parse epoch progress from training output
                epoch_match = re.search(r'[Ee]poch\s+(\d+)/(\d+)', line)
                if epoch_match:
                    current_epoch = int(epoch_match.group(1))
                    total = int(epoch_match.group(2))
                    if total > 0:
                        progress = current_epoch / total * 100
                        self.root.after(0, self.progress_var.set, progress)
            self.train_proc.wait()
            rc = self.train_proc.returncode
            if not self._stop_event.is_set():
                msg = t("training_complete") if rc == 0 else f"{t('training_failed')} (exit {rc})"
                self.root.after(0, self._log, f"\n{msg}")
                self.root.after(0, self.status_lbl_var.set, msg)
        except Exception as e:
            if not self._stop_event.is_set():
                self.root.after(0, self._log, f"\nError: {e}")
        finally:
            if self.train_proc:
                try: self.train_proc.stdout.close()
                except Exception: pass
            self.root.after(0, self._done)

    def _done(self):
        self.is_training = False
        self.btn_start.config(state=tk.NORMAL)
        self.btn_stop.config(state=tk.DISABLED)
        self.train_proc = None

    def _stop(self):
        if self.is_training:
            if messagebox.askyesno("?", t("error_confirm_stop")):
                self._stop_event.set()
                with self._proc_lock:
                    if self.train_proc:
                        try:
                            self.train_proc.stdout.close()
                        except Exception:
                            pass
                        try:
                            self.train_proc.terminate()
                        except Exception:
                            pass
                self.status_lbl_var.set(t("training_stopped"))
                self.btn_start.config(state=tk.NORMAL)
                self.btn_stop.config(state=tk.DISABLED)

    # ---- Tools ----
    def _validate_dataset(self):
        if not self.data_var.get():
            messagebox.showerror("Error", t("error_no_dataset_first"))
            return
        if IS_PACKAGED:
            self._log(f"{t('validate')}... (direct mode)")
            threading.Thread(target=self._validate_direct, daemon=True).start()
        else:
            cmd = [sys.executable, str(self.project_root / "training" / "scripts" / "dataset_validator.py"),
                   "--data", self.data_var.get()]
            self._log(f"{t('validate')}...")
            threading.Thread(target=self._run_subprocess, args=(cmd,), daemon=True).start()

    def _validate_direct(self):
        """Run validation directly in packaged mode."""
        try:
            from training.scripts.dataset_validator import main as validate_main
            # Temporarily set sys.argv
            old_argv = sys.argv
            sys.argv = ["dataset_validator.py", "--data", self.data_var.get()]
            validate_main()
            sys.argv = old_argv
        except Exception as e:
            self.root.after(0, self._log, f"Validation error: {e}")
        finally:
            self.root.after(0, self._done)

    def _export_model(self):
        # 提醒用户：导出文件命名
        _reminder = t("export_reminder")
        if _reminder == "export_reminder":  # i18n key 不存在时原样返回 key
            _reminder = (
                "即将导出模型文件。请选择训练好的模型文件(.pt)和输出目录。\n\n"
                "导出后请注意为输出文件夹取一个有意义的名称（如模型名称），\n"
                "避免下次导出时覆盖。每次训练完成后都会生成新的 best.pt，\n"
                "建议用训练名称（如 pig_yolov11s）命名输出文件夹。\n\n"
                "NCNN zip 包内含: model.param + model.bin + labels.txt + model_meta.json\n"
                "APP 端可直接导入此 zip 文件使用。"
            )
        messagebox.showinfo(t("export"), _reminder)
        w = filedialog.askopenfilename(title=t("export_select_model"),
                                       filetypes=[("PyTorch", "*.pt"), ("ONNX", "*.onnx"), ("All", "*.*")],
                                       initialdir=str(self.project_root / "training" / "run"))
        if not w:
            return
        o = filedialog.askdirectory(title=t("export_select_output"),
                                     initialdir=str(self.project_root / "training" / "run"))
        if o:
            python_exe = find_python()
            if python_exe is None and IS_PACKAGED:
                # 打包模式下无 Python，直接调用 export_pipeline.main()
                self._log(f"{t('export')}... (direct mode)")
                try:
                    from training.tools.export_pipeline import main as export_main
                    old_argv = sys.argv
                    sys.argv = [
                        "export_pipeline.py",
                        "--weights", w,
                        "--output", o,
                    ]
                    # 根据格式选择添加参数
                    fmt = self.export_format_var.get()
                    if t("export_format_pt") in fmt or "PyTorch" in fmt:
                        sys.argv.extend(["--format", "onnx"])
                    self._log(f"{t('export')}... ({fmt}) (direct)")
                    export_main()
                except Exception as e:
                    self._log(f"{t('export')} error: {e}")
                finally:
                    sys.argv = old_argv
            elif python_exe is not None:
                cmd = [python_exe, str(self.project_root / "training" / "tools" / "export_pipeline.py"),
                       "--weights", w, "--output", o]
                # 根据格式选择添加参数
                fmt = self.export_format_var.get()
                if t("export_format_pt") in fmt or "PyTorch" in fmt:
                    cmd.extend(["--format", "onnx"])
                self._log(f"{t('export')}... ({fmt})")
                threading.Thread(target=self._run_subprocess, args=(cmd,), daemon=True).start()
            else:
                self._log(f"{t('export')}... (direct mode)")
                self._log("Export requires Python environment. Use command line.")

    def _visualize(self):
        messagebox.showinfo(t("menu_visualize"), t("visualize_explain"))
        d = filedialog.askdirectory(title=t("menu_visualize"), initialdir=str(self.project_root / "runs" / "train"))
        if d:
            if IS_PACKAGED:
                self._log("Visualization requires Python environment. Use command line.")
            else:
                cmd = [sys.executable, str(self.project_root / "training" / "tools" / "visualize_results.py"), "--run-dir", d]
                self._log(f"{t('menu_visualize')}...")
                threading.Thread(target=self._run_subprocess, args=(cmd,), daemon=True).start()

    def _check_env(self):
        self._log(f"{t('menu_env_check')}...")
        checks = [f"{t('env_python')}: {sys.version}", f"Mode: {'Packaged EXE' if IS_PACKAGED else 'Python Script'}"]
        for name in ["torch", "ultralytics", "cv2"]:
            try:
                m = __import__(name)
                checks.append(f"{name}: {getattr(m, '__version__', 'installed')}")
            except ImportError:
                checks.append(f"{name}: {t('env_not_installed')}")
        try:
            import torch
            checks.append(f"{t('env_gpu')}: {torch.cuda.get_device_name(0) if torch.cuda.is_available() else t('env_not_available')}")
        except Exception: pass
        self._log("\n".join(checks))

    def _show_about(self):
        messagebox.showinfo(t("about_title"), t("about_text"))

    def _check_deps_status(self):
        """检查所有依赖的安装状态"""
        deps = {
            t("deps_group_core"): [
                ("torch", "PyTorch 深度学习框架"),
                ("torchvision", "计算机视觉 transforms"),
                ("ultralytics", "YOLOv11 训练框架"),
                ("cv2", "图像处理 (opencv-python)"),
                ("PIL", "图像 I/O (Pillow)"),
                ("numpy", "数组运算"),
                ("yaml", "YAML 配置解析 (pyyaml)"),
                ("pandas", "数据分析"),
                ("onnx", "ONNX 导出"),
                ("matplotlib", "训练曲线绘图"),
                ("seaborn", "统计图表"),
                ("sklearn", "指标计算 (scikit-learn)"),
                ("tqdm", "进度条"),
                ("psutil", "系统监控"),
            ],
            t("deps_group_optional"): [
                ("onnxsim", "ONNX 简化"),
                ("tensorboard", "训练日志可视化"),
                ("ncnn", "NCNN 推理引擎"),
                ("pnnx", "PyTorch->NCNN 转换"),
            ],
            t("deps_group_enhanced"): [
                ("albumentations", "高级图像增强"),
                ("imgaug", "图像增强"),
                ("GPUtil", "GPU 监控"),
            ],
        }
        installed = []
        missing = []
        for group_name, group_deps in deps.items():
            for mod_name, desc in group_deps:
                try:
                    mod = __import__(mod_name)
                    ver = getattr(mod, "__version__", "")
                    label = f"{mod_name} {ver}" if ver else mod_name
                    installed.append((group_name, mod_name, desc, label))
                except ImportError:
                    missing.append((group_name, mod_name, desc))

        win = tk.Toplevel(self.root)
        win.title(t("deps_status_title"))
        win.geometry("620x480")
        win.transient(self.root)
        win.grab_set()

        cols = ("group", "package", "description", "status")
        tree = ttk.Treeview(win, columns=cols, show="headings", height=18)
        tree.heading("group", text=t("deps_status_group"))
        tree.heading("package", text=t("deps_status_package"))
        tree.heading("description", text=t("deps_status_desc"))
        tree.heading("status", text=t("deps_status_status"))
        tree.column("group", width=80)
        tree.column("package", width=130)
        tree.column("description", width=250)
        tree.column("status", width=130)
        tree.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)

        for g, m, d, v in installed:
            tree.insert("", tk.END, values=(g, m, d, f"✓ {v}"))
        for g, m, d in missing:
            tree.insert("", tk.END, values=(g, m, d, f"✗ {t('deps_not_installed')}"))

        ok_count = len(installed)
        total = ok_count + len(missing)
        ttk.Label(win, text=f"{ok_count}/{total} {t('deps_installed')}").pack(pady=(0, 10))

    def _get_deps_status_text(self):
        """获取依赖状态摘要文本"""
        deps = [
            ("torch", "PyTorch"), ("torchvision", "TorchVision"),
            ("ultralytics", "Ultralytics"), ("cv2", "OpenCV"),
            ("PIL", "Pillow"), ("numpy", "NumPy"),
            ("yaml", "PyYAML"), ("pandas", "Pandas"),
            ("onnx", "ONNX"), ("matplotlib", "Matplotlib"),
            ("seaborn", "Seaborn"), ("sklearn", "Scikit-learn"),
            ("tqdm", "tqdm"), ("psutil", "psutil"),
            ("onnxsim", "onnxsim"), ("tensorboard", "TensorBoard"),
            ("ncnn", "NCNN"), ("pnnx", "pnnx"),
            ("albumentations", "Albumentations"), ("imgaug", "imgaug"),
            ("GPUtil", "GPUtil"),
        ]
        ok = []
        ng = []
        for mod, label in deps:
            try:
                __import__(mod)
                ok.append(label)
            except ImportError:
                ng.append(label)
        lines = [f"{t('deps_installed')} ({len(ok)}): {', '.join(ok)}"]
        if ng:
            lines.append(f"{t('deps_not_installed')} ({len(ng)}): {', '.join(ng)}")
        return "\n".join(lines)

    def _install_deps(self):
        """一键安装依赖入口"""
        self._show_install_dialog()

    def _show_install_dialog(self):
        """显示依赖安装对话框"""
        dialog = tk.Toplevel(self.root)
        dialog.title(t("install_deps_title"))
        dialog.geometry("500x400")
        dialog.transient(self.root)
        dialog.grab_set()

        status_text = self._get_deps_status_text()
        ttk.Label(dialog, text=status_text, justify=tk.LEFT, wraplength=470).pack(padx=10, pady=10)

        ttk.Label(dialog, text=t("install_deps_select")).pack(padx=10, pady=5)

        var_core = tk.BooleanVar(value=True)
        var_optional = tk.BooleanVar(value=True)
        var_enhanced = tk.BooleanVar(value=False)

        ttk.Checkbutton(dialog, text=t("install_deps_core"), variable=var_core).pack(anchor=tk.W, padx=20)
        ttk.Checkbutton(dialog, text=t("install_deps_optional"), variable=var_optional).pack(anchor=tk.W, padx=20)
        ttk.Checkbutton(dialog, text=t("install_deps_enhanced"), variable=var_enhanced).pack(anchor=tk.W, padx=20)

        ttk.Button(dialog, text=t("install_deps_btn"), command=lambda: self._start_install(
            var_core.get(), var_optional.get(), var_enhanced.get(), dialog
        )).pack(pady=20)

    def _start_install(self, core, optional, enhanced, dialog):
        """在后台线程中执行 pip install"""
        dialog.destroy()

        packages = []
        if core:
            packages.extend(["ultralytics", "opencv-python", "Pillow", "numpy",
                             "pyyaml", "pandas", "onnx", "matplotlib",
                             "seaborn", "scikit-learn", "tqdm", "psutil"])
        if optional:
            packages.extend(["onnxsim", "tensorboard", "ncnn", "pnnx"])
        if enhanced:
            packages.extend(["albumentations", "imgaug", "GPUtil"])

        if not packages:
            return

        cmd = [sys.executable, "-m", "pip", "install"] + packages
        cmd.extend(["--proxy", "http://127.0.0.1:7897"])
        cmd.extend(["-i", "https://pypi.tuna.tsinghua.edu.cn/simple"])
        cmd.extend(["--trusted-host", "pypi.tuna.tsinghua.edu.cn"])
        cmd.extend(["--timeout", "120"])

        self._log(t("install_deps_start"))
        threading.Thread(target=self._run_subprocess, args=(cmd,), daemon=True).start()

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


def main():
    # Hide console window on Windows (script mode); packaged exe uses console=False in spec
    if sys.platform == "win32":
        try:
            _hwnd = ctypes.windll.kernel32.GetConsoleWindow()
            if _hwnd:
                ctypes.windll.user32.ShowWindow(_hwnd, 0)  # SW_HIDE
        except Exception:
            pass
    setup_dpi()
    root = tk.Tk()
    TrainingGUI(root)
    root.mainloop()


if __name__ == "__main__":
    main()
