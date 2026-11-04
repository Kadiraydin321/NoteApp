@echo off
title Not Defterim - Rust Native Masaustu Yonetim Merkezi
color 0B
cd /d "%~dp0"

:menu
cls
color 0B
echo ===============================================================================
echo             NOT DEFTERIM - MODERN RUST NATIVE YONETIM MERKEZI
echo ===============================================================================
echo.
echo   [1] Not Defterim Masaustu Baslat (Rust / Native GUI)
echo   [2] Rust Masaustu Surumunu Hizli Derle (cargo build --release)
echo   [3] Rust Masaustu Birim Testlerini Calistir (cargo test)
echo   [4] Wi-Fi Senkronizasyon Test Paketini Calistir (Gelismis LAN Dogrulayici)
echo   [5] Hizli Teshis ve Sistem Durumu (Rust, Cargo ve Ag Portu Kontrolu)
echo   [0] Cikis
echo.
echo ===============================================================================
set /p secim="Seciminiz [1/2/3/4/5/0]: "
if defined secim set "secim=%secim: =%"

if "%secim%"=="1" goto baslat_rust
if "%secim%"=="2" goto derle_rust
if "%secim%"=="3" goto testler_rust
if "%secim%"=="4" goto wifi_sync_test
if "%secim%"=="5" goto hizli_teshis
if "%secim%"=="0" goto cikis

color 0C
echo.
echo [UYARI] Gecersiz secim: "%secim%"! Lutfen menudeki seceneklerden birini girin.
timeout /t 2 >nul 2>&1 || ping -n 3 127.0.0.1 >nul
goto menu

:baslat_rust
cls
color 0B
echo ===============================================================================
echo   [1/5] Not Defterim (Rust / Native GUI) Baslatiliyor...
echo ===============================================================================
echo.

:: 1. Oncelik: Derlenmis Release ikili dosyasi
if exist "%~dp0desktop-native\target\release\notdefterim.exe" (
    color 0A
    echo [BILGI] Derlenmis Release surumu baslatiliyor: notdefterim.exe...
    start "" "%~dp0desktop-native\target\release\notdefterim.exe" %*
    goto baslat_rust_sonuc
)

:: 2. Oncelik: Derlenmis Debug ikili dosyasi
if exist "%~dp0desktop-native\target\debug\notdefterim.exe" (
    color 0A
    echo [BILGI] Derlenmis Debug surumu baslatiliyor: notdefterim.exe...
    start "" "%~dp0desktop-native\target\debug\notdefterim.exe" %*
    goto baslat_rust_sonuc
)

:: 3. Oncelik: Cargo Run ile kaynak koddan derleyip calistirma
where cargo >nul 2>&1
if %errorlevel% neq 0 (
    color 0C
    echo [HATA] Rust paket yoneticisi 'cargo' bulunamadi veya PATH ortam degiskeninde yok!
    echo Lutfen https://rustup.rs adresinden Rust arac setini kurun.
    pause
    goto menu
)

color 0E
echo [BILGI] Derlenmis hazir ikili bulunamadi. Cargo ile calistiriliyor [cargo run]...
cd /d "%~dp0desktop-native"
cargo run
if %errorlevel% neq 0 (
    color 0C
    echo.
    echo [HATA] Uygulama calistirilamadi! Hata kodu: %errorlevel%
    pause
)
cd /d "%~dp0"
goto menu

:baslat_rust_sonuc
color 0A
echo [BASARILI] Not Defterim Masaustu uygulamasi arka planda baslatildi.
timeout /t 2 >nul 2>&1 || ping -n 3 127.0.0.1 >nul
goto menu

:derle_rust
cls
color 0B
echo ===============================================================================
echo   [2/5] Rust Masaustu Surumunu Hizli Derle (cargo build --release)
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
echo   [3/5] Rust Masaustu Birim Testlerini Calistir (cargo test)
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
echo   [5/5] Hizli Teshis ve Sistem Durumu (Rust, Cargo ve Ag Portu Kontrolu)
echo ===============================================================================
echo.

:: 1. Rust ve Cargo Calisma Zamani Denetimi
echo [1/4] Rust ve Cargo Calisma Zamani Denetimi:
where rustc >nul 2>&1
if %errorlevel% equ 0 (
    for /f "tokens=*" %%v in ('rustc --version 2^>nul') do echo   - rustc Surumu   : %%v [KURULU - TAMAM]
) else (
    echo   - rustc          : [BULUNAMADI - UYARI] - Rust derleyicisi gereklidir
)

where cargo >nul 2>&1
if %errorlevel% equ 0 (
    for /f "tokens=*" %%v in ('cargo --version 2^>nul') do echo   - cargo Surumu   : %%v [KURULU - TAMAM]
) else (
    echo   - cargo          : [BULUNAMADI - UYARI] - Cargo paket yoneticisi gereklidir
)

:: 2. Wi-Fi Senkronizasyon Portu (8765) Kontrolu
echo.
echo [2/4] Wi-Fi Senkronizasyon Portu [8765] Ag Denetimi:
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

:: 3. Yerel Ag (LAN) IPv4 Adresleri
echo.
color 0B
echo [3/4] Yerel IP Adresleri - Wi-Fi Senkronizasyon Cihaz Eslesmesi Icin:
for /f "tokens=2 delims=:" %%i in ('ipconfig ^| findstr /i "IPv4"') do (
    echo   - Yerel IP :%%i
)

:: 4. Dosya ve Bilesen Kontrolleri
echo.
echo [4/4] Proje Bilesen Durumlari:
if exist "%~dp0desktop-native\Cargo.toml" (
    echo   - desktop-native\Cargo.toml         : [MEVCUT - RUST PROJESI HAZIR]
) else (
    echo   - desktop-native\Cargo.toml         : [EKSIK]
)
if exist "%~dp0desktop-native\ui\index.html" (
    echo   - desktop-native\ui [Arayuz]        : [MEVCUT]
) else (
    echo   - desktop-native\ui [Arayuz]        : [EKSIK]
)
if exist "%~dp0desktop-native\target\release\notdefterim.exe" (
    echo   - desktop-native\target [Release]   : [DERLENMIS - HAZIR]
) else (
    echo   - desktop-native\target [Release]   : [HENUZ DERLENMEDI - Secenek 2 ile derlenebilir]
)
if exist "%~dp0gradlew.bat" (
    echo   - Android Gradle [gradlew.bat]      : [MEVCUT]
) else (
    echo   - Android Gradle [gradlew.bat]      : [EKSIK]
)
if exist "%~dp0tools\sync_tester\" (
    echo   - tools\sync_tester                 : [MEVCUT]
) else (
    if exist "%~dp0..\tools_sync_tester\" (
        echo   - tools_sync_tester                 : [UST DIZINDE MEVCUT]
    ) else (
        echo   - tools\sync_tester                 : [HENUZ OLUSTURULMADI]
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
