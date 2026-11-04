@echo off
title Not Defterim - Modern Masaustu Yonetim Merkezi
color 0B
cd /d "%~dp0"

:menu
cls
color 0B
echo ===============================================================================
echo             NOT DEFTERIM - MODERN MASAUSTU YONETIM MERKEZI
echo ===============================================================================
echo.
echo   [1] Not Defterim Masaustu Baslat (Native Exe / Go Motoru / Edge App)
echo   [2] Go Native Ikiliyi Hizli Derle (go build : notdefterim.exe)
echo   [3] Rust Masaustu Surumunu Derle (cargo build --release)
echo   [4] Rust Masaustu Birim Testlerini Calistir (cargo test)
echo   [5] Wi-Fi Senkronizasyon Test Paketini Calistir (Gelismis LAN Dogrulayici)
echo   [6] Hizli Teshis ve Sistem Durumu (Go, Rust, Port 8765, Dosyalar)
echo   [0] Cikis
echo.
echo ===============================================================================
set /p secim="Seciminiz [1/2/3/4/5/6/0]: "
if defined secim set "secim=%secim: =%"

if "%secim%"=="1" goto baslat_desktop
if "%secim%"=="2" goto derle_go
if "%secim%"=="3" goto derle_rust
if "%secim%"=="4" goto testler_rust
if "%secim%"=="5" goto wifi_sync_test
if "%secim%"=="6" goto hizli_teshis
if "%secim%"=="0" goto cikis

color 0C
echo.
echo [UYARI] Gecersiz secim: "%secim%"! Lutfen menudeki seceneklerden birini girin.
timeout /t 2 >nul 2>&1 || ping -n 3 127.0.0.1 >nul
goto menu

:baslat_desktop
cls
color 0B
echo ===============================================================================
echo   [1/6] Not Defterim Masaustu Uygulamasi Baslatiliyor...
echo ===============================================================================
echo.

:: 1. Oncelik: Derlenmis Release ikili dosyasi
if exist "%~dp0desktop-native\target\release\notdefterim.exe" (
    color 0A
    echo [BILGI] Derlenmis Release surumu baslatiliyor: notdefterim.exe...
    start "" "%~dp0desktop-native\target\release\notdefterim.exe" %*
    goto baslat_desktop_sonuc
)

:: 2. Oncelik: Derlenmis Debug ikili dosyasi
if exist "%~dp0desktop-native\target\debug\notdefterim.exe" (
    color 0A
    echo [BILGI] Derlenmis Debug surumu baslatiliyor: notdefterim.exe...
    start "" "%~dp0desktop-native\target\debug\notdefterim.exe" %*
    goto baslat_desktop_sonuc
)

:: 3. Oncelik: Go Motorunu Otomatik Derle ve Baslat
set "GO_BIN="
where go >nul 2>&1 && set "GO_BIN=go"
if not defined GO_BIN (
    if exist "C:\Program Files\Go\bin\go.exe" set "GO_BIN=C:\Program Files\Go\bin\go.exe"
)

if defined GO_BIN (
    if exist "%~dp0desktop-native\go-engine\main.go" (
        color 0E
        echo [ISLEM] Go ile yerel native ikili derleniyor [notdefterim.exe]...
        if not exist "%~dp0desktop-native\target\release\" mkdir "%~dp0desktop-native\target\release" >nul 2>&1
        call "%GO_BIN%" build -ldflags "-H windowsgui -s -w" -o "%~dp0desktop-native\target\release\notdefterim.exe" "%~dp0desktop-native\go-engine\main.go"
        if exist "%~dp0desktop-native\target\release\notdefterim.exe" (
            color 0A
            echo [BASARILI] Native ikili uretildi, baslatiliyor...
            start "" "%~dp0desktop-native\target\release\notdefterim.exe" %*
            goto baslat_desktop_sonuc
        )
    )
)

:: 4. Oncelik: Microsoft Edge App Modu
set "EDGE_BIN="
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" set "EDGE_BIN=%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"
if not defined EDGE_BIN (
    if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" set "EDGE_BIN=%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"
)
if not defined EDGE_BIN (
    where msedge >nul 2>&1 && set "EDGE_BIN=msedge"
)

if defined EDGE_BIN (
    if exist "%~dp0desktop-native\ui\index.html" (
        color 0A
        echo [BILGI] Microsoft Edge App modunda baslatiliyor...
        start "" "%EDGE_BIN%" --app="file:///%~dp0desktop-native\ui\index.html"
        goto baslat_desktop_sonuc
    )
)

