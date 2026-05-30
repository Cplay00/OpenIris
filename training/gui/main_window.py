#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenIris Training GUI - Main Window

基于 tkinter 的 YOLO 模型训练图形界面。
提供数据集管理、训练配置、实时监控等功能。
"""

import tkinter as tk
from tkinter import ttk, filedialog, messagebox, scrolledtext
import threading
import subprocess
import sys
import os
from pathlib import Path
import json
import yaml
from datetime import datetime


class TrainingGUI:
    """YOLO 训练图形界面"""

    def __init__(self, root):
        self.root = root
        self.root.title("OpenIris YOLO 训练平台 v1.0")
        self.root.geometry("1200x800")
        self.root.minsize(1000, 700)

        # 训练进程
        self.train_process = None
        self.is_training = False

        # 配置文件路径
        self.project_root = Path(__file__).parent.parent.parent
        self.config_dir = self.project_root / "training" / "configs"

        # 创建界面
        self._create_menu()
        self._create_main_layout()
        self._create_status_bar()

        # 加载默认配置
        self._load_default_config()

    def _create_menu(self):
        """创建菜单栏"""
        menubar = tk.Menu(self.root)
        self.root.config(menu=menubar)

        # 文件菜单
        file_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="文件", menu=file_menu)
        file_menu.add_command(label="加载配置", command=self._load_config)
        file_menu.add_command(label="保存配置", command=self._save_config)
        file_menu.add_separator()
        file_menu.add_command(label="退出", command=self.root.quit)

        # 数据集菜单
        dataset_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="数据集", menu=dataset_menu)
        dataset_menu.add_command(label="数据集向导", command=self._dataset_wizard)
        dataset_menu.add_command(label="格式转换", command=self._format_converter)
        dataset_menu.add_command(label="数据集校验", command=self._validate_dataset)

        # 工具菜单
        tools_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="工具", menu=tools_menu)
        tools_menu.add_command(label="模型导出", command=self._export_model)
        tools_menu.add_command(label="训练可视化", command=self._visualize_results)
        tools_menu.add_separator()
        tools_menu.add_command(label="环境检测", command=self._check_environment)

        # 帮助菜单
        help_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="帮助", menu=help_menu)
        help_menu.add_command(label="使用说明", command=self._show_help)
        help_menu.add_command(label="关于", command=self._show_about)

    def _create_main_layout(self):
        """创建主布局"""
        # 主框架
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.pack(fill=tk.BOTH, expand=True)

        # 左侧配置面板
        left_frame = ttk.LabelFrame(main_frame, text="训练配置", padding="10")
        left_frame.pack(side=tk.LEFT, fill=tk.BOTH, expand=False, padx=(0, 10))

        # 右侧控制和日志面板
        right_frame = ttk.Frame(main_frame)
        right_frame.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        # 创建配置面板
        self._create_config_panel(left_frame)

        # 创建控制面板
        self._create_control_panel(right_frame)

        # 创建日志面板
        self._create_log_panel(right_frame)

    def _create_config_panel(self, parent):
        """创建配置面板"""
        # 使用 Notebook (标签页)
        notebook = ttk.Notebook(parent)
        notebook.pack(fill=tk.BOTH, expand=True)

        # 基础配置标签页
        basic_frame = ttk.Frame(notebook, padding="10")
        notebook.add(basic_frame, text="基础配置")
        self._create_basic_config(basic_frame)

        # 高级配置标签页
        advanced_frame = ttk.Frame(notebook, padding="10")
        notebook.add(advanced_frame, text="高级配置")
        self._create_advanced_config(advanced_frame)

        # 数据增强标签页
        augment_frame = ttk.Frame(notebook, padding="10")
        notebook.add(augment_frame, text="数据增强")
        self._create_augment_config(augment_frame)

    def _create_basic_config(self, parent):
        """创建基础配置面板"""
        row = 0

        # 数据集配置
        ttk.Label(parent, text="数据集配置:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.data_var = tk.StringVar()
        ttk.Entry(parent, textvariable=self.data_var, width=30).grid(row=row, column=1, pady=5)
        ttk.Button(parent, text="浏览", command=self._browse_data).grid(row=row, column=2, pady=5)

        row += 1

        # 模型选择
        ttk.Label(parent, text="预训练模型:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.model_var = tk.StringVar(value="yolov11n.pt")
        model_combo = ttk.Combobox(parent, textvariable=self.model_var, values=[
            "yolov11n.pt", "yolov11s.pt", "yolov11m.pt", "yolov11l.pt", "yolov11x.pt"
        ], state="readonly", width=27)
        model_combo.grid(row=row, column=1, columnspan=2, pady=5, sticky=tk.W+tk.E)

        row += 1

        # 训练轮次
        ttk.Label(parent, text="训练轮次:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.epochs_var = tk.IntVar(value=100)
        ttk.Spinbox(parent, from_=1, to=1000, textvariable=self.epochs_var, width=10).grid(
            row=row, column=1, sticky=tk.W, pady=5)

        row += 1

        # 批次大小
        ttk.Label(parent, text="批次大小:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.batch_var = tk.IntVar(value=16)
        ttk.Spinbox(parent, from_=1, to=256, textvariable=self.batch_var, width=10).grid(
            row=row, column=1, sticky=tk.W, pady=5)

        row += 1

        # 输入尺寸
        ttk.Label(parent, text="输入尺寸:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.imgsz_var = tk.IntVar(value=640)
        imgsz_combo = ttk.Combobox(parent, textvariable=self.imgsz_var, values=[
            320, 416, 512, 640, 800, 1024
        ], state="readonly", width=10)
        imgsz_combo.grid(row=row, column=1, sticky=tk.W, pady=5)

        row += 1

        # 设备选择
        ttk.Label(parent, text="训练设备:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.device_var = tk.StringVar(value="0")
        device_combo = ttk.Combobox(parent, textvariable=self.device_var, values=[
            "0", "0,1", "cpu"
        ], state="readonly", width=10)
        device_combo.grid(row=row, column=1, sticky=tk.W, pady=5)

        row += 1

        # 实验名称
        ttk.Label(parent, text="实验名称:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.name_var = tk.StringVar(value="openiris_v1")
        ttk.Entry(parent, textvariable=self.name_var, width=30).grid(
            row=row, column=1, columnspan=2, pady=5)

    def _create_advanced_config(self, parent):
        """创建高级配置面板"""
        row = 0

        # 优化器
        ttk.Label(parent, text="优化器:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.optimizer_var = tk.StringVar(value="AdamW")
        ttk.Combobox(parent, textvariable=self.optimizer_var, values=[
            "SGD", "Adam", "AdamW", "auto"
        ], state="readonly", width=15).grid(row=row, column=1, sticky=tk.W, pady=5)

        row += 1

        # 学习率
        ttk.Label(parent, text="学习率:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.lr_var = tk.DoubleVar(value=0.001)
        ttk.Entry(parent, textvariable=self.lr_var, width=15).grid(row=row, column=1, sticky=tk.W, pady=5)

        row += 1

        # 权重衰减
        ttk.Label(parent, text="权重衰减:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.weight_decay_var = tk.DoubleVar(value=0.0005)
        ttk.Entry(parent, textvariable=self.weight_decay_var, width=15).grid(row=row, column=1, sticky=tk.W, pady=5)

        row += 1

        # 早停轮次
        ttk.Label(parent, text="早停轮次:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.patience_var = tk.IntVar(value=50)
        ttk.Spinbox(parent, from_=0, to=200, textvariable=self.patience_var, width=10).grid(
            row=row, column=1, sticky=tk.W, pady=5)

        row += 1

        # 混合精度
        self.amp_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(parent, text="混合精度训练 (AMP)", variable=self.amp_var).grid(
            row=row, column=0, columnspan=2, sticky=tk.W, pady=5)

        row += 1

        # EMA
        self.ema_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(parent, text="指数移动平均 (EMA)", variable=self.ema_var).grid(
            row=row, column=0, columnspan=2, sticky=tk.W, pady=5)

        row += 1

        # 标签平滑
        ttk.Label(parent, text="标签平滑:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.smoothing_var = tk.DoubleVar(value=0.0)
        ttk.Entry(parent, textvariable=self.smoothing_var, width=15).grid(row=row, column=1, sticky=tk.W, pady=5)

    def _create_augment_config(self, parent):
        """创建数据增强配置面板"""
        row = 0

        # Mosaic
        ttk.Label(parent, text="Mosaic:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.mosaic_var = tk.DoubleVar(value=1.0)
        ttk.Scale(parent, from_=0, to=1, variable=self.mosaic_var, orient=tk.HORIZONTAL).grid(
            row=row, column=1, sticky=tk.W+tk.E, pady=5)

        row += 1

        # Mixup
        ttk.Label(parent, text="Mixup:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.mixup_var = tk.DoubleVar(value=0.0)
        ttk.Scale(parent, from_=0, to=1, variable=self.mixup_var, orient=tk.HORIZONTAL).grid(
            row=row, column=1, sticky=tk.W+tk.E, pady=5)

        row += 1

        # 翻转
        ttk.Label(parent, text="水平翻转:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.fliplr_var = tk.DoubleVar(value=0.5)
        ttk.Scale(parent, from_=0, to=1, variable=self.fliplr_var, orient=tk.HORIZONTAL).grid(
            row=row, column=1, sticky=tk.W+tk.E, pady=5)

        row += 1

        # 旋转
        ttk.Label(parent, text="旋转角度:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.degrees_var = tk.DoubleVar(value=0.0)
        ttk.Scale(parent, from_=0, to=45, variable=self.degrees_var, orient=tk.HORIZONTAL).grid(
            row=row, column=1, sticky=tk.W+tk.E, pady=5)

        row += 1

        # 缩放
        ttk.Label(parent, text="缩放范围:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.scale_var = tk.DoubleVar(value=0.5)
        ttk.Scale(parent, from_=0, to=1, variable=self.scale_var, orient=tk.HORIZONTAL).grid(
            row=row, column=1, sticky=tk.W+tk.E, pady=5)

        row += 1

        # HSV
        ttk.Label(parent, text="HSV-饱和度:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.hsv_s_var = tk.DoubleVar(value=0.7)
        ttk.Scale(parent, from_=0, to=1, variable=self.hsv_s_var, orient=tk.HORIZONTAL).grid(
            row=row, column=1, sticky=tk.W+tk.E, pady=5)

        row += 1

        ttk.Label(parent, text="HSV-明度:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.hsv_v_var = tk.DoubleVar(value=0.4)
        ttk.Scale(parent, from_=0, to=1, variable=self.hsv_v_var, orient=tk.HORIZONTAL).grid(
            row=row, column=1, sticky=tk.W+tk.E, pady=5)

        row += 1

        # 随机擦除
        ttk.Label(parent, text="随机擦除:").grid(row=row, column=0, sticky=tk.W, pady=5)
        self.erasing_var = tk.DoubleVar(value=0.0)
        ttk.Scale(parent, from_=0, to=1, variable=self.erasing_var, orient=tk.HORIZONTAL).grid(
            row=row, column=1, sticky=tk.W+tk.E, pady=5)

    def _create_control_panel(self, parent):
        """创建控制面板"""
        control_frame = ttk.LabelFrame(parent, text="训练控制", padding="10")
        control_frame.pack(fill=tk.X, pady=(0, 10))

        # 按钮行
        btn_frame = ttk.Frame(control_frame)
        btn_frame.pack(fill=tk.X)

        self.start_btn = ttk.Button(btn_frame, text="开始训练", command=self._start_training)
        self.start_btn.pack(side=tk.LEFT, padx=5)

        self.stop_btn = ttk.Button(btn_frame, text="停止训练", command=self._stop_training, state=tk.DISABLED)
        self.stop_btn.pack(side=tk.LEFT, padx=5)

        ttk.Button(btn_frame, text="验证数据集", command=self._validate_dataset).pack(side=tk.LEFT, padx=5)
        ttk.Button(btn_frame, text="导出模型", command=self._export_model).pack(side=tk.LEFT, padx=5)

        # 进度条
        self.progress_var = tk.DoubleVar(value=0)
        self.progress_bar = ttk.Progressbar(control_frame, variable=self.progress_var, maximum=100)
        self.progress_bar.pack(fill=tk.X, pady=(10, 0))

        # 状态标签
        self.status_var = tk.StringVar(value="就绪")
        ttk.Label(control_frame, textvariable=self.status_var).pack(anchor=tk.W, pady=(5, 0))

    def _create_log_panel(self, parent):
        """创建日志面板"""
        log_frame = ttk.LabelFrame(parent, text="训练日志", padding="10")
        log_frame.pack(fill=tk.BOTH, expand=True)

        # 日志文本框
        self.log_text = scrolledtext.ScrolledText(log_frame, height=15, state=tk.DISABLED)
        self.log_text.pack(fill=tk.BOTH, expand=True)

        # 清空按钮
        ttk.Button(log_frame, text="清空日志", command=self._clear_log).pack(anchor=tk.E, pady=(5, 0))

    def _create_status_bar(self):
        """创建状态栏"""
        status_frame = ttk.Frame(self.root)
        status_frame.pack(fill=tk.X, side=tk.BOTTOM)

        ttk.Label(status_frame, text="OpenIris YOLO 训练平台 | ").pack(side=tk.LEFT)
        ttk.Label(status_frame, text="版本 1.0").pack(side=tk.LEFT)

    def _load_default_config(self):
        """加载默认配置"""
        hyp_file = self.config_dir / "hyp_train.yaml"
        if hyp_file.exists():
            try:
                with open(hyp_file, "r", encoding="utf-8") as f:
                    config = yaml.safe_load(f)
                self._apply_config(config)
            except Exception as e:
                self._log(f"加载默认配置失败: {e}")

    def _apply_config(self, config):
        """应用配置到界面"""
        if "model" in config:
            self.model_var.set(config["model"])
        if "epochs" in config:
            self.epochs_var.set(config["epochs"])
        if "batch" in config:
            self.batch_var.set(config["batch"])
        if "imgsz" in config:
            self.imgsz_var.set(config["imgsz"])
        if "optimizer" in config:
            self.optimizer_var.set(config["optimizer"])
        if "lr0" in config:
            self.lr_var.set(config["lr0"])
        if "weight_decay" in config:
            self.weight_decay_var.set(config["weight_decay"])
        if "patience" in config:
            self.patience_var.set(config["patience"])
        if "amp" in config:
            self.amp_var.set(config["amp"])
        if "label_smoothing" in config:
            self.smoothing_var.set(config["label_smoothing"])

    def _browse_data(self):
        """浏览数据集配置文件"""
        filename = filedialog.askopenfilename(
            title="选择数据集配置文件",
            filetypes=[("YAML files", "*.yaml"), ("All files", "*.*")],
            initialdir=str(self.config_dir)
        )
        if filename:
            self.data_var.set(filename)

    def _load_config(self):
        """加载配置文件"""
        filename = filedialog.askopenfilename(
            title="加载训练配置",
            filetypes=[("YAML files", "*.yaml"), ("All files", "*.*")],
            initialdir=str(self.config_dir)
        )
        if filename:
            try:
                with open(filename, "r", encoding="utf-8") as f:
                    config = yaml.safe_load(f)
                self._apply_config(config)
                self._log(f"配置已加载: {filename}")
            except Exception as e:
                messagebox.showerror("错误", f"加载配置失败: {e}")

    def _save_config(self):
        """保存配置文件"""
        filename = filedialog.asksaveasfilename(
            title="保存训练配置",
            defaultextension=".yaml",
            filetypes=[("YAML files", "*.yaml"), ("All files", "*.*")],
            initialdir=str(self.config_dir)
        )
        if filename:
            config = self._get_current_config()
            try:
                with open(filename, "w", encoding="utf-8") as f:
                    yaml.dump(config, f, allow_unicode=True, default_flow_style=False)
                self._log(f"配置已保存: {filename}")
            except Exception as e:
                messagebox.showerror("错误", f"保存配置失败: {e}")

    def _get_current_config(self):
        """获取当前界面配置"""
        return {
            "model": self.model_var.get(),
            "epochs": self.epochs_var.get(),
            "batch": self.batch_var.get(),
            "imgsz": self.imgsz_var.get(),
            "device": self.device_var.get(),
            "optimizer": self.optimizer_var.get(),
            "lr0": self.lr_var.get(),
            "weight_decay": self.weight_decay_var.get(),
            "patience": self.patience_var.get(),
            "amp": self.amp_var.get(),
            "label_smoothing": self.smoothing_var.get(),
            "mosaic": self.mosaic_var.get(),
            "mixup": self.mixup_var.get(),
            "fliplr": self.fliplr_var.get(),
            "degrees": self.degrees_var.get(),
            "scale": self.scale_var.get(),
            "hsv_s": self.hsv_s_var.get(),
            "hsv_v": self.hsv_v_var.get(),
            "erasing": self.erasing_var.get(),
            "name": self.name_var.get(),
            "project": "runs/train",
            "data": self.data_var.get(),
        }

    def _start_training(self):
        """开始训练"""
        if not self.data_var.get():
            messagebox.showerror("错误", "请先选择数据集配置文件")
            return

        if self.is_training:
            messagebox.showwarning("警告", "训练正在进行中")
            return

        # 获取配置
        config = self._get_current_config()

        # 构建训练命令
        cmd = self._build_train_command(config)

        self._log("=" * 60)
        self._log("开始训练")
        self._log(f"命令: {' '.join(cmd)}")
        self._log("=" * 60)

        # 更新界面状态
        self.is_training = True
        self.start_btn.config(state=tk.DISABLED)
        self.stop_btn.config(state=tk.NORMAL)
        self.status_var.set("训练中...")

        # 在新线程中运行训练
        self.train_thread = threading.Thread(target=self._run_training, args=(cmd,))
        self.train_thread.daemon = True
        self.train_thread.start()

    def _build_train_command(self, config):
        """构建训练命令"""
        cmd = [
            sys.executable,
            str(self.project_root / "training" / "scripts" / "train.py"),
            "--data", config["data"],
            "--model", config["model"],
            "--epochs", str(config["epochs"]),
            "--batch", str(config["batch"]),
            "--imgsz", str(config["imgsz"]),
            "--device", config["device"],
            "--name", config["name"],
        ]

        return cmd

    def _run_training(self, cmd):
        """运行训练进程"""
        try:
            self.train_process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                universal_newlines=True,
                bufsize=1
            )

            # 实时读取输出
            for line in self.train_process.stdout:
                self.root.after(0, self._log, line.strip())

            # 等待进程结束
            self.train_process.wait()

            if self.train_process.returncode == 0:
                self.root.after(0, self._log, "\n训练完成!")
                self.root.after(0, self.status_var.set, "训练完成")
            else:
                self.root.after(0, self._log, f"\n训练失败，返回码: {self.train_process.returncode}")
                self.root.after(0, self.status_var.set, "训练失败")

        except Exception as e:
            self.root.after(0, self._log, f"\n训练错误: {e}")
            self.root.after(0, self.status_var.set, "训练错误")

        finally:
            self.root.after(0, self._training_finished)

    def _training_finished(self):
        """训练结束回调"""
        self.is_training = False
        self.start_btn.config(state=tk.NORMAL)
        self.stop_btn.config(state=tk.DISABLED)
        self.train_process = None

    def _stop_training(self):
        """停止训练"""
        if self.train_process and self.is_training:
            if messagebox.askyesno("确认", "确定要停止训练吗？"):
                self.train_process.terminate()
                self._log("训练已停止")
                self.status_var.set("已停止")

    def _validate_dataset(self):
        """验证数据集"""
        if not self.data_var.get():
            messagebox.showerror("错误", "请先选择数据集配置文件")
            return

        self._log("开始验证数据集...")

        cmd = [
            sys.executable,
            str(self.project_root / "training" / "scripts" / "dataset_validator.py"),
            "--data", self.data_var.get()
        ]

        threading.Thread(target=self._run_command, args=(cmd,), daemon=True).start()

    def _export_model(self):
        """导出模型"""
        weights = filedialog.askopenfilename(
            title="选择模型权重文件",
            filetypes=[("PyTorch files", "*.pt"), ("All files", "*.*")],
            initialdir=str(self.project_root / "runs" / "train")
        )

        if weights:
            assets_dir = filedialog.askdirectory(
                title="选择 Android assets 目录",
                initialdir=str(self.project_root / "ncnn-android-yolov11" / "app" / "src" / "main" / "assets" / "models")
            )

            if assets_dir:
                cmd = [
                    sys.executable,
                    str(self.project_root / "training" / "tools" / "export_pipeline.py"),
                    "--weights", weights,
                    "--assets", assets_dir
                ]

                self._log("开始导出模型...")
                threading.Thread(target=self._run_command, args=(cmd,), daemon=True).start()

    def _run_command(self, cmd):
        """运行命令并输出日志"""
        try:
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                universal_newlines=True,
                bufsize=1
            )

            for line in process.stdout:
                self.root.after(0, self._log, line.strip())

            process.wait()

            if process.returncode == 0:
                self.root.after(0, self._log, "命令执行完成")
            else:
                self.root.after(0, self._log, f"命令失败，返回码: {process.returncode}")

        except Exception as e:
            self.root.after(0, self._log, f"命令执行错误: {e}")

    def _dataset_wizard(self):
        """数据集向导"""
        messagebox.showinfo("数据集向导", "数据集向导功能开发中...")

    def _format_converter(self):
        """格式转换"""
        messagebox.showinfo("格式转换", "格式转换功能开发中...")

    def _visualize_results(self):
        """训练可视化"""
        run_dir = filedialog.askdirectory(
            title="选择训练结果目录",
            initialdir=str(self.project_root / "runs" / "train")
        )

        if run_dir:
            cmd = [
                sys.executable,
                str(self.project_root / "training" / "tools" / "visualize_results.py"),
                "--run-dir", run_dir
            ]

            self._log("生成训练可视化...")
            threading.Thread(target=self._run_command, args=(cmd,), daemon=True).start()

    def _check_environment(self):
        """检查环境"""
        self._log("检查训练环境...")

        checks = []

        # 检查 Python
        checks.append(f"Python: {sys.version}")

        # 检查 PyTorch
        try:
            import torch
            checks.append(f"PyTorch: {torch.__version__}")
            checks.append(f"CUDA: {torch.cuda.is_available()}")
            if torch.cuda.is_available():
                checks.append(f"GPU: {torch.cuda.get_device_name(0)}")
        except ImportError:
            checks.append("PyTorch: 未安装")

        # 检查 ultralytics
        try:
            import ultralytics
            checks.append(f"Ultralytics: {ultralytics.__version__}")
        except ImportError:
            checks.append("Ultralytics: 未安装")

        # 检查 OpenCV
        try:
            import cv2
            checks.append(f"OpenCV: {cv2.__version__}")
        except ImportError:
            checks.append("OpenCV: 未安装")

        # 检查 CUDA 工具
        try:
            result = subprocess.run(["nvidia-smi"], capture_output=True, text=True)
            if result.returncode == 0:
                checks.append("NVIDIA Driver: 已安装")
            else:
                checks.append("NVIDIA Driver: 未安装或不可用")
        except FileNotFoundError:
            checks.append("NVIDIA Driver: 未安装")

        self._log("\n环境检查结果:")
        self._log("-" * 40)
        for check in checks:
            self._log(f"  {check}")
        self._log("-" * 40)

    def _show_help(self):
        """显示帮助"""
        help_text = """OpenIris YOLO 训练平台使用说明

