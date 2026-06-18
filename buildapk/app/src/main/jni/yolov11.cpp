#include "yolov11.h"
#include <cpu.h>
#include <iostream>
#include <vector>
#include <android/asset_manager.h>
#include <android/log.h>
#include <fstream>
#include <sstream>


// 快速指数
static float fast_exp(float x)
{
    union {
        uint32_t i;
        float f;
    } v{};
    v.i = (1 << 23) * (1.4426950409 * x + 126.93490512f);
    return v.f;
}


static float softmax(
	const float* src,
	float* dst,
	int length
)
{
	float alpha = -FLT_MAX;
	for (int c = 0; c < length; c++)
	{
		float score = src[c];
		if (score > alpha)
		{
			alpha = score;
		}
	}

	float denominator = 0;
	float dis_sum = 0;
	for (int i = 0; i < length; ++i)
	{
		// dst[i] = expf(src[i] - alpha);
		dst[i] = fast_exp(src[i] - alpha); // // 使用 fast_exp 代替 expf
		
		denominator += dst[i];
	}
	for (int i = 0; i < length; ++i)
	{
		dst[i] /= denominator;
		dis_sum += i * dst[i];
	}
	return dis_sum;
}

static void generate_proposals(
        int stride,
        const ncnn::Mat& feat_blob,
        const float prob_threshold,
        std::vector<Object>& objects
)
{
    const int num_w = feat_blob.w;
    const int num_h = feat_blob.h;
    const int num_c = feat_blob.c;

    // Auto-detect format based on blob dimensions
    // Decoded: w < 64 (ultralytics post-processed)
    // Raw DFL: w >= 64 (raw YOLO output)
    // 格式检测: 检查是否包含 DFL 编码的 64 通道
    // 条件: c > 1 且 w >= 4*reg_max (即 64)
    // DFL 格式特征: c > 1 且 c >= 4*reg_max (即 64), w 和 h 是空间维度
    // ultralytics decoded 格式特征: c == 1, h 是每锚值数, w 是锚点数
    // 内置模型经过 Permute 0=3 + Concat axis=w 后,特征沿 w 维度(144),空间沿 c 和 h 维度
    // 因此需要同时检查 c 和 w 是否包含 DFL 特征维度(>= 64)
    const bool is_decoded_format = !(num_c > 1 && num_w > 1 && num_h > 1 && (num_c >= 4 * 16 || num_w >= 4 * 16));

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn",
        "generate_proposals: stride=%d, c=%d, h=%d, w=%d, decoded=%d",
        stride, num_c, num_h, num_w, is_decoded_format ? 1 : 0);

    if (is_decoded_format)
    {
        // 检测数据布局
        bool values_in_w;
        int num_anchors, num_values;

        if (num_c == 1) {
            // 2D blob: [c=1, h=values, w=anchors]
            // ultralytics 导出: [c=1, h=4+nc, w=8400]
            values_in_w = false;
            num_anchors = num_w;
            num_values = num_h;
        } else if (num_c > 1) {
            // 3D blob: [c=anchors, h=values, w=1] 或 [c=anchors, h=1, w=values]
            values_in_w = (num_w > num_h);
            num_anchors = num_c;
            num_values = values_in_w ? num_w : num_h;
        } else {
            __android_log_print(ANDROID_LOG_ERROR, "ncnn",
                "generate_proposals: invalid decoded shape c=%d h=%d w=%d",
                num_c, num_h, num_w);
            return;
        }

        if (num_values < 5 || num_anchors <= 0) {
            __android_log_print(ANDROID_LOG_ERROR, "ncnn",
                "generate_proposals: invalid decoded shape c=%d h=%d w=%d",
                num_c, num_h, num_w);
            return;
        }

        for (int i = 0; i < num_anchors; i++)
        {
            float cx, cy, bw, bh, max_prob;
            int class_index = 0;

            if (values_in_w) {
                const float* ptr = feat_blob.channel(i).row(0);
                cx = ptr[0]; cy = ptr[1]; bw = ptr[2]; bh = ptr[3];
                max_prob = ptr[4];
                for (int c = 4; c < num_values; c++)
                    if (ptr[c] > max_prob) { max_prob = ptr[c]; class_index = c - 4; }
            } else {
                if (num_c == 1) {
                    // 2D blob: [c=1, h=values, w=anchors]
                    // 数据布局: row(value_index)[anchor_index]
                    cx = feat_blob.channel(0).row(0)[i];
                    cy = feat_blob.channel(0).row(1)[i];
                    bw = feat_blob.channel(0).row(2)[i];
                    bh = feat_blob.channel(0).row(3)[i];
                    max_prob = feat_blob.channel(0).row(4)[i];
                    for (int c = 4; c < num_values; c++) {
                        float s = feat_blob.channel(0).row(c)[i];
                        if (s > max_prob) { max_prob = s; class_index = c - 4; }
                    }
                } else {
                    // 3D blob: [c=anchors, h=values, w=1]
                    // 数据布局: channel(anchor_index).row(value_index)[0]
                    cx = feat_blob.channel(i).row(0)[0];
                    cy = feat_blob.channel(i).row(1)[0];
                    bw = feat_blob.channel(i).row(2)[0];
                    bh = feat_blob.channel(i).row(3)[0];
                    max_prob = feat_blob.channel(i).row(4)[0];
                    for (int c = 4; c < num_values; c++) {
                        float s = feat_blob.channel(i).row(c)[0];
                        if (s > max_prob) { max_prob = s; class_index = c - 4; }
                    }
                }
            }

            if (max_prob < prob_threshold)
                continue;

            Object obj;
            obj.rect.x = cx - bw * 0.5f;
            obj.rect.y = cy - bh * 0.5f;
            obj.rect.width = bw;
            obj.rect.height = bh;
            obj.label = class_index;
            obj.prob = max_prob;
            objects.push_back(obj);
        }
    }
    else
    {
        // Raw DFL format: [DFL_left(16), DFL_top(16), DFL_right(16), DFL_bottom(16), class_logits(nc)]
        const int reg_max = 16;
        float dst[16];
        const int num_class = num_w - 4 * reg_max;

        __android_log_print(ANDROID_LOG_DEBUG, "ncnn",
            "generate_proposals: DFL format, num_w=%d, num_class=%d, grid_y=%d, grid_x=%d, stride=%d",
            num_w, num_class, num_c, num_h, stride);

        for (int i = 0; i < num_c; i++)
        {
            for (int j = 0; j < num_h; j++)
            {
                const float* matat = feat_blob.channel(i).row(j);

                int class_index = 0;
                float class_score = -FLT_MAX;
                for (int c = 0; c < num_class; c++)
                {
                    float score = matat[4 * reg_max + c];
                    if (score > class_score)
                    {
                        class_index = c;
                        class_score = score;
                    }
                }

                if (class_score >= prob_threshold)
                {
                    float x0 = j + 0.5f - softmax(matat, dst, 16);
                    float y0 = i + 0.5f - softmax(matat + 16, dst, 16);
                    float x1 = j + 0.5f + softmax(matat + 2 * 16, dst, 16);
                    float y1 = i + 0.5f + softmax(matat + 3 * 16, dst, 16);

                    x0 *= stride;
                    y0 *= stride;
                    x1 *= stride;
                    y1 *= stride;

                    Object obj;
                    obj.rect.x = x0;
                    obj.rect.y = y0;
                    obj.rect.width = x1 - x0;
                    obj.rect.height = y1 - y0;
                    obj.label = class_index;
                    obj.prob = 1.0f / (1.0f + exp(-class_score));
                    objects.push_back(obj);
                }
            }
        }
    }
}

