@echo off
chcp 65001 >nul
title Not Defterim - Masaüstü Başlatıcı
if exist "%~dp0desktop-native" (cd /d "%~dp0") else (cd /d "%~dp0..\not-uygulamalari\not-defterim")

:: 1. Öncelik: Derlenmiş Native Exe varsa sessizce doğrudan başlat
if exist "desktop-native\target\release\notdefterim.exe" (
    start "" "desktop-native\target\release\notdefterim.exe" %*
    exit /b 0
)
if exist "desktop-native\target\debug\notdefterim.exe" (
    start "" "desktop-native\target\debug\notdefterim.exe" %*
    exit /b 0
)

:: 2. Öncelik: Go Motorunu Hızlı Derle ve Çalıştır
set "GO_BIN="
where go >nul 2>&1 && set "GO_BIN=go"
if not defined GO_BIN (
    if exist "C:\Program Files\Go\bin\go.exe" set "GO_BIN=C:\Program Files\Go\bin\go.exe"
)

if defined GO_BIN (
    if exist "desktop-native\go-engine\main.go" (
        echo [BİLGİ] Not Defterim ilk kez hazırlanıyor, lütfen bekleyin...
        if not exist "desktop-native\target\release\" mkdir "desktop-native\target\release" >nul 2>&1
        "%GO_BIN%" build -ldflags "-H windowsgui -s -w" -o "desktop-native\target\release\notdefterim.exe" "desktop-native\go-engine\main.go"
        if exist "desktop-native\target\release\notdefterim.exe" (
            start "" "desktop-native\target\release\notdefterim.exe" %*
            exit /b 0
        )
    )
)

:: 3. Öncelik: Microsoft Edge App Modu (Anında Kesintisiz Başlatma)
set "EDGE_BIN="
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" set "EDGE_BIN=%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"
if not defined EDGE_BIN (
    if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" set "EDGE_BIN=%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"
)
if not defined EDGE_BIN (
    where msedge >nul 2>&1 && set "EDGE_BIN=msedge"
)

if defined EDGE_BIN (
    if exist "desktop-native\ui\index.html" (
        start "" "%EDGE_BIN%" --app="file:///%~dp0..\not-uygulamalari\not-defterim\desktop-native\ui\index.html"
        exit /b 0
    )
)

:: 4. Alternatif: Varsayılan Tarayıcı ile Aç
if exist "desktop-native\ui\index.html" (
    start "" "desktop-native\ui\index.html"
    exit /b 0
)

:: Hata Durumu (Bileşenler eksikse kullanıcıyı bilgilendir)
color 0C
echo.
echo ===============================================================================
echo [HATA] Not Defterim masaüstü bileşenleri bulunamadı veya başlatılamadı!
echo Lütfen 'desktop-native\ui\index.html' veya derlenmiş uygulamanın mevcut olduğundan emin olun.
echo ===============================================================================
echo.
pause
exit /b 1