:: 5. Oncelik: Varsayilan Tarayici
if exist "%~dp0desktop-native\ui\index.html" (
    color 0A
    echo [BILGI] Varsayilan tarayici uzerinden baslatiliyor...
    start "" "%~dp0desktop-native\ui\index.html"
    goto baslat_desktop_sonuc
)

color 0C
echo [HATA] Masaustu arayuz bilesenleri bulunamadi!
pause
goto menu

:baslat_desktop_sonuc
color 0A
echo [BASARILI] Not Defterim Masaustu uygulamasi baslatildi.
timeout /t 2 >nul 2>&1 || ping -n 3 127.0.0.1 >nul
goto menu

:derle_go
cls
color 0B
echo ===============================================================================
echo   [2/6] Go Native Ikiliyi Hizli Derle (notdefterim.exe)
echo ===============================================================================
echo.
set "GO_BIN="
where go >nul 2>&1 && set "GO_BIN=go"
if not defined GO_BIN (
    if exist "C:\Program Files\Go\bin\go.exe" set "GO_BIN=C:\Program Files\Go\bin\go.exe"
)

if not defined GO_BIN (
    color 0C
    echo [HATA] Go derleyicisi sistemde bulunamadi!
    echo Lutfen Go'nun kurulu oldugundan emin olun (varsayilan: C:\Program Files\Go\bin\go.exe).
    pause
    goto menu
)

if not exist "%~dp0desktop-native\go-engine\main.go" (
    color 0C
    echo [HATA] 'desktop-native\go-engine\main.go' dosyasi bulunamadi!
    pause
    goto menu
)

echo [ISLEM] Go ile optimize edilmis yerel ikili derleniyor...
if not exist "%~dp0desktop-native\target\release\" mkdir "%~dp0desktop-native\target\release" >nul 2>&1
call "%GO_BIN%" build -ldflags "-H windowsgui -s -w" -o "%~dp0desktop-native\target\release\notdefterim.exe" "%~dp0desktop-native\go-engine\main.go"
if %errorlevel% neq 0 (
    color 0C
    echo.
    echo [HATA] Go derleme islemi basarisiz oldu! Hata kodu: %errorlevel%
) else (
    color 0A
    echo.
    echo [BASARILI] Go Native masaustu uygulamasi basariyla derlendi!
    echo Hedef Konum: desktop-native\target\release\notdefterim.exe
)
echo.
pause
goto menu

:derle_rust
cls
color 0B
echo ===============================================================================
echo   [3/6] Rust Masaustu Surumunu Derle (cargo build --release)
echo ===============================================================================
echo.
where cargo >nul 2>&1
if %errorlevel% neq 0 (
    color 0C
    echo [HATA] Rust paket yoneticisi 'cargo' sistemde bulunamadi!
    pause
    goto menu
)
cd /d "%~dp0desktop-native"
echo [ISLEM] Optimize edilmis Release surumu derleniyor [cargo build --release]...
echo.
call cargo build --release
if %errorlevel% neq 0 (
    color 0C
    echo.
    echo [HATA] Derleme islemi basarisiz oldu! Hata kodu: %errorlevel%
) else (
    color 0A
    echo.
    echo [BASARILI] Rust masaustu surumu basariyla derlendi!
    echo Hedef Konum: desktop-native\target\release\notdefterim.exe
)
cd /d "%~dp0"
echo.
pause
goto menu

:testler_rust
cls
color 0B
echo ===============================================================================
echo   [4/6] Rust Masaustu Birim Testlerini Calistir (cargo test)
echo ===============================================================================
echo.
where cargo >nul 2>&1
if %errorlevel% neq 0 (
    color 0C
    echo [HATA] Rust paket yoneticisi 'cargo' sistemde bulunamadi!
    pause
    goto menu
)
cd /d "%~dp0desktop-native"
echo [ISLEM] Rust birim ve entegrasyon testleri yurutuluyor [cargo test]...
echo.
call cargo test
if %errorlevel% neq 0 (
    color 0E
    echo.
    echo [UYARI] Testler sirasinda bazi basarisizliklar veya uyarilar olustu.
) else (
    color 0A
    echo.
    echo [BASARILI] Tum Rust birim testleri basariyla tamamlandi!
)
cd /d "%~dp0"
echo.
pause
goto menu

