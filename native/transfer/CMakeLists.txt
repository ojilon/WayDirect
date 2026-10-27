# waydirect_transfer â€” protocol helpers, listener hooks (no full Java NetworkManager replacement)

add_library(waydirect_transfer STATIC
    src/transfer_engine.cpp
    src/transfer_placeholder.cpp
    src/transfer_shim.c
)

target_include_directories(waydirect_transfer
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(waydirect_transfer
    PUBLIC
        waydirect_core
        waydirect_storage
    PRIVATE
        project_warnings
)

target_compile_features(waydirect_transfer PUBLIC cxx_std_23)

