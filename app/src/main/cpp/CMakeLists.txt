# native gate for the server identity - pure C, no STL, no external deps
cmake_minimum_required(VERSION 3.22.1)
project(blobgate C)

add_library(blobgate SHARED blobgate.c)
target_compile_options(blobgate PRIVATE -fvisibility=hidden -O2 -Wall)
target_link_libraries(blobgate log)
