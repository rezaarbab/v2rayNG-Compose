# native gate for the server identity + the decode primitives - pure C, no STL, no external deps
cmake_minimum_required(VERSION 3.18.1)
project(blobgate C)

add_library(blobgate SHARED blobgate.c blobsec.c)
target_compile_options(blobgate PRIVATE -fvisibility=hidden -O2 -Wall)
# -s drops the symbol table, and the 47 entry points are static now, so neither the dynamic table nor
# a disassembler's function list carries their names any more. The library still exports JNI_OnLoad -
# that one name is the price of loading at all.
target_link_options(blobgate PRIVATE -s)
# log: android logging is not used (nothing is written to logcat); z: the envelope inflate
target_link_libraries(blobgate log z)
