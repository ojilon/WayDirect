# waydirect_core â€” shared foundation (no domain logic, no JNI)

add_library(waydirect_core STATIC
    src/json_util.cpp
    src/paths.cpp
    src/logging.cpp
    src/text.cpp
)

target_include_directories(waydirect_core
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(waydirect_core
    PRIVATE
        project_warnings
)

target_compile_features(waydirect_core PUBLIC cxx_std_23)

