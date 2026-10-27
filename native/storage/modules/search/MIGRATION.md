# storage/modules/search

**Target:** `WayDirect_storage_search` (STATIC)

## Migrate â€” done

1. [x] `file_search.cpp` lives in `modules/search/src/file_search.cpp`
2. [x] API in `include/WayDirect/storage/search.hpp` (namespace `WayDirect::storage`)
3. [x] STATIC library listing the `.cpp`
4. [x] `file_search.cpp` removed from parent `storage/CMakeLists.txt`
5. [x] JNI includes / calls updated

## Improvements

- Search over **index files** under cache (not full tree walk every time)
- Return path to result file when hit lists are large

