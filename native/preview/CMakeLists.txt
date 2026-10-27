# waydirect_preview â€” read-only document/text preview for the in-app viewer (no JNI)

add_library(waydirect_preview STATIC
    src/preview.cpp
)

target_include_directories(waydirect_preview
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(waydirect_preview
    PUBLIC
        waydirect_core
    PRIVATE
        project_warnings
)

target_compile_features(waydirect_preview PUBLIC cxx_std_23)

