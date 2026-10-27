# =============================================================================
# storage/CMakeLists.txt â€” C++ side of the storage module
#
# Parallel Odin files live in src/*.odin (walker, extension_map, stats_json,
# analyse_storage_space, safety). Those are compiled by the top-level Odin
# step in native/CMakeLists.txt â€” you do NOT list them here.
#
# When a piece is fully rewritten in Odin and no longer needed from C++,
# just delete the corresponding .cpp from the list below.
# =============================================================================

# Optional submodules (search / cleaner / organizer)
add_subdirectory(modules/search)
add_subdirectory(modules/cleaner)
add_subdirectory(modules/organizer)

add_library(waydirect_storage STATIC
    src/analyse_storage_space.cpp
    src/dir_walk_android.c
    src/extension_map.cpp
    src/index_c.c
    src/list_files.cpp
    src/safety.cpp
    src/stats_db.cpp
    src/stats_json.cpp
    src/storage_cache.cpp
    src/storage_placeholder.cpp
    src/walker.cpp
)

target_include_directories(waydirect_storage
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(waydirect_storage
    PUBLIC
        waydirect_core
        waydirect_storage_search
        waydirect_storage_cleaner
        waydirect_storage_organizer
    PRIVATE
        waydirect_json
        waydirect_sqlite3
        project_warnings
)

target_compile_features(waydirect_storage PUBLIC cxx_std_23)
