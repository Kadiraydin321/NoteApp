@echo off
title Not Defterim - Masaustu Yonetim Menusu
color 0B

:menu
cls
echo ===================================================
echo           NOT DEFTERIM - MASAUSTU (WINDOWS)
echo ===================================================
echo.
echo [1] Not Defterim (Compose Multiplatform Kotlin)
echo [2] Not Defterim (JavaScript / Electron Surumu)
echo [3] Windows Kurulum Paketi Uret (.msi)
echo [4] Tum Birim Testlerini Calistir (Kotlin + JS)
echo [0] Cikis
echo.
echo ===================================================
set /p secim="Seciminiz (1/2/3/4/0): "

if "%secim%"=="1" goto baslat_compose
if "%secim%"=="2" goto baslat_electron
if "%secim%"=="3" goto paketle
if "%secim%"=="4" goto testler
if "%secim%"=="0" goto cikis

echo Gecersiz secim!
timeout /t 2 >nul
goto menu

:baslat_compose
cls
echo ===================================================
echo   Not Defterim Compose Desktop Baslatiliyor...
echo ===================================================
echo.
call gradlew.bat :desktop:run
if %errorlevel% neq 0 (
    echo.
    echo Bir hata olustu. Hata kodu: %errorlevel%
    pause
)
goto menu

:baslat_electron
cls
echo ===================================================
echo   Not Defterim Electron Masaustu Baslatiliyor...
echo ===================================================
echo.
cd /d "%~dp0desktop-electron"
call npm start
cd /d "%~dp0"
if %errorlevel% neq 0 (
    echo.
    echo Bir hata olustu. Hata kodu: %errorlevel%
    pause
)
goto menu

:paketle
cls
echo ===================================================
echo   Windows Kurulum Paketi (.msi) Uretiliyor...
echo ===================================================
echo.
call gradlew.bat :desktop:packageMsi
if %errorlevel% neq 0 (
    echo.
    echo Paketleme basarisiz oldu. Hata kodu: %errorlevel%
) else (
    echo.
    echo Kurulum paketi basariyla olusturuldu!
    echo Konum: desktop\build\compose\binaries\main\msi
)
pause
goto menu

:testler
cls
echo ===================================================
echo   Tum Birim Testleri Calistiriliyor...
echo ===================================================
echo.
echo [1/2] Kotlin Compose Testleri...
call gradlew.bat :desktop:test
echo.
echo [2/2] Electron JavaScript Testleri...
cd /d "%~dp0desktop-electron"
call npm test
cd /d "%~dp0"
pause
goto menu

:cikis
exit
