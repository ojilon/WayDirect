# waydirect_media â€” placeholder for audio/video metadata / helpers (future)

add_library(waydirect_media STATIC
    src/media_placeholder.cpp
)

target_include_directories(waydirect_media
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(waydirect_media
    PUBLIC
        waydirect_core
    PRIVATE
        project_warnings
)

target_compile_features(waydirect_media PUBLIC cxx_std_23)

