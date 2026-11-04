@echo off
chcp 65001 >nul
title Wi-Fi Senkronizasyon Test Aracı
color 0A
cd /d "%~dp0"

echo ========================================================
echo     WI-FI / LAN SENKRONIZASYON TEST PROTOKOLÜ
echo ========================================================
echo.
set /p target_host="Hedef IP Adresi (Varsayılan: 127.0.0.1): "
if "%target_host%"=="" set target_host=127.0.0.1

set /p target_port="Hedef Port (Varsayılan: 8765): "
if "%target_port%"=="" set target_port=8765

set /p target_pin="Eşleşme PIN Kodu (Masaüstü ekranındaki 4 hane, opsiyonel): "

echo.
echo Testler başlatılıyor...
echo.

if not "%target_pin%"=="" (
    node index.js --host %target_host% --port %target_port% --pin %target_pin%
) else (
    node index.js --host %target_host% --port %target_port%
)

echo.
pause
