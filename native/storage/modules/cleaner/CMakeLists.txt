# waydirect_storage_cleaner â€” duplicates, large files, future junk cleanup
add_library(waydirect_storage_cleaner STATIC
    src/duplicate_finder.cpp
    src/large_files.cpp
)

# Needs parent storage/include for header-only walker/safety/extension_map.
# (Include dir only â€” no link to waydirect_storage to keep the graph acyclic.)
target_include_directories(waydirect_storage_cleaner
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/../../include
)

target_link_libraries(waydirect_storage_cleaner
    PUBLIC
        waydirect_core
    PRIVATE
        project_warnings
)

target_compile_features(waydirect_storage_cleaner PUBLIC cxx_std_23)