:wifi_sync_test
cls
color 0B
echo ===============================================================================
echo   [5/6] Wi-Fi Senkronizasyon Test Paketini Calistir (Gelismis LAN Dogrulayici)
echo ===============================================================================
echo.
cd /d "%~dp0"

:: 1. Oncelik: Proje ici batch baslatici
if exist "%~dp0tools\sync_tester\run_tests.bat" (
    echo [BILGI] 'tools\sync_tester\run_tests.bat' bulundu, testler baslatiliyor...
    echo.
    call "%~dp0tools\sync_tester\run_tests.bat"
    goto wifi_sync_sonuc
)
if exist "%~dp0tools\sync_tester\Test_WiFi_Sync.bat" (
    echo [BILGI] 'tools\sync_tester\Test_WiFi_Sync.bat' bulundu, testler baslatiliyor...
    echo.
    call "%~dp0tools\sync_tester\Test_WiFi_Sync.bat"
    goto wifi_sync_sonuc
)

:: 2. Oncelik: Proje ici Node.js motoru
if exist "%~dp0tools\sync_tester\index.js" (
    where node >nul 2>&1
    if %errorlevel% neq 0 (
        color 0C
        echo [HATA] Node.js sisteminizde kurulu veya PATH icinde degil!
        pause
        goto menu
    )
    echo [BILGI] 'node tools/sync_tester/index.js' calistiriliyor...
    echo.
    call node "%~dp0tools\sync_tester\index.js"
    goto wifi_sync_sonuc
)

:: 3. Oncelik: Yeni kategorik konum veya ust dizin wifi-sync-tester\run_tests.bat
if exist "%~dp0..\..\araclar-ve-servisler\wifi-sync-tester\run_tests.bat" (
    echo [BILGI] '..\..\araclar-ve-servisler\wifi-sync-tester\run_tests.bat' bulundu, testler baslatiliyor...
    echo.
    call "%~dp0..\..\araclar-ve-servisler\wifi-sync-tester\run_tests.bat"
    goto wifi_sync_sonuc
)
if exist "%~dp0..\tools_sync_tester\run_tests.bat" (
    echo [BILGI] '..\tools_sync_tester\run_tests.bat' bulundu, testler baslatiliyor...
    echo.
    call "%~dp0..\tools_sync_tester\run_tests.bat"
    goto wifi_sync_sonuc
)

:: 4. Oncelik: Ust dizin tools_sync_tester\index.js
if exist "%~dp0..\tools_sync_tester\index.js" (
    echo [BILGI] 'node ..\tools_sync_tester\index.js' calistiriliyor...
    echo.
    call node "%~dp0..\tools_sync_tester\index.js"
    goto wifi_sync_sonuc
)

:: 5. Oncelik: Bagimsiz Python Test Istemcisi
if exist "%~dp0tools\sync_tester\sync_tester.py" (
    echo [BILGI] Python test araci bulundu, calistiriliyor...
    echo.
    call python "%~dp0tools\sync_tester\sync_tester.py"
    goto wifi_sync_sonuc
)
if exist "%~dp0..\tools_sync_tester\sync_tester.py" (
    echo [BILGI] Python test araci bulundu, calistiriliyor...
    echo.
    call python "%~dp0..\tools_sync_tester\sync_tester.py"
    goto wifi_sync_sonuc
)

:: Test paketi henuz hazir degilse rehberlik
color 0E
echo [BILGI] 'tools\sync_tester' test paketi henuz hazir degil veya bulunamadi.
echo Lutfen tools\sync_tester dizininin mevcut oldugundan emin olun.
goto wifi_sync_bitis

:wifi_sync_sonuc
if %errorlevel% neq 0 (
    color 0C
    echo.
    echo [UYARI] Wi-Fi Senkronizasyon Test Paketi hata veya uyari ile sonuclandi [Kod: %errorlevel%].
) else (
    color 0A
    echo.
    echo [BASARILI] Wi-Fi Senkronizasyon Testleri basariyla tamamlandi!
)

:wifi_sync_bitis
echo.
pause
goto menu

:hizli_teshis
cls
color 0B
echo ===============================================================================
echo   [6/6] Hizli Teshis ve Sistem Durumu (Go, Rust, Port 8765, Dosyalar)
echo ===============================================================================
echo.

