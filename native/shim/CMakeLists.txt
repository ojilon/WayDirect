# waydirect_shim â€” C++ shim layer exposing extern "C" WayDirect_* symbols for the Odin router.
# Each function is a one-line wrapper over the existing C++ domain module.
add_library(waydirect_shim STATIC waydirect_shim.cpp)

target_link_libraries(waydirect_shim
    PUBLIC
        waydirect_core
        waydirect_storage
        waydirect_documents
        waydirect_transfer
        waydirect_preview
    PRIVATE
        project_warnings
)

target_compile_features(waydirect_shim PUBLIC cxx_std_23)

