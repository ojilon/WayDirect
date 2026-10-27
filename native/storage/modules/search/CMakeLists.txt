# waydirect_storage_search â€” file name/content search helpers
add_library(waydirect_storage_search STATIC
    src/file_search.cpp
    src/collect_data.c
)

target_include_directories(waydirect_storage_search
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(waydirect_storage_search
    PUBLIC
        waydirect_core
    PRIVATE
        project_warnings
)

target_compile_features(waydirect_storage_search PUBLIC cxx_std_23)

