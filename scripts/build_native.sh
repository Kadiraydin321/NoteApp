#!/usr/bin/env bash
# scripts/build_native.sh - Builds ByteRescue native C++ binary and NSIS Setup installer
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

echo "========================================================"
echo "⚡ ByteRescue Native C++ & NSIS Kurulum Derleyici"
echo "========================================================"

# 1. Embed HTML into C++ header
echo "[1/4] Web arayüzü C++ başlığına gömülüyor (cpp/src/embedded_web.hpp)..."
cat << 'HEADER_EOF' > cpp/src/embedded_web.hpp
// cpp/src/embedded_web.hpp
// Auto-generated from cpp/web/index.html
#pragma once

namespace byterescue {

const char EMBEDDED_INDEX_HTML[] = R"html(
HEADER_EOF

cat cpp/web/index.html >> cpp/src/embedded_web.hpp

cat << 'FOOTER_EOF' >> cpp/src/embedded_web.hpp
)html";

} // namespace byterescue
FOOTER_EOF

# 2. Compile C++ with Zig
ZIG="/home/birin/.local/toolchain/zig"
if [ ! -f "${ZIG}" ]; then
    ZIG="zig"
fi

echo "[2/4] C++ kaynak kodları derleniyor (ByteRescue.exe)..."
"${ZIG}" c++ -target x86_64-windows -std=c++17 -O3 \
    cpp/src/main.cpp \
    cpp/src/http_server.cpp \
    cpp/src/carver_engine.cpp \
    cpp/src/ntfs_scanner.cpp \
    cpp/src/disk_detector.cpp \
    cpp/src/folder_dialog.cpp \
    cpp/src/app.rc \
    -o ByteRescue.exe \
    -lws2_32 -lshell32 -lole32 -lcomctl32 \
    -static

rm -f ByteRescue.pdb ByteRescue.lib main.lib

# 3. Compile NSIS Installer
echo "[3/4] NSIS kurulum paketi derleniyor (ByteRescue_Kurulum.exe)..."
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -Command \
    "Set-Location '\\\\wsl\$\\Ubuntu\\home\\birin\\Projeler\\ByteRescue'; & 'C:\\Users\\Birin\\AppData\\Local\\electron-builder\\Cache\\nsis\\nsis-3.0.4.1\\Bin\\makensis.exe' installer.nsi" < /dev/null

# 4. Copy to Desktop
echo "[4/4] Masaüstüne kopyalanıyor (C:\\Users\\Birin\\Desktop\\ByteRescue)..."
mkdir -p /mnt/c/Users/Birin/Desktop/ByteRescue
cp -v ByteRescue.exe ByteRescue_Kurulum.exe /mnt/c/Users/Birin/Desktop/ByteRescue/

echo ""
echo "========================================================"
echo "✅ Derleme ve kurulum paketi başarıyla tamamlandı!"
echo "   - Tek Exe: C:\\Users\\Birin\\Desktop\\ByteRescue\\ByteRescue.exe"
echo "   - Kurulum: C:\\Users\\Birin\\Desktop\\ByteRescue\\ByteRescue_Kurulum.exe"
echo "========================================================"
