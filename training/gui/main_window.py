#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Training GUI - Main Window (i18n: zh_CN default, en_US fallback)
Modern tkinter-based YOLO training interface.
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

from training.gui.i18n import t, set_language, get_language, get_available_languages


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
        tk.Label(f, text=self._text, justify=tk.LEFT,
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

        self.lbl = tk.Label(self, text=label, width=16, anchor="w",
                            font=("Segoe UI", 9))
        self.lbl.pack(side=tk.LEFT, padx=(0, 8))
        if tooltip:
            ToolTip(self.lbl, tooltip)

        self.var = tk.IntVar(value=int(value)) if is_int else tk.DoubleVar(value=value)
        self.entry = tk.Entry(self, textvariable=self.var, width=8,
                              justify="center", font=("Segoe UI", 9),
                              relief=tk.FLAT, bg="#f0f0f0")
        self.entry.pack(side=tk.RIGHT, padx=(8, 0))
        self.entry.bind("<Return>", self._sync)
        self.entry.bind("<FocusOut>", self._sync)
        if tooltip:
            ToolTip(self.entry, tooltip)

        self.scale = tk.Scale(self, from_=from_, to=to, variable=self.var,
                              orient=tk.HORIZONTAL, showvalue=False,
                              resolution=resolution, sliderlength=20,
                              length=300, font=("Segoe UI", 8))
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

    def get(self):
        return self.var.get()

    def set(self, v):
        self.var.set(v)


# ============================================================
# Modern Style
# ============================================================
def apply_modern_style():
    s = ttk.Style()
    try:
        s.theme_use("clam")
    except Exception:
        pass
    BG, FG, ACCENT, CARD = "#f5f6fa", "#2d3436", "#0984e3", "#ffffff"
    BORDER, HEADER = "#dfe6e9", "#dfe6e9"
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
    s.configure("TLabelframe", background=CARD, foreground=FG, borderwidth=1,
                relief="solid", bordercolor=BORDER)
    s.configure("TLabelframe.Label", background=CARD, foreground=ACCENT,
                font=("Segoe UI", 10, "bold"))
    s.configure("TNotebook", background=BG, borderwidth=0)
    s.configure("TNotebook.Tab", padding=[16, 6], font=("Segoe UI", 9))
    s.map("TNotebook.Tab",
          background=[("selected", CARD), ("!selected", HEADER)],
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
        self.root.geometry("1100x780")
        self.root.minsize(960, 680)

        self.train_proc = None
        self.is_training = False
        self.project_root = Path(__file__).parent.parent.parent
        self.config_dir = self.project_root / "training" / "configs"

        # Tooltip registry for language switch
        self._tooltips = {}

        self._build_ui()
        self._load_defaults()

        # Set icon
        ico = Path(__file__).parent / "openiris.ico"
        if ico.exists():
            try:
                self.root.iconbitmap(str(ico))
            except Exception:
                pass

    # ---- UI ----
    def _build_ui(self):
        # Top bar
        top = ttk.Frame(self.root)
        top.pack(fill=tk.X, padx=12, pady=(10, 4))
        ttk.Label(top, text=t("app_title"), font=("Segoe UI", 14, "bold"),
                  foreground=self.colors[2]).pack(side=tk.LEFT)
        ttk.Label(top, text=f"{t('version')} 2.0", font=("Segoe UI", 9),
                  foreground="#636e72").pack(side=tk.LEFT, padx=(8, 0))

        # Language switch
        lang_frame = ttk.Frame(top)
        lang_frame.pack(side=tk.RIGHT)
        ttk.Label(lang_frame, text="Language:", font=("Segoe UI", 9)).pack(side=tk.LEFT)
        self.lang_var = tk.StringVar(value=get_language())
        lang_cb = ttk.Combobox(lang_frame, textvariable=self.lang_var,
                               values=list(get_available_languages().keys()),
                               state="readonly", width=6)
        lang_cb.pack(side=tk.LEFT, padx=(4, 0))
        lang_cb.bind("<<ComboboxSelected>>", self._switch_language)
        ToolTip(lang_cb, "zh_CN: 简体中文\nen_US: English")

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
        ttk.Label(sb, textvariable=self.status_var,
                  font=("Segoe UI", 8), foreground="#636e72").pack(side=tk.LEFT)
        ttk.Label(sb, text="OpenIris v2.0", font=("Segoe UI", 8),
                  foreground="#b2bec3").pack(side=tk.RIGHT)

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

        hm = tk.Menu(mb, tearoff=0)
        mb.add_cascade(label=t("menu_help"), menu=hm)
        hm.add_command(label=t("menu_about"), command=self._show_about)

    # ---- Config Panel ----
    def _build_config(self, parent):
        nb = ttk.Notebook(parent)
        nb.pack(fill=tk.BOTH, expand=True, padx=(0, 6))

        f1 = ttk.Frame(nb, padding=10)
        nb.add(f1, text=f" {t('tab_basic')} ")
        self._build_basic(f1)

        f2 = ttk.Frame(nb, padding=10)
        nb.add(f2, text=f" {t('tab_advanced')} ")
        self._build_advanced(f2)

        f3 = ttk.Frame(nb, padding=10)
        nb.add(f3, text=f" {t('tab_augment')} ")
        self._build_augment(f3)

    def _build_basic(self, p):
        r = 0
        ttk.Label(p, text=t("dataset_config"),
                  font=("Segoe UI", 10, "bold")).grid(row=r, column=0, columnspan=3, sticky=tk.W, pady=(0, 4))
        r += 1
        self.data_var = tk.StringVar()
        e = ttk.Entry(p, textvariable=self.data_var)
        e.grid(row=r, column=0, columnspan=2, sticky=tk.EW, pady=2)
        self._tip(e, "tip_dataset")
        b = ttk.Button(p, text=t("browse"), command=self._browse_data)
        b.grid(row=r, column=2, padx=(4, 0), pady=2)
        r += 1

        ttk.Label(p, text=t("pretrained_model"),
                  font=("Segoe UI", 10, "bold")).grid(row=r, column=0, columnspan=3, sticky=tk.W, pady=(8, 4))
        r += 1
        self.model_var = tk.StringVar(value="yolo11n.pt")
        cb = ttk.Combobox(p, textvariable=self.model_var,
                          values=["yolo11n.pt", "yolo11s.pt", "yolo11m.pt", "yolo11l.pt", "yolo11x.pt"],
                          state="normal", width=20)
        cb.grid(row=r, column=0, sticky=tk.EW, pady=2)
        self._tip(cb, "tip_model")
        b2 = ttk.Button(p, text=t("browse"), command=self._browse_model)
        b2.grid(row=r, column=1, padx=(4, 0), pady=2)
        hint = ttk.Label(p, text=t("or_type_path"), font=("Segoe UI", 8), foreground="#636e72")
        hint.grid(row=r, column=2, padx=(4, 0), pady=2)
        self._tip(hint, "tip_model_path")
        r += 1

        ttk.Label(p, text=t("training_params"),
                  font=("Segoe UI", 10, "bold")).grid(row=r, column=0, columnspan=3, sticky=tk.W, pady=(8, 4))
        r += 1

        for label_key, varname, default, tip_key in [
            ("epochs", "epochs_var", 200, "tip_epochs"),
            ("batch_size", "batch_var", 32, "tip_batch"),
            ("image_size", "imgsz_var", 640, "tip_imgsz"),
        ]:
            lbl = ttk.Label(p, text=t(label_key))
            lbl.grid(row=r, column=0, sticky=tk.W, pady=2)
            self._tip(lbl, tip_key)
            var = tk.IntVar(value=default)
            setattr(self, varname, var)
            sp = ttk.Spinbox(p, from_=1, to=2048, textvariable=var, width=10)
            sp.grid(row=r, column=1, columnspan=2, sticky=tk.W, pady=2)
            self._tip(sp, tip_key)
            r += 1

        lbl = ttk.Label(p, text=t("device"))
        lbl.grid(row=r, column=0, sticky=tk.W, pady=2)
        self._tip(lbl, "tip_device")
        self.device_var = tk.StringVar(value="0")
        cb2 = ttk.Combobox(p, textvariable=self.device_var, values=["0", "0,1", "cpu"],
                           state="readonly", width=10)
        cb2.grid(row=r, column=1, columnspan=2, sticky=tk.W, pady=2)
        self._tip(cb2, "tip_device")
        r += 1

        lbl = ttk.Label(p, text=t("experiment_name"))
        lbl.grid(row=r, column=0, sticky=tk.W, pady=2)
        self._tip(lbl, "tip_name")
        self.name_var = tk.StringVar(value="openiris_v1")
        en = ttk.Entry(p, textvariable=self.name_var)
        en.grid(row=r, column=1, columnspan=2, sticky=tk.EW, pady=2)
        self._tip(en, "tip_name")
        r += 1
        p.columnconfigure(1, weight=1)

    def _build_advanced(self, p):
        r = 0
        ttk.Label(p, text=t("optimizer"),
                  font=("Segoe UI", 10, "bold")).grid(row=r, column=0, columnspan=2, sticky=tk.W, pady=(0, 4))
        r += 1

        lbl = ttk.Label(p, text=t("algorithm"))
        lbl.grid(row=r, column=0, sticky=tk.W, pady=2)
        self._tip(lbl, "tip_optimizer")
        self.optimizer_var = tk.StringVar(value="AdamW")
        cb = ttk.Combobox(p, textvariable=self.optimizer_var,
                          values=["SGD", "Adam", "AdamW", "auto"], state="readonly", width=12)
        cb.grid(row=r, column=1, sticky=tk.W, pady=2)
        self._tip(cb, "tip_optimizer")
        r += 1

        for lk, vn, df, tk_ in [
            ("learning_rate", "lr_var", 0.001, "tip_lr"),
            ("weight_decay", "wd_var", 0.0005, "tip_weight_decay"),
            ("label_smoothing", "smooth_var", 0.02, "tip_label_smoothing"),
        ]:
            lbl = ttk.Label(p, text=t(lk))
            lbl.grid(row=r, column=0, sticky=tk.W, pady=2)
            self._tip(lbl, tk_)
            var = tk.DoubleVar(value=df)
            setattr(self, vn, var)
            e = ttk.Entry(p, textvariable=var, width=12)
            e.grid(row=r, column=1, sticky=tk.W, pady=2)
            self._tip(e, tk_)
            r += 1

        lbl = ttk.Label(p, text=t("early_stop_patience"))
        lbl.grid(row=r, column=0, sticky=tk.W, pady=2)
        self._tip(lbl, "tip_patience")
        self.patience_var = tk.IntVar(value=50)
        sp = ttk.Spinbox(p, from_=0, to=500, textvariable=self.patience_var, width=10)
        sp.grid(row=r, column=1, sticky=tk.W, pady=2)
        self._tip(sp, "tip_patience")
        r += 1

        self.amp_var = tk.BooleanVar(value=True)
        chk = ttk.Checkbutton(p, text=t("amp_mixed_precision"), variable=self.amp_var)
        chk.grid(row=r, column=0, columnspan=2, sticky=tk.W, pady=4)
        self._tip(chk, "tip_amp")
        r += 1

        lbl = ttk.Label(p, text=t("workers"))
        lbl.grid(row=r, column=0, sticky=tk.W, pady=2)
        self._tip(lbl, "tip_workers")
        self.workers_var = tk.IntVar(value=4)
        sp = ttk.Spinbox(p, from_=0, to=32, textvariable=self.workers_var, width=10)
        sp.grid(row=r, column=1, sticky=tk.W, pady=2)
        self._tip(sp, "tip_workers")
        r += 1
        p.columnconfigure(1, weight=1)

    def _build_augment(self, p):
        r = 0
        ttk.Label(p, text=t("data_augmentation"),
                  font=("Segoe UI", 10, "bold")).grid(row=r, column=0, columnspan=2, sticky=tk.W, pady=(0, 6))
        r += 1

        canvas = tk.Canvas(p, highlightthickness=0)
        sb = ttk.Scrollbar(p, orient="vertical", command=canvas.yview)
        sf = ttk.Frame(canvas)
        sf.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.create_window((0, 0), window=sf, anchor="nw")
        canvas.configure(yscrollcommand=sb.set)
        canvas.grid(row=r, column=0, sticky="nsew")
        sb.grid(row=r, column=1, sticky="ns")
        p.rowconfigure(r, weight=1)
        p.columnconfigure(0, weight=1)
        canvas.bind_all("<MouseWheel>", lambda e: canvas.yview_scroll(int(-1 * (e.delta / 120)), "units"))

        sliders = [
            ("Mosaic",       "mosaic_var",  0, 1,   1.0,   0.05, "tip_mosaic"),
            ("Mixup",        "mixup_var",   0, 1,   0.0,   0.05, "tip_mixup"),
            ("Flip L-R",     "fliplr_var",  0, 1,   0.5,   0.05, "tip_fliplr"),
            ("Rotation",     "degrees_var", 0, 45,  0.0,   1.0,  "tip_degrees"),
            ("Scale",        "scale_var",   0, 1,   0.5,   0.05, "tip_scale"),
            ("Translate",    "trans_var",   0, 0.5, 0.1,   0.01, "tip_translate"),
            ("Shear",        "shear_var",   0, 30,  0.0,   1.0,  "tip_shear"),
            ("HSV-Hue",      "hsv_h_var",   0, 0.1, 0.015, 0.001,"tip_hsv_h"),
            ("HSV-Sat",      "hsv_s_var",   0, 1,   0.7,   0.05, "tip_hsv_s"),
            ("HSV-Val",      "hsv_v_var",   0, 1,   0.4,   0.05, "tip_hsv_v"),
            ("Random Erase", "erasing_var", 0, 1,   0.0,   0.05, "tip_erasing"),
        ]

        for label, vn, fr, to, df, res, tk_ in sliders:
            sl = LabeledSlider(sf, label=label, from_=fr, to=to,
                               value=df, resolution=res, tooltip=t(tk_))
            sl.pack(fill=tk.X, padx=4, pady=3)
            setattr(self, vn, sl.var)
            # Store for language switch
            self._tooltips[f"slider_{vn}"] = (sl, tk_)

    # ---- Right Panel ----
    def _build_right(self, parent):
        ctrl = ttk.LabelFrame(parent, text=t("training_control"), padding=10)
        ctrl.pack(fill=tk.X, padx=(6, 0), pady=(0, 6))

        br = ttk.Frame(ctrl)
        br.pack(fill=tk.X)
        self.btn_start = ttk.Button(br, text=t("start_training"),
                                     style="Accent.TButton", command=self._start)
        self.btn_start.pack(side=tk.LEFT, padx=(0, 6))
        self.btn_stop = ttk.Button(br, text=t("stop"), style="Danger.TButton",
                                    command=self._stop, state=tk.DISABLED)
        self.btn_stop.pack(side=tk.LEFT, padx=(0, 6))
        ttk.Button(br, text=t("validate"), command=self._validate_dataset).pack(side=tk.LEFT, padx=(0, 6))
        ttk.Button(br, text=t("export"), command=self._export_model).pack(side=tk.LEFT)

        self.progress_var = tk.DoubleVar(value=0)
        ttk.Progressbar(ctrl, variable=self.progress_var, maximum=100).pack(fill=tk.X, pady=(8, 0))

        self.status_lbl_var = tk.StringVar(value=t("ready_to_train"))
        ttk.Label(ctrl, textvariable=self.status_lbl_var, font=("Segoe UI", 9)).pack(anchor=tk.W, pady=(4, 0))

        lf = ttk.LabelFrame(parent, text=t("training_log"), padding=10)
        lf.pack(fill=tk.BOTH, expand=True, padx=(6, 0))

        self.log_text = scrolledtext.ScrolledText(
            lf, height=15, state=tk.DISABLED, font=("Consolas", 9),
            wrap=tk.WORD, bg="#1e1e1e", fg="#d4d4d4",
            insertbackground="white", selectbackground="#264f78")
        self.log_text.pack(fill=tk.BOTH, expand=True)

        self.btn_clear = ttk.Button(lf, text=t("clear_log"), command=self._clear_log)
        self.btn_clear.pack(anchor=tk.E, pady=(4, 0))

    # ---- Tooltip helper ----
    def _tip(self, widget, key):
        tt = ToolTip(widget, t(key))
        self._tooltips[key] = tt
        return tt

    # ---- Language switch ----
    def _switch_language(self, event=None):
        lang = self.lang_var.get()
        set_language(lang)
        # Rebuild UI
        for w in self.root.winfo_children():
            w.destroy()
        self._build_ui()
        self._load_defaults()

    # ---- File dialogs ----
    def _browse_data(self):
        f = filedialog.askopenfilename(title=t("dataset_config"),
                                       filetypes=[("YAML", "*.yaml"), ("All", "*.*")],
                                       initialdir=str(self.config_dir))
        if f:
            self.data_var.set(f)

    def _browse_model(self):
        f = filedialog.askopenfilename(title=t("pretrained_model"),
                                       filetypes=[("PyTorch", "*.pt"), ("All", "*.*")],
                                       initialdir=str(self.project_root))
        if f:
            self.model_var.set(f)

    def _load_config(self):
        f = filedialog.askopenfilename(title=t("menu_load_config"),
                                       filetypes=[("YAML", "*.yaml"), ("All", "*.*")],
                                       initialdir=str(self.config_dir))
        if f:
            try:
                with open(f, "r", encoding="utf-8") as fh:
                    self._apply_cfg(yaml.safe_load(fh))
                self._log(f"Config loaded: {f}")
            except Exception as e:
                messagebox.showerror(t("error_load_config"), str(e))

    def _save_config(self):
        f = filedialog.asksaveasfilename(title=t("menu_save_config"),
                                         defaultextension=".yaml",
                                         filetypes=[("YAML", "*.yaml"), ("All", "*.*")],
                                         initialdir=str(self.config_dir))
        if f:
            with open(f, "w", encoding="utf-8") as fh:
                yaml.dump(self._get_cfg(), fh, allow_unicode=True, default_flow_style=False)
            self._log(f"Config saved: {f}")

    # ---- Config helpers ----
    def _load_defaults(self):
        hyp = self.config_dir / "hyp_train.yaml"
        if hyp.exists():
            try:
                with open(hyp, "r", encoding="utf-8") as f:
                    self._apply_cfg(yaml.safe_load(f))
            except Exception:
                pass

    def _apply_cfg(self, cfg):
        m = {"model": self.model_var, "epochs": self.epochs_var, "batch": self.batch_var,
             "imgsz": self.imgsz_var, "optimizer": self.optimizer_var, "lr0": self.lr_var,
             "weight_decay": self.wd_var, "patience": self.patience_var, "amp": self.amp_var,
             "label_smoothing": self.smooth_var, "mosaic": self.mosaic_var, "mixup": self.mixup_var,
             "fliplr": self.fliplr_var, "degrees": self.degrees_var, "scale": self.scale_var,
             "hsv_s": self.hsv_s_var, "hsv_v": self.hsv_v_var, "erasing": self.erasing_var,
             "name": self.name_var, "device": self.device_var, "workers": self.workers_var,
             "data": self.data_var}
        for k, v in m.items():
            if k in cfg:
                try:
                    v.set(cfg[k])
                except Exception:
                    pass

    def _get_cfg(self):
        return {"model": self.model_var.get(), "data": self.data_var.get(),
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
                "project": "runs/train"}

    # ---- Training ----
    def _start(self):
        if not self.data_var.get():
            messagebox.showerror("Error", t("error_no_dataset"))
            return
        if self.is_training:
            return
        cfg = self._get_cfg()
        cmd = [sys.executable, str(self.project_root / "training" / "scripts" / "train.py"),
               "--data", cfg["data"], "--model", cfg["model"],
               "--epochs", str(cfg["epochs"]), "--batch", str(cfg["batch"]),
               "--imgsz", str(cfg["imgsz"]), "--device", str(cfg["device"]),
               "--name", cfg["name"]]
        self._log("=" * 50)
        self._log(f"{t('start_training')}...")
        self._log(f"CMD: {' '.join(cmd)}")
        self._log("=" * 50)
        self.is_training = True
        self.btn_start.config(state=tk.DISABLED)
        self.btn_stop.config(state=tk.NORMAL)
        self.status_lbl_var.set(t("training_in_progress"))
        threading.Thread(target=self._run, args=(cmd,), daemon=True).start()

    def _run(self, cmd):
        try:
            flags = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0
            self.train_proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                               universal_newlines=True, bufsize=1, creationflags=flags)
            for line in self.train_proc.stdout:
                self.root.after(0, self._log, line.strip())
            self.train_proc.wait()
            rc = self.train_proc.returncode
            msg = t("training_complete") if rc == 0 else f"{t('training_failed')} (exit {rc})"
            self.root.after(0, self._log, f"\n{msg}")
            self.root.after(0, self.status_lbl_var.set, msg)
        except Exception as e:
            self.root.after(0, self._log, f"\nError: {e}")
        finally:
            self.root.after(0, self._done)

    def _done(self):
        self.is_training = False
        self.btn_start.config(state=tk.NORMAL)
        self.btn_stop.config(state=tk.DISABLED)
        self.train_proc = None

    def _stop(self):
        if self.train_proc and self.is_training:
            if messagebox.askyesno("?", t("error_confirm_stop")):
                self.train_proc.terminate()
                self._log(t("training_stopped"))
                self.status_lbl_var.set(t("training_stopped"))

    # ---- Tools ----
    def _validate_dataset(self):
        if not self.data_var.get():
            messagebox.showerror("Error", t("error_no_dataset_first"))
            return
        cmd = [sys.executable, str(self.project_root / "training" / "scripts" / "dataset_validator.py"),
               "--data", self.data_var.get()]
        self._log(f"{t('validate')}...")
        threading.Thread(target=self._run, args=(cmd,), daemon=True).start()

    def _export_model(self):
        w = filedialog.askopenfilename(title=t("menu_export"),
                                       filetypes=[("PyTorch", "*.pt"), ("All", "*.*")],
                                       initialdir=str(self.project_root / "runs" / "train"))
        if not w:
            return
        a = filedialog.askdirectory(title=t("menu_export"),
                                    initialdir=str(self.project_root / "ncnn-android-yolov11" /
                                                   "app" / "src" / "main" / "assets" / "models"))
        if a:
            cmd = [sys.executable, str(self.project_root / "training" / "tools" / "export_pipeline.py"),
                   "--weights", w, "--assets", a]
            self._log(f"{t('export')}...")
            threading.Thread(target=self._run, args=(cmd,), daemon=True).start()

    def _visualize(self):
        d = filedialog.askdirectory(title=t("menu_visualize"),
                                    initialdir=str(self.project_root / "runs" / "train"))
        if d:
            cmd = [sys.executable, str(self.project_root / "training" / "tools" / "visualize_results.py"),
                   "--run-dir", d]
            self._log(f"{t('menu_visualize')}...")
            threading.Thread(target=self._run, args=(cmd,), daemon=True).start()

    def _check_env(self):
        self._log(f"{t('menu_env_check')}...")
        checks = [f"{t('env_python')}: {sys.version}"]
        for name in ["torch", "ultralytics", "cv2"]:
            try:
                m = __import__(name)
                checks.append(f"{name}: {getattr(m, '__version__', 'installed')}")
            except ImportError:
                checks.append(f"{name}: {t('env_not_installed')}")
        try:
            import torch
            checks.append(f"{t('env_gpu')}: {torch.cuda.get_device_name(0) if torch.cuda.is_available() else t('env_not_available')}")
        except Exception:
            pass
        self._log("\n".join(checks))

    def _show_about(self):
        messagebox.showinfo(t("about_title"), t("about_text"))

    # ---- Logging ----
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
# Entry
# ============================================================
def main():
    root = tk.Tk()
    try:
        from ctypes import windll
        windll.shcore.SetProcessDpiAwareness(1)
    except Exception:
        pass
    TrainingGUI(root)
    root.mainloop()


if __name__ == "__main__":
    main()