static float clamp(
        float val,
        float min = 0.f,
        float max = 1280.f
)
{
    return val > min ? (val < max ? val : max) : min;
}

// 计算 IoU (Intersection over Union) 的函数
static float compute_iou(const cv::Rect& box1, const cv::Rect& box2)
{
    float inter_area = (box1 & box2).area();
    float union_area = box1.area() + box2.area() - inter_area;
    return inter_area / union_area;
}


static void non_max_suppression(
        std::vector<Object>& proposals,
        std::vector<Object>& results,
        int orin_h,
        int orin_w,
        float dh = 0,
        float dw = 0,
        float ratio_h = 1.0f,
        float ratio_w = 1.0f,
        float conf_thres = 0.25f,
        float iou_thres = 0.65f
)
{
    results.clear();
    std::vector<cv::Rect> bboxes;
    std::vector<float> scores;
    std::vector<int> labels;
    std::vector<int> indices;

    // 1. 将 proposals 中的矩形框和分数提取出来
    for (auto& pro : proposals)
    {
        bboxes.push_back(pro.rect);
        scores.push_back(pro.prob);
        labels.push_back(pro.label);
    }

    // 2. 按照分数进行排序，排序后从高到低进行 NMS
    std::vector<int> sorted_indices(scores.size());
    for (size_t i = 0; i < scores.size(); ++i)
    {
        sorted_indices[i] = i;
    }

    std::sort(sorted_indices.begin(), sorted_indices.end(),
              [&scores](int i1, int i2) { return scores[i1] > scores[i2]; });

    // 3. 执行非最大抑制
    std::vector<bool> keep(scores.size(), true);
    for (size_t i = 0; i < sorted_indices.size(); ++i)
    {
        if (!keep[sorted_indices[i]]) continue;

        const auto& box_i = bboxes[sorted_indices[i]];
        float score_i = scores[sorted_indices[i]];

        if (score_i < conf_thres)
            break;

        for (size_t j = i + 1; j < sorted_indices.size(); ++j)
        {
            if (!keep[sorted_indices[j]]) continue;

            const auto& box_j = bboxes[sorted_indices[j]];
            float iou = compute_iou(box_i, box_j);

            // 如果 IOU 大于阈值，则将 box_j 去除
            if (iou > iou_thres)
            {
                keep[sorted_indices[j]] = false;
            }
        }
    }

    // 4. 将满足条件的框添加到结果中
    for (size_t i = 0; i < sorted_indices.size(); ++i)
    {
        if (keep[sorted_indices[i]])
        {
            const auto& bbox = bboxes[sorted_indices[i]];
            float score = scores[sorted_indices[i]];
            int label = labels[sorted_indices[i]];

            float x0 = bbox.x;
            float y0 = bbox.y;
            float x1 = bbox.x + bbox.width;
            float y1 = bbox.y + bbox.height;

            x0 = (x0 - dw) / ratio_w;
            y0 = (y0 - dh) / ratio_h;
            x1 = (x1 - dw) / ratio_w;
            y1 = (y1 - dh) / ratio_h;

            x0 = clamp(x0, 0.f, orin_w);
            y0 = clamp(y0, 0.f, orin_h);
            x1 = clamp(x1, 0.f, orin_w);
            y1 = clamp(y1, 0.f, orin_h);

            Object obj;
            obj.rect.x = x0;
            obj.rect.y = y0;
            obj.rect.width = x1 - x0;
            obj.rect.height = y1 - y0;
            obj.prob = score;
            obj.label = label;

            results.push_back(obj);
        }
    }
}

