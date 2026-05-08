project(yolov11ncnn)

cmake_minimum_required(VERSION 3.10)

# opencv4 requires c++11
set(CMAKE_CXX_STANDARD 11)

set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv-mobile-3.4.20-android/sdk/native/jni)
find_package(OpenCV REQUIRED core imgproc)

set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20240410-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

# OpenMP support - required by opencv-mobile static libs compiled with OpenMP
# The opencv-mobile 3.4.20 library was compiled with an older LLVM that references
# __kmpc_dispatch_deinit, which was removed in LLVM 17+ (NDK r27 ships clang 18).
# We link the static libomp.a for the other __kmpc symbols it DOES provide,
# and include omp_stubs.c to provide the missing __kmpc_dispatch_deinit stub.

# Determine the correct clang lib directory for the ABI
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(OMP_ARCH "aarch64")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(OMP_ARCH "arm")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(OMP_ARCH "x86_64")
elseif(ANDROID_ABI STREQUAL "x86")
    set(OMP_ARCH "i386")
endif()

# NDK r27 ships with clang 18
set(OMP_STATIC_LIB "${ANDROID_TOOLCHAIN_ROOT}/lib/clang/18/lib/linux/${OMP_ARCH}/libomp.a")

if(NOT EXISTS "${OMP_STATIC_LIB}")
    # Fallback: try with CMAKE_CXX_COMPILER_VERSION
    set(OMP_STATIC_LIB "${ANDROID_TOOLCHAIN_ROOT}/lib/clang/${CMAKE_CXX_COMPILER_VERSION}/lib/linux/${OMP_ARCH}/libomp.a")
endif()

if(EXISTS "${OMP_STATIC_LIB}")
    message(STATUS "Using static OpenMP runtime: ${OMP_STATIC_LIB}")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fopenmp")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fopenmp")

    add_library(yolov11ncnn SHARED
        yolov11ncnn.cpp
        ndkcamera.cpp
        yolov11.cpp
        omp_stubs.c
    )
    target_link_libraries(yolov11ncnn ncnn ${OpenCV_LIBS} camera2ndk mediandk ${OMP_STATIC_LIB} dl)
else()
    message(WARNING "Static OpenMP not found at: ${OMP_STATIC_LIB}")
    message(FATAL_ERROR "Could not find static libomp.a. Please check your NDK installation.")
endif()