1. 数据集准备:
   - 准备 YOLO 格式的数据集
   - 使用"数据集向导"创建数据集
   - 使用"验证数据集"检查格式

2. 训练配置:
   - 选择预训练模型 (推荐 yolov11n.pt)
   - 设置训练轮次 (推荐 100-200)
   - 调整批次大小 (根据显存)
   - 配置数据增强参数

3. 开始训练:
   - 点击"开始训练"按钮
   - 实时查看训练日志
   - 使用"停止训练"中断训练

4. 模型导出:
   - 训练完成后选择最优权重
   - 导出为 NCNN 格式
   - 同步到 Android assets

快捷键:
  Ctrl+S: 保存配置
  Ctrl+O: 加载配置
  Ctrl+T: 开始训练
  Ctrl+P: 停止训练
"""
        help_window = tk.Toplevel(self.root)
        help_window.title("使用说明")
        help_window.geometry("500x400")

        text = scrolledtext.ScrolledText(help_window, wrap=tk.WORD)
        text.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        text.insert(tk.END, help_text)
        text.config(state=tk.DISABLED)

    def _show_about(self):
        """显示关于"""
        messagebox.showinfo(
            "关于",
            "OpenIris YOLO 训练平台\n\n"
            "版本: 1.0\n"
            "基于: Ultralytics YOLOv11\n"
            "目标: Android NCNN 部署\n\n"
            "© 2024 OpenIris Team"
        )

    def _log(self, message):
        """添加日志"""
        self.log_text.config(state=tk.NORMAL)
        timestamp = datetime.now().strftime("%H:%M:%S")
        self.log_text.insert(tk.END, f"[{timestamp}] {message}\n")
        self.log_text.see(tk.END)
        self.log_text.config(state=tk.DISABLED)

    def _clear_log(self):
        """清空日志"""
        self.log_text.config(state=tk.NORMAL)
        self.log_text.delete(1.0, tk.END)
        self.log_text.config(state=tk.DISABLED)


def main():
    """主函数"""
    root = tk.Tk()

    # 设置主题
    style = ttk.Style()
    style.theme_use("clam")  # 可选: clam, alt, default, classic

    app = TrainingGUI(root)

    # 绑定快捷键
    root.bind("<Control-s>", lambda e: app._save_config())
    root.bind("<Control-o>", lambda e: app._load_config())
    root.bind("<Control-t>", lambda e: app._start_training())
    root.bind("<Control-p>", lambda e: app._stop_training())

    root.mainloop()


if __name__ == "__main__":
    main()