Inference::Inference(){
    blob_pool_allocator.set_size_compare_ratio(0.f);
    workspace_pool_allocator.set_size_compare_ratio(0.f);
}

int Inference::loadNcnnNetwork(AAssetManager* mgr, const char* modeltype , const int& modelInputShape, const float* meanVals, const float* normVals, bool useGpu)
{
    target_size = modelInputShape;
    gpuEnabled = useGpu;

    net.clear();
    blob_pool_allocator.clear();
    workspace_pool_allocator.clear();

    ncnn::set_cpu_powersave(2);
    ncnn::set_omp_num_threads(ncnn::get_big_cpu_count());

    net.opt = ncnn::Option();

#if NCNN_VULKAN
    net.opt.use_vulkan_compute = useGpu;
#endif

    net.opt.num_threads = ncnn::get_big_cpu_count();
    net.opt.blob_allocator = &blob_pool_allocator;
    net.opt.workspace_allocator = &workspace_pool_allocator;

    char parampath[256];
    char modelpath[256];

    sprintf(parampath, "models/yolov11%s/model.param", modeltype);
    sprintf(modelpath, "models/yolov11%s/model.bin", modeltype);

    int ret_param = net.load_param(mgr, parampath);
    int ret_model = net.load_model(mgr, modelpath);

    if (ret_param != 0 || ret_model != 0)
    {
        return -1;
    }

    // 加载标签
    loadLabels(mgr, modeltype);

    this->meanVals[0] = meanVals[0];
    this->meanVals[1] = meanVals[1];
    this->meanVals[2] = meanVals[2];
    this->normVals[0] = normVals[0];
    this->normVals[1] = normVals[1];
    this->normVals[2] = normVals[2];
    return 0;
}

