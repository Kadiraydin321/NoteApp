@echo off
title Not Defterim (Electron JavaScript Masaustu)
color 0B
cd /d "%~dp0desktop-electron"
echo ===================================================
echo     Not Defterim Electron Masaustu Baslatiliyor...
echo ===================================================
echo.
call npm start
if %errorlevel% neq 0 (
    echo.
    echo Bir hata olustu. Hata kodu: %errorlevel%
    pause
)
