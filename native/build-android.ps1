# =============================================================================
# native/build-android.ps1 — Odin-based Android native build (replaces CMake)
#
# Builds libWayDirect_engine.so for Android arm64-v8a using:
#   * odin build          — for all *.odin sources (Odin packages + C ABI exports)
#   * NDK clang (clang++) — for remaining C/C++ FFI implementation files
#   * llvm-ar             — to archive objects into a shared library
#
# Usage (from repo root):
#   pwsh native/build-android.ps1
#
# Environment (set these or export before running):
#   $env:ODIN_ANDROID_NDK   — Android NDK root (default: D:\Dev\AndroidCLI\sdk\ndk\30.0.16248370)
#   $env:ODIN_ANDROID_SDK   — Android SDK root (default: D:\Dev\AndroidCLI\sdk)
#   $env:JAVA_HOME          — JDK root (needed only if you also run odin bundle android)
#
# Output:
#   native/build/arm64-v8a/libWayDirect_engine.so
#
# =============================================================================

param(
    [string]$TargetAbi   = "arm64-v8a",
    [string]$OdinTarget  = "linux_arm64",
    [string]$OdinSubTarget = "-subtarget:android",
    [int]$MinApi         = 29,
    [string]$BuildDir    = "native/build",
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"

# --- locate NDK ---------------------------------------------------------------
if (-not $env:ODIN_ANDROID_NDK) {
    $env:ODIN_ANDROID_NDK = "D:\Dev\AndroidCLI\sdk\ndk\30.0.16248370"
    Write-Host "ODIN_ANDROID_NDK not set, using default: $($env:ODIN_ANDROID_NDK)"
}
$NdkRoot = $env:ODIN_ANDROID_NDK
if (-not (Test-Path $NdkRoot)) {
    throw "NDK not found at $NdkRoot. Set ODIN_ANDROID_NDK."
}

# NDK clang for C/C++ compilation (windows-x86_64 host)
$Clang = Join-Path $NdkRoot "toolchains\llvm\prebuilt\windows-x86_64\bin\clang++.exe"
if (-not (Test-Path $Clang)) {
    throw "clang++ not found at $Clang"
}
$LlvmAr = Join-Path $NdkRoot "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-ar.exe"
if (-not (Test-Path $LlvmAr)) {
    throw "llvm-ar not found at $LlvmAr"
}

# Android platform (for libc++ / system libs)
$AndroidPlatform = "android-$MinApi"
$ClangFlags = @(
    "-target", "aarch64-linux-android$MinApi",
    "-DANDROID",
    "-D__ANDROID_API__=$MinApi",
    "-fPIC",
    "-O2",
    "-Wall",
    "-Wextra",
    "-I", (Join-Path $NdkRoot "sysroot\usr\include\aarch64-linux-android"),
    "-I", (Join-Path $NdkRoot "sysroot\usr\include"),
    "--sysroot", (Join-Path $NdkRoot "sysroot"),
)

# --- locate Odin compiler -----------------------------------------------------
$Odin = Get-Command "odin" -ErrorAction SilentlyContinue
if (-not $Odin) {
    throw "odin not found on PATH. Install from https://odin-lang.org"
}
Write-Host "Odin compiler: $($Odin.Source)"

# --- directories --------------------------------------------------------------
$NativeRoot = Resolve-Path "."
$OdinSrc    = Join-Path $NativeRoot "jni"        # entry package: waydirect_jni (imports waydirect_core transitively)
$OutDir     = Join-Path $BuildDir $TargetAbi
$ObjDir     = Join-Path $BuildDir "obj"
$LibOut     = Join-Path $OutDir "libWayDirect_engine.so"

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }
if (-not (Test-Path $ObjDir)) { New-Item -ItemType Directory -Path $ObjDir -Force | Out-Null }

# --- step 1: build Odin sources into object files -----------------------------
Write-Host "`n[1/4] Building Odin sources with odin build ..."
$OdinPrefix = Join-Path $ObjDir "waydirect_engine"

# Clean stale Odin objects
Get-ChildItem -Path $ObjDir -Filter "waydirect_engine-*.o" -ErrorAction SilentlyContinue | Remove-Item -Force

$OdinArgs = @(
    "build", $OdinSrc,
    "-build-mode:obj",
    "-target:$OdinTarget",
    "-reloc-mode:pic",
    "-no-entry-point",
    "-out:" + $OdinPrefix,
)
if ($OdinSubTarget) { $OdinArgs += $OdinSubTarget }