int Inference::parseParamLayerCount(const char* paramPath)
{
    std::ifstream file(paramPath);
    if (!file.is_open())
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Failed to open param file: %s", paramPath);
        return -1;
    }

    std::string line;
    // 第一行: magic number
    if (!std::getline(file, line))
    {
        return -1;
    }

    // 第二行: layer_count blob_count
    if (!std::getline(file, line))
    {
        return -1;
    }

    int layer_count = 0;
    int blob_count = 0;
    std::istringstream iss(line);
    iss >> layer_count >> blob_count;

    return layer_count;
}

int Inference::parseParamInputSize(const char* paramPath)
{
    std::ifstream file(paramPath);
    if (!file.is_open())
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Failed to open param file: %s", paramPath);
        return 640;
    }

    std::string line;
    // 跳过 magic number
    if (!std::getline(file, line))
    {
        return 640;
    }

    // 跳过 layer_count blob_count
    if (!std::getline(file, line))
    {
        return 640;
    }

    // 遍历所有层，找到 Input 层
    while (std::getline(file, line))
    {
        std::istringstream iss(line);
        std::string layer_type, layer_name;
        int num_bottoms, num_tops;

        iss >> layer_type >> layer_name >> num_bottoms >> num_tops;

        // 跳过 bottoms 和 tops
        for (int i = 0; i < num_bottoms + num_tops; i++)
        {
            std::string dummy;
            iss >> dummy;
        }

        // 检查是否是 Input 层
        if (layer_type == "Input")
        {
            // 解析参数 0=1 1=3 2=640 3=640
            std::string param;
            int h = 0, w = 0;
            while (iss >> param)
            {
                size_t eq = param.find('=');
                if (eq != std::string::npos)
                {
                    int key = std::atoi(param.substr(0, eq).c_str());
                    int value = std::atoi(param.substr(eq + 1).c_str());
                    if (key == 2) h = value;  // 2 = height
                    if (key == 3) w = value;  // 3 = width
                }
            }

            if (h > 0 && h == w)
            {
                __android_log_print(ANDROID_LOG_DEBUG, "ncnn",
                    "Parsed Input shape from param: %dx%d", w, h);
                return h;
            }
            else if (h > 0 || w > 0)
            {
                int size = std::max(h, w);
                __android_log_print(ANDROID_LOG_DEBUG, "ncnn",
                    "Parsed non-square Input shape from param: %dx%d, using max=%d", w, h, size);
                return size;
            }
        }
    }

    // 未找到 Input 层或没有 shape 参数，返回默认值
    __android_log_print(ANDROID_LOG_WARN, "ncnn",
        "No Input layer with shape found in param file: %s, using default 640", paramPath);
    return 640;
}

std::string Inference::getModelInfo(const char* paramPath, const char* modelPath) const
{
    std::ostringstream info;

    // 模型输入尺寸
    info << "输入尺寸: " << target_size << "x" << target_size << "\n";

    // 参数层数
    int layer_count = parseParamLayerCount(paramPath);
    if (layer_count > 0)
    {
        info << "网络层数: " << layer_count << "\n";
    }

    // 标签数量
    info << "标签数量: " << class_names.size() << "\n";

    // param 文件大小
    {
        std::ifstream f(paramPath, std::ios::binary | std::ios::ate);
        if (f.is_open())
        {
            long size = f.tellg();
            if (size > 1024 * 1024)
                info << "Param 大小: " << (size / 1024 / 1024) << " MB\n";
            else
                info << "Param 大小: " << (size / 1024) << " KB\n";
        }
    }

    // bin 文件大小
    {
        std::ifstream f(modelPath, std::ios::binary | std::ios::ate);
        if (f.is_open())
        {
            long size = f.tellg();
            if (size > 1024 * 1024)
                info << "Model 大小: " << (size / 1024 / 1024) << " MB\n";
            else
                info << "Model 大小: " << (size / 1024) << " KB\n";
        }
    }

    return info.str();
}

