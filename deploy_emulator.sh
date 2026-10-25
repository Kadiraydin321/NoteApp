#!/usr/bin/env bash
set -e

ADB_WIN="C:\\Users\\Birin\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe"
DESKTOP_APK="C:\\Users\\Birin\\Desktop\\NoteApp-debug.apk"
PACKAGE_NAME="com.example.noteapp"
MAIN_ACTIVITY="com.example.noteapp/.MainActivity"

echo "=========================================="
echo "1. APK Derleniyor (assembleDebug)..."
echo "=========================================="
cd /home/birin/Projeler/NoteApp
./gradlew assembleDebug --quiet

echo "=========================================="
echo "2. APK Masaüstüne Kopyalanıyor..."
echo "=========================================="
cp app/build/outputs/apk/debug/app-debug.apk /mnt/c/Users/Birin/Desktop/NoteApp-debug.apk

echo "=========================================="
echo "3. Emülatör Cihazı Kontrol Ediliyor..."
echo "=========================================="
timeout 10 cmd.exe /c "$ADB_WIN devices" < /dev/null

echo "=========================================="
echo "4. Önbellek ve Veriler Temizleniyor (pm clear)..."
echo "=========================================="
timeout 10 cmd.exe /c "$ADB_WIN shell pm clear $PACKAGE_NAME" < /dev/null || true

echo "=========================================="
echo "5. Eski Uygulama Kaldırılıyor (uninstall)..."
echo "=========================================="
timeout 10 cmd.exe /c "$ADB_WIN uninstall $PACKAGE_NAME" < /dev/null || true

echo "=========================================="
echo "6. Yeni Sürüm Yükleniyor (install -r)..."
echo "=========================================="
timeout 30 cmd.exe /c "$ADB_WIN install -r $DESKTOP_APK" < /dev/null

echo "=========================================="
echo "7. Uygulama Emülatörde Başlatılıyor..."
echo "=========================================="
timeout 10 cmd.exe /c "$ADB_WIN shell am start -n $MAIN_ACTIVITY" < /dev/null

echo "=========================================="
echo "✓ Tamamlandı! Pixel 10 Pro emülatöründe tertemiz çalışıyor."
echo "=========================================="
