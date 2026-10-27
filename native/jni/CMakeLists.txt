# =============================================================================
# jni/CMakeLists.txt â€” the only shared library Android actually loads
#
# waydirect_engine.c is a tiny C trampoline:
#   Java  â†’  JNI  â†’  route_action()  (exported by the Odin archive)
#
# Everything else (C++ modules + Odin objects) is linked in statically.
# =============================================================================

add_library(waydirect_engine SHARED waydirect_engine.c)

# Android provides liblog; desktop / clangd configures may not.
find_library(log-lib log)

target_link_libraries(waydirect_engine
    PRIVATE
        waydirect_core_odin      # Odin router + ported storage pieces
        waydirect_shim           # C++ functions still called from Odin
        waydirect_storage
        waydirect_documents
        waydirect_transfer
        waydirect_media
        waydirect_preview
        project_warnings
        waydirect_file_system
)

if(log-lib)
    target_link_libraries(waydirect_engine PRIVATE ${log-lib})
endif()

# This translation unit is pure C (no C++).
set_target_properties(waydirect_engine PROPERTIES LANGUAGE C)
target_compile_features(waydirect_engine PUBLIC c_std_11)

# Make sure the Odin archive is built before we try to link it.
add_dependencies(waydirect_engine waydirect_core_odin_gen)

