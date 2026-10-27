# waydirect_storage_organizer â€” plan/apply file organization
add_library(waydirect_storage_organizer STATIC
    src/file_organizer.cpp
)

# Needs parent storage/include for header-only walker/safety/extension_map.
# (Include dir only â€” no link to waydirect_storage to keep the graph acyclic.)
target_include_directories(waydirect_storage_organizer
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/../../include
)

target_link_libraries(waydirect_storage_organizer
    PUBLIC
        waydirect_core
    PRIVATE
        waydirect_json
        project_warnings
)

target_compile_features(waydirect_storage_organizer PUBLIC cxx_std_23)

