cmake_minimum_required(VERSION 3.6.0)

if(${ANDROID_ABI} STREQUAL "x86")
    enable_language(ASM_NASM)
    set(PER_ARCH_SOURCES
        src/main/cpp/detect_kvm_x86.asm)
else()
    set(PER_ARCH_SOURCES
        src/main/cpp/detect_kvm_generic.cpp)
endif()

add_library(tanker-bindings-jni SHARED
            ${PER_ARCH_SOURCES}
            src/main/cpp/onload.cpp
)
