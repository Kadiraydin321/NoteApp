@echo off
title ByteRescue by Kadir - Veri Kurtarma Laboratuvari
color 0B
cd /d "%~dp0"

echo ==========================================================
echo    ⚡ BYTERESCUE by Kadir - Veri Kurtarma Laboratuvari
echo ==========================================================
echo.

if exist "release\win-unpacked\ByteRescue.exe" (
    echo [1/2] Paketlenmis surum baslatiliyor...
    start "" "release\win-unpacked\ByteRescue.exe"
    exit /b
)

if exist "node_modules\.bin\electron.cmd" (
    echo [2/2] Node / Electron uzerinden baslatiliyor...
    call npm start
    exit /b
)

echo HATA: Calistirilabilir dosya bulunamadi.
echo Lutfen release klasorundeki ByteRescue-Setup-1.2.0.exe ile kurulum yapin.
pause
