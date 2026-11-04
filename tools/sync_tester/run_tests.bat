@echo off
chcp 65001 >nul
cd /d "%~dp0"
if "%~1"=="" (
    call "%~dp0Test_WiFi_Sync.bat"
) else (
    node "%~dp0index.js" %*
)