int Inference::loadNcnnNetworkFromPath(const char* paramPath, const char* modelPath, const int& modelInputShape, const float* meanVals, const float* normVals, bool useGpu)
{
    target_size = modelInputShape;
    gpuEnabled = useGpu;

    net.clear();
    blob_pool_allocator.clear();
    workspace_pool_allocator.clear();

    ncnn::set_cpu_powersave(2);
    ncnn::set_omp_num_threads(ncnn::get_big_cpu_count());

    net.opt = ncnn::Option();

#if NCNN_VULKAN
    net.opt.use_vulkan_compute = useGpu;
#endif

    net.opt.num_threads = ncnn::get_big_cpu_count();
    net.opt.blob_allocator = &blob_pool_allocator;
    net.opt.workspace_allocator = &workspace_pool_allocator;

    int ret_param = net.load_param(paramPath);
    int ret_model = net.load_model(modelPath);

    if (ret_param != 0 || ret_model != 0)
    {
        return -1;
    }

    this->meanVals[0] = meanVals[0];
    this->meanVals[1] = meanVals[1];
    this->meanVals[2] = meanVals[2];
    this->normVals[0] = normVals[0];
    this->normVals[1] = normVals[1];
    this->normVals[2] = normVals[2];
    return 0;
}

int Inference::loadLabelsFromPath(const char* labelsPath)
{
    class_names.clear();

    FILE* fp = fopen(labelsPath, "r");
    if (fp)
    {
        bool first_line = true;
        char line[256];
        while (fgets(line, sizeof(line), fp))
        {
            // 去除换行符
            size_t len = strlen(line);
            while (len > 0 && (line[len-1] == '\n' || line[len-1] == '\r'))
            {
                line[--len] = '\0';
            }
            // 跳过 UTF-8 BOM (EF BB BF)
            if (first_line && len >= 3 &&
                (unsigned char)line[0] == 0xEF &&
                (unsigned char)line[1] == 0xBB &&
                (unsigned char)line[2] == 0xBF)
            {
                memmove(line, line + 3, len - 3 + 1);
                len -= 3;
            }
            first_line = false;

            if (len > 0)
            {
                class_names.push_back(line);
            }
        }
        fclose(fp);

        __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "Loaded %d labels from %s",
            (int)class_names.size(), labelsPath);
    }
    else
    {
        __android_log_print(ANDROID_LOG_WARN, "ncnn", "Failed to open labels file: %s", labelsPath);
    }

    // 如果没有加载到标签，使用默认 COCO 标签（与 loadLabels 保持一致）
    if (class_names.empty())
    {
        fprintf(stderr, "No labels loaded from path, using default COCO labels\n");
        const char* default_names[] = {
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
            "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
            "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
            "hair drier", "toothbrush"
        };
        for (const auto& name : default_names)
        {
            class_names.push_back(name);
        }
    }

    return 0;
}

std::vector<Object> Inference::runInference(const cv::Mat &bgr)
{
    const float prob_threshold = 0.25f;
    const float nms_threshold = 0.45f;

    int img_w = bgr.cols;
    int img_h = bgr.rows;

    int w = img_w;
    int h = img_h;

    float scale = 1.f;
    if (w > h) {
        scale = (float)target_size / w;
        w = target_size;
        h = (int)(h * scale);
    }
    else {
        scale = (float)target_size / h;
        h = target_size;
        w = (int)(w * scale);
    }

    ncnn::Mat in = ncnn::Mat::from_pixels_resize(bgr.data, ncnn::Mat::PIXEL_BGR2RGB, img_w, img_h, w, h);

    // int wpad = (w + 32 - 1) / 32 * 32 - w;
    // int hpad = (h + 32 - 1) / 32 * 32 - h;

    int wpad = target_size - w;
    int hpad = target_size - h;

    ncnn::Mat in_pad;
    ncnn::copy_make_border(in, in_pad, hpad / 2, hpad - hpad / 2, wpad / 2,  wpad - wpad / 2, ncnn::BORDER_CONSTANT, 114.f);

    in_pad.substract_mean_normalize(meanVals, normVals);

    ncnn::Extractor ex = net.create_extractor();

    ex.input("in0", in_pad);


    std::vector<Object> proposals;

    // Try extracting all available output blobs
    // ultralytics export: only "out0" (single output, all scales merged)
    // Built-in model: "out0"(stride8), "out1"(stride16), "out2"(stride32)
    const char* output_names[] = {"out0", "out1", "out2"};
    const int strides[] = {8, 16, 32};

    // Phase 1: Extract all available output blobs
    struct OutputBlob { ncnn::Mat mat; int stride; };
    std::vector<OutputBlob> output_blobs;
    for (int s = 0; s < 3; s++)
    {
        ncnn::Mat out;
        int ret = ex.extract(output_names[s], out);
        if (ret == 0 && !out.empty())
        {
            __android_log_print(ANDROID_LOG_DEBUG, "ncnn",
                "Extracted %s: w=%d, h=%d, c=%d",
                output_names[s], out.w, out.h, out.c);
            output_blobs.push_back({out, strides[s]});
        }
        else if (s == 0)
        {
            // out0 must exist
            __android_log_print(ANDROID_LOG_ERROR, "ncnn", "Failed to extract out0");
            return {};
        }
        // out1, out2 are optional (ultralytics single-output models won't have them)
    }

    // Phase 2: Generate proposals with per-blob format auto-detection
    for (const auto& blob : output_blobs)
    {
        std::vector<Object> objects_s;
        generate_proposals(blob.stride, blob.mat, prob_threshold, objects_s);
        proposals.insert(proposals.end(), objects_s.begin(), objects_s.end());
    }
    std::vector<Object> objects;

    non_max_suppression(proposals, objects,
                        img_h, img_w, hpad / 2, wpad / 2,
                        scale, scale, prob_threshold, nms_threshold);


    return objects;

}

