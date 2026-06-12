# OpenIris ProGuard Rules

# Keep JNI classes
-keep class com.yolo.openiris.Yolov11Ncnn { *; }

# Keep AI model classes
-keep class com.yolo.openiris.ai.** { *; }

# Keep config classes
-keep class com.yolo.openiris.config.** { *; }

# Keep detection classes
-keep class com.yolo.openiris.detection.** { *; }

# Keep VLM/LLM classes
-keep class com.yolo.openiris.vlm.** { *; }
-keep class com.yolo.openiris.llm.** { *; }

# Keep fusion classes
-keep class com.yolo.openiris.fusion.** { *; }

# Keep export classes
-keep class com.yolo.openiris.export.** { *; }

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep coroutines
-dontwarn kotlinx.coroutines.**

# Keep Material Design
-dontwarn com.google.android.material.**

# Keep FlexboxLayout
-dontwarn com.google.android.flexbox.**

# Keep Security Crypto
-dontwarn androidx.security.crypto.**
-keep class androidx.security.crypto.** { *; }