:: 1. Go Calisma Zamani Denetimi
echo [1/5] Go Calisma Zamani Denetimi:
set "GO_DETECTED="
where go >nul 2>&1 && set "GO_DETECTED=go"
if not defined GO_DETECTED (
    if exist "C:\Program Files\Go\bin\go.exe" set "GO_DETECTED=C:\Program Files\Go\bin\go.exe"
)
if defined GO_DETECTED (
    for /f "tokens=*" %%v in ('"%GO_DETECTED%" version 2^>nul') do echo   - Go Surumu      : %%v [KURULU - TAMAM]
) else (
    echo   - Go             : [BULUNAMADI] - Go Native ikili derlemesi icin onerilir
)

:: 2. Rust ve Cargo Calisma Zamani Denetimi
echo.
echo [2/5] Rust ve Cargo Calisma Zamani Denetimi:
where rustc >nul 2>&1
if %errorlevel% equ 0 (
    for /f "tokens=*" %%v in ('rustc --version 2^>nul') do echo   - rustc Surumu   : %%v [KURULU - TAMAM]
) else (
    echo   - rustc          : [BULUNAMADI - BILGI]
)

where cargo >nul 2>&1
if %errorlevel% equ 0 (
    for /f "tokens=*" %%v in ('cargo --version 2^>nul') do echo   - cargo Surumu   : %%v [KURULU - TAMAM]
) else (
    echo   - cargo          : [BULUNAMADI - BILGI]
)

:: 3. Wi-Fi Senkronizasyon Portu (8765) Kontrolu
echo.
echo [3/5] Wi-Fi Senkronizasyon Portu [8765] Ag Denetimi:
netstat -ano | findstr /c:":8765" >nul 2>&1
if %errorlevel% equ 0 (
    color 0E
    echo   - Port 8765 Durumu: AKTIF / KULLANIMDA - Senkronizasyon sunucusu dinliyor
    echo     Ayrintili Soket Bilgisi:
    netstat -ano | findstr /c:":8765"
) else (
    color 0A
    echo   - Port 8765 Durumu: SERBEST - Kullanima hazir, cakisma yok [TAMAM]
)

:: 4. Yerel Ag (LAN) IPv4 Adresleri
echo.
color 0B
echo [4/5] Yerel IP Adresleri - Wi-Fi Senkronizasyon Cihaz Eslesmesi Icin:
for /f "tokens=2 delims=:" %%i in ('ipconfig ^| findstr /i "IPv4"') do (
    echo   - Yerel IP :%%i
)

:: 5. Dosya ve Bilesen Kontrolleri
echo.
echo [5/5] Proje Bilesen Durumlari:
if exist "%~dp0desktop-native\target\release\notdefterim.exe" (
    echo   - desktop-native\target [Release Exe] : [DERLENMIS - CALISMAYA HAZIR]
) else (
    echo   - desktop-native\target [Release Exe] : [HENUZ DERLENMEDI - Secenek 2 ile derlenebilir]
)
if exist "%~dp0desktop-native\go-engine\main.go" (
    echo   - desktop-native\go-engine [Go Motor] : [MEVCUT]
) else (
    echo   - desktop-native\go-engine [Go Motor] : [EKSIK]
)
if exist "%~dp0desktop-native\ui\index.html" (
    echo   - desktop-native\ui [Arayuz]          : [MEVCUT]
) else (
    echo   - desktop-native\ui [Arayuz]          : [EKSIK]
)
if exist "%~dp0desktop-native\Cargo.toml" (
    echo   - desktop-native\Cargo.toml           : [MEVCUT - RUST PROJESI HAZIR]
) else (
    echo   - desktop-native\Cargo.toml           : [EKSIK]
)
if exist "%~dp0gradlew.bat" (
    echo   - Android Gradle [gradlew.bat]        : [MEVCUT]
) else (
    echo   - Android Gradle [gradlew.bat]        : [EKSIK]
)
if exist "%~dp0tools\sync_tester\" (
    echo   - tools\sync_tester                   : [MEVCUT]
) else (
    if exist "%~dp0..\tools_sync_tester\" (
        echo   - tools_sync_tester                   : [UST DIZINDE MEVCUT]
    ) else (
        echo   - tools\sync_tester                   : [HENUZ OLUSTURULMADI]
    )
)

echo.
echo ===============================================================================
echo Sistem ve Ag Portu Teshisi Tamamlandi.
echo.
pause
goto menu

:cikis
cls
color 07
echo Not Defterim Masaustu Yonetim Merkezi'nden cikiliyor. Iyi calismalar!
timeout /t 1 >nul 2>&1 || ping -n 2 127.0.0.1 >nul
exit /b 0
