# waydirect_documents â€” document filter / future render hooks (no JNI)

add_library(waydirect_file_system STATIC
    src/android_fs.c
)

target_include_directories(waydirect_file_system
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(waydirect_file_system
    PUBLIC
        waydirect_core
        waydirect_storage
    PRIVATE
        project_warnings
)

target_compile_features(waydirect_file_system PUBLIC cxx_std_23)