$OdinEnv = @{ "ODIN_ANDROID_NDK" = $NdkRoot }
& $Odin.Source @OdinArgs | Out-String
if ($LASTEXITCODE -ne 0) { throw "Odin build failed" }

# Collect Odin object files
$OdinObjFiles = Get-ChildItem -Path $ObjDir -Filter "waydirect_engine-*.o" -ErrorAction SilentlyContinue
if (-not $OdinObjFiles) {
    # Some Odin versions emit a single file without suffix
    $OdinObjFiles = Get-ChildItem -Path $ObjDir -Filter "waydirect_engine.o" -ErrorAction SilentlyContinue
}
if (-not $OdinObjFiles) {
    throw "Odin produced no object files in $ObjDir"
}
Write-Host "Odin objects: $($OdinObjFiles.Count) files"

# --- step 2: compile C/C++ FFI implementation files ---------------------------
Write-Host "`n[2/4] Compiling C/C++ FFI sources with NDK clang++ ..."

# C/C++ source files that implement the FFI behind Odin's foreign imports.
# These are the files that still use C/C++ (not yet ported to pure Odin).
$CppSources = @(
    "core/src/json_util.cpp",
    "core/src/paths.cpp",
    "core/src/logging.cpp",
    "core/src/text.cpp",
    "storage/src/analyse_storage_space.cpp",
    "storage/src/dir_walk_android.c",
    "storage/src/extension_map.cpp",
    "storage/src/index_c.c",
    "storage/src/list_files.cpp",
    "storage/src/safety.cpp",
    "storage/src/stats_db.cpp",
    "storage/src/stats_json.cpp",
    "storage/src/storage_cache.cpp",
    "storage/src/storage_placeholder.cpp",
    "storage/src/walker.cpp",
    "documents/src/document_engine.cpp",
    "documents/src/documents_placeholder.cpp",
    "documents/src/check_if_directory.c",
    "documents/src/doc_filter_helper.c",
    "transfer/src/transfer_engine.cpp",
    "transfer/src/transfer_placeholder.cpp",
    "transfer/src/transfer_shim.c",
    "media/src/media_placeholder.cpp",
    "preview/src/preview.cpp",
    "file_system_bridge/src/android_fs.c",
)

$CppObjFiles = New-Object System.Collections.ArrayList
foreach ($src in $CppSources) {
    $srcPath = Join-Path $NativeRoot $src
    if (-not (Test-Path $srcPath)) {
        Write-Warning "Skipping missing source: $src"
        continue
    }
    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($src)
    $objFile = Join-Path $ObjDir ($baseName + ".o")
    $CppObjFiles.Add($objFile)

    $CmdArgs = @($Clang, @($ClangFlags), "-c", $srcPath, "-o", $objFile)
    if ($Verbose) { Write-Host "  clang++ $src -> $objFile" }
    & $CmdArgs | Out-String
    if ($LASTEXITCODE -ne 0) { throw "clang++ failed for $src" }
}
Write-Host "C/C++ objects: $($CppObjFiles.Count) files"

# --- step 3: archive into shared library --------------------------------------
Write-Host "`n[3/4] Archiving into libWayDirect_engine.so ..."

# Remove old output
if (Test-Path $LibOut) { Remove-Item -Path $LibOut -Force }

# Link with clang++ into a shared object
$AllObjs = @($OdinObjFiles) + @($CppObjFiles)
$LinkArgs = @(
    $Clang,
    "-shared",
    "-target", "aarch64-linux-android$MinApi",
    "--sysroot", (Join-Path $NdkRoot "sysroot"),
    "-o", $LibOut,
)
$LinkArgs += $AllObjs
# Link Android system libs
$LinkArgs += @("-llog", "-landroid", "-lEGL", "-lGLESv2")

if ($Verbose) { Write-Host "  Linking $($AllObjs.Count) objects into $LibOut" }
& $LinkArgs | Out-String
if ($LASTEXITCODE -ne 0) { throw "Link failed" }

Write-Host "Created: $LibOut"

# --- step 4: summary ----------------------------------------------------------
Write-Host "`n[4/4] Build complete."
Write-Host "  Output: $LibOut"
Write-Host "  To package: copy this .so to app/src/main/jniLibs/arm64-v8a/ and run ./gradlew assembleDebug"
Write-Host "  Or use: odin bundle android app -minimum-os-version:$MinApi -out:WayDirect.apk"