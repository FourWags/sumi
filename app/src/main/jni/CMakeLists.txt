# Licensed under Apache-2.0; see the project license and upstream notices.
cmake_minimum_required(VERSION 3.22)
project(SumiKeyboardNative LANGUAGES C CXX)
include("${CMAKE_CURRENT_LIST_DIR}/NativeSources.cmake")
add_library(jni_latinime SHARED ${SUMI_NATIVE_SOURCES})
target_include_directories(jni_latinime PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/src")
# Match the existing ndk-build production compiler policy and static C++ runtime.
target_compile_options(jni_latinime PRIVATE
    -Wall -Wextra -Weffc++ -Wformat=2 -Wcast-qual -Wcast-align
    -Wwrite-strings -Wfloat-equal -Wpointer-arith -Winit-self -Wredundant-decls
    -Woverloaded-virtual -Wsign-promo -Wno-system-headers
    -Wno-unused-parameter -Wno-unused-function -fno-exceptions -fno-rtti)
if(ANDROID_ABI STREQUAL "x86")
    target_compile_options(jni_latinime PRIVATE -mstackrealign)
endif()
target_link_libraries(jni_latinime PRIVATE dl)
