cmake_minimum_required(VERSION 3.18)
project(naran C)
add_library(naran SHARED naran.c)
target_link_libraries(naran)
