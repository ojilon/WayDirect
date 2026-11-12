# =============================================================================
# native/build-android.ps1 — Odin-based Android native build (replaces CMake)
#
# Builds libWayDirect_engine.so for Android arm64-v8a using:
#   * odin build          — for all *.odin sources (Odin packages + C ABI exports)
#   * NDK clang (clang++) — for remaining C/C++ FFI implementation files
#   * llvm-ar             — to archive objects into a shared library
#
# Usage (from repo root):
#   powershell -File native/build-android.ps1
#
# Environment (set these before running):
#   $env:ODIN_ANDROID_NDK   — Android NDK root (default: D:\Dev\AndroidCLI\sdk\ndk\30.0.16248370)
#   $env:ODIN_ANDROID_SDK   — Android SDK root (default: D:\Dev\AndroidCLI\sdk)
#
# Output:
#   native/build/arm64-v8a/libWayDirect_engine.so
#
# =============================================================================


param(
    [string]$Abi = "arm64-v8a",
    [int]$MinApi = 29,
    [string]$BuildDir = "native/build"
)

$ErrorActionPreference = "Stop"

# --------------------------------------------------
# 1. Tools
# --------------------------------------------------

$OdinCmd = Get-Command odin -ErrorAction SilentlyContinue
if (-not $OdinCmd) {
    throw "Odin compiler not found. Add odin to PATH."
}
$OdinDir = Split-Path $OdinCmd.Source -Parent
if (-not (Test-Path $OdinDir)) {
    throw "Odin directory not found: $OdinDir"
}

$Ndk = $env:ODIN_ANDROID_NDK

if (-not $Ndk) {
    $Ndk = "D:\Dev\AndroidCLI\sdk\ndk\30.0.16248370"
}

if (-not (Test-Path $Ndk)) {
    throw "Android NDK not found: $Ndk"
}

$Toolchain = "$Ndk/toolchains/llvm/prebuilt/windows-x86_64/bin"

$Clang = "$Toolchain/clang.exe"
$ClangPP = "$Toolchain/clang++.exe"

foreach ($Tool in @($Clang, $ClangPP)) {
    if (-not (Test-Path $Tool)) {
        throw "Missing NDK compiler: $Tool"
    }
}

# --------------------------------------------------
# 2. Directories
# --------------------------------------------------

$Root = (Get-Location).Path
$Native = "$Root/native"

$ObjDir = Join-Path $BuildDir "obj"
$OutDir = Join-Path $BuildDir $Abi

New-Item -ItemType Directory -Force -Path $ObjDir, $OutDir | Out-Null

$ObjDir = (Resolve-Path $ObjDir).Path
$OutDir = (Resolve-Path $OutDir).Path

$Output = Join-Path $OutDir "libWayDirect_engine.so"

# Clean previous objects
Remove-Item "$ObjDir/*.o" -Force -ErrorAction SilentlyContinue

# --------------------------------------------------
# 3. Odin compilation
# --------------------------------------------------

Write-Host "`n[1/3] Building Odin..."

# odin.exe must run from its own directory (LLVM loads relative resources).
Push-Location $OdinDir
try {
    $OdinArgs = @(
        "build",
        "$Native/jni",
        "-build-mode:obj",
        "-target:linux_arm64",
        "-subtarget:android",
        "-reloc-mode:pic",
        "-no-entry-point",
        "-out:$ObjDir/waydirect_engine"
    )
    $OdinCmdLine = $OdinArgs -join ' '
    Write-Host "  odin $OdinCmdLine"
    cmd /c "odin $OdinCmdLine" 2>&1
}
finally {
    Pop-Location
}

if ($LASTEXITCODE -ne 0) {
    throw "Odin compilation failed."
}

$OdinObjects = @(
    Get-ChildItem $ObjDir -Filter "waydirect_engine*.o" -File
)

if ($OdinObjects.Count -eq 0) {
    throw "Odin produced no object files."
}

# --------------------------------------------------
# 4. Legacy C/C++ compilation
# --------------------------------------------------

Write-Host "`n[2/3] Building legacy C/C++..."

$Sources = @(
    "storage/src/dir_walk_android.c"
    "storage/src/index_c.c"
    "storage/src/storage_placeholder.cpp"
    "documents/src/documents_placeholder.cpp"
    "documents/src/check_if_directory.c"
    "documents/src/doc_filter_helper.c"
    "transfer/src/transfer_placeholder.cpp"
    "transfer/src/transfer_shim.c"
    "media/src/media_placeholder.cpp"
    "file_system_bridge/src/android_fs.c"
)

$Sysroot = "$Ndk/toolchains/llvm/prebuilt/windows-x86_64/sysroot"

# Include paths for remaining native headers (after .cpp/.hpp purge)
$IncPaths = @(
    "-I$Native/core/include"
    "-I$Native/storage/include"
    "-I$Native/documents/include"
    "-I$Native/transfer/include"
    "-I$Native/file_system_bridge/include"
)

$Flags = @(
    "-target", "aarch64-linux-android$MinApi"
    "--sysroot=$Sysroot"
    "-DANDROID"
    "-fPIC"
    "-O2"
    "-Wall"
    "-Wextra"
    "-I$Sysroot/usr/include"
    "-I$Sysroot/usr/include/aarch64-linux-android"
) + $IncPaths

$CppObjects = @()

foreach ($Source in $Sources) {

    $Input = Join-Path $Native $Source

    if (-not (Test-Path $Input)) {
        Write-Warning "Skipping missing source: $Source"
        continue
    }

    # Preserve directory structure to avoid basename collisions.
    $RelativePath = [IO.Path]::ChangeExtension($Source, ".o")
    $Object = Join-Path $ObjDir $RelativePath
    $ObjectDir = Split-Path $Object -Parent

    New-Item -ItemType Directory -Force -Path $ObjectDir | Out-Null

    $Compiler = if ($Source.EndsWith(".c")) {
        $Clang
    } else {
        $ClangPP
    }

    Write-Host "  $Source"

    & $Compiler @Flags -c $Input -o $Object

    if ($LASTEXITCODE -ne 0) {
        throw "Compilation failed: $Source"
    }

    $CppObjects += $Object
}

# --------------------------------------------------
# 5. Link shared library
# --------------------------------------------------

Write-Host "`n[3/3] Linking shared library..."

$Objects = @($OdinObjects.FullName) + $CppObjects

& $ClangPP `
    -shared `
    -target "aarch64-linux-android$MinApi" `
    "--sysroot=$Sysroot" `
    -o $Output `
    $Objects `
    -llog `
    -landroid `
    -lEGL `
    -lGLESv2

if ($LASTEXITCODE -ne 0) {
    throw "Shared library linking failed."
}

# --------------------------------------------------
# 6. Copy .so to Gradle jniLibs (for APK packaging)
# --------------------------------------------------

$JniLibsDir = Join-Path (Resolve-Path "app/src/main") "jniLibs/$Abi"
if (-not (Test-Path $JniLibsDir)) {
    New-Item -ItemType Directory -Force -Path $JniLibsDir | Out-Null
}
$DestSo = Join-Path $JniLibsDir "libWayDirect_engine.so"
Copy-Item -Path $Output -Destination $DestSo -Force
Write-Host "Copied .so to: $DestSo"

Write-Host "`n=================================="
Write-Host "Build successful"
Write-Host "ABI:    $Abi"
Write-Host "Output: $Output"
Write-Host "Copied to: $DestSo"
Write-Host "=================================="