int Inference::loadLabels(AAssetManager* mgr, const char* modeltype)
{
    class_names.clear();

    char labelspath[256];
    sprintf(labelspath, "models/yolov11%s/labels.txt", modeltype);

    AAsset* asset = AAssetManager_open(mgr, labelspath, AASSET_MODE_BUFFER);
    if (asset)
    {
        size_t length = AAsset_getLength(asset);
        std::string content(length, '\0');
        AAsset_read(asset, &content[0], length);
        AAsset_close(asset);

        // 逐行解析标签
        size_t start = 0;
        // 跳过 UTF-8 BOM (EF BB BF)
        if (length >= 3 &&
            (unsigned char)content[0] == 0xEF &&
            (unsigned char)content[1] == 0xBB &&
            (unsigned char)content[2] == 0xBF)
        {
            start = 3;
        }
        while (start < content.size())
        {
            size_t end = content.find('\n', start);
            if (end == std::string::npos)
                end = content.size();

            std::string line = content.substr(start, end - start);
            if (!line.empty() && line.back() == '\r')
                line.pop_back();
            if (!line.empty())
                class_names.push_back(line);

            start = end + 1;
        }
    }
    else
    {
        fprintf(stderr, "Failed to open labels file: %s\n", labelspath);
    }

    // 如果没有加载到标签，使用默认 COCO 标签
    if (class_names.empty())
    {
        __android_log_print(ANDROID_LOG_WARN, "ncnn", "No labels loaded from path, using default COCO labels");
        const char* default_names[] = {
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
            "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
            "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
            "hair drier", "toothbrush"
        };
        for (const auto& name : default_names)
        {
            class_names.push_back(name);
        }
    }

    return 0;
}

const char* Inference::getClassName(int label) const
{
    if (label >= 0 && label < (int)class_names.size())
    {
        return class_names[label].c_str();
    }
    return "unknown";
}

int Inference::draw(cv::Mat& bgr, const std::vector<Object>& objects)
{
    cv::Mat res = bgr;
    for (size_t i = 0; i < objects.size(); i++)
	{
		const Object& obj = objects[i];

		fprintf(stderr, "%d = %.5f at %.2f %.2f %.2f x %.2f\n", obj.label, obj.prob,
			obj.rect.x, obj.rect.y, obj.rect.width, obj.rect.height);

		cv::rectangle(res, obj.rect, cv::Scalar(255, 0, 0));

		char text[256];
		sprintf(text, "%s %.1f%%", getClassName(obj.label), obj.prob * 100);

		int baseLine = 0;
		cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);

		int x = obj.rect.x;
		int y = obj.rect.y - label_size.height - baseLine;
		if (y < 0)
			y = 0;
		if (x + label_size.width > bgr.cols)
			x = bgr.cols - label_size.width;

		cv::rectangle(res, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
			cv::Scalar(255, 255, 255), -1);

		cv::putText(res, text, cv::Point(x, y + label_size.height),
			cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 0, 0));
	}

    return 0;
}
