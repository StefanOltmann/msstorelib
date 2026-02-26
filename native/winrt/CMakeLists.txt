cmake_minimum_required(VERSION 3.20)

# Native C++/WinRT DLL that bridges StoreContext to a C ABI for the JVM.
project(msstore_winrt LANGUAGES CXX)

# Match the minimum supported C++ standard for C++/WinRT.
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(msstore_winrt SHARED
    msstore_winrt.cpp
    msstore_winrt.h
)

# MSSTORE_WINRT_EXPORTS enables __declspec(dllexport) in the header.
# NOMINMAX and WIN32_LEAN_AND_MEAN reduce Windows.h macro pollution.
target_compile_definitions(
    msstore_winrt
    PRIVATE
    MSSTORE_WINRT_EXPORTS
    NOMINMAX
    WIN32_LEAN_AND_MEAN
)

# windowsapp is required for WinRT APIs, ole32 provides COM memory APIs.
target_link_libraries(msstore_winrt PRIVATE windowsapp ole32)
