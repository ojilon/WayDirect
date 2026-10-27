# waydirect_documents â€” document filter / future render hooks (no JNI)

add_library(waydirect_documents STATIC
    src/document_engine.cpp
    src/documents_placeholder.cpp
    src/check_if_directory.c
    src/doc_filter_helper.c
)

target_include_directories(waydirect_documents
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(waydirect_documents
    PUBLIC
        waydirect_core
        waydirect_storage
    PRIVATE
        project_warnings
)

target_compile_features(waydirect_documents PUBLIC cxx_std_23)

