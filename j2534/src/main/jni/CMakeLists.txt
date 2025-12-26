# CMakeLists.txt for SpaceTec J2534 Native Library

cmake_minimum_required(VERSION 3.18.1)

project("spacetec_j2534")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add the native library
add_library(
    spacetec_j2534
    SHARED
    j2534_native.cpp
)

# Find required libraries
find_library(log-lib log)

# Link libraries
target_link_libraries(
    spacetec_j2534
    ${log-lib}
)

# Include directories
target_include_directories(
    spacetec_j2534
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)