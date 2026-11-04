@echo off
title Not Defterim - Masaustu Yonetim Merkezi
color 0B
cd /d "%~dp0"

:menu
cls
color 0B
echo ===============================================================================
echo                 NOT DEFTERIM - MASAUSTU YONETIM MERKEZI
echo ===============================================================================
echo.
echo   [1] Not Defterim (Compose Multiplatform Kotlin)
echo   [2] Not Defterim (JavaScript / Electron Surumu)
echo   [3] Windows Kurulum Paketi Uret (.msi)
echo   [4] Tum Birim Testlerini Calistir (Kotlin + JS)
echo   [5] Wi-Fi Senkronizasyon Test Paketini Calistir (Gelismis LAN Dogrulayici)
echo   [6] Hizli Teshis ve Sistem Durumu (Node.js ve Ag Portu Kontrolu)
echo   [0] Cikis
echo.
echo ===============================================================================
set /p secim="Seciminiz [1/2/3/4/5/6/0]: "
if defined secim set "secim=%secim: =%"

if "%secim%"=="1" goto baslat_compose
if "%secim%"=="2" goto baslat_electron
if "%secim%"=="3" goto paketle
if "%secim%"=="4" goto testler
if "%secim%"=="5" goto wifi_sync_test
if "%secim%"=="6" goto hizli_teshis
if "%secim%"=="0" goto cikis

color 0C
echo.
echo [UYARI] Gecersiz secim: "%secim%"! Lutfen menudeki seceneklerden birini girin.
timeout /t 2 >nul 2>&1 || ping -n 3 127.0.0.1 >nul
goto menu

:baslat_compose
cls
color 0B
echo ===============================================================================
echo   [1/6] Not Defterim (Compose Desktop Kotlin) Baslatiliyor...
echo ===============================================================================
echo.
if not exist "%~dp0gradlew.bat" (
    color 0C
    echo [HATA] 'gradlew.bat' dosyasi ana dizinde bulunamadi!
    echo Lutfen proje kok dizininde oldugunuzdan emin olun.
    pause
    goto menu
)
where java >nul 2>&1
if %errorlevel% neq 0 (
    color 0C
    echo [HATA] Java JDK bulunamadi veya PATH ortam degiskenine eklenmemis!
    echo Lutfen JDK 17+ kurulu oldugundan emin olun.
    pause
    goto menu
)
echo [BILGI] Gradle gorevi yurutuluyor: ':desktop:run'...
echo.
call "%~dp0gradlew.bat" :desktop:run
if %errorlevel% neq 0 (
    color 0C
    echo.
    echo [HATA] Compose Desktop baslatilirken bir hata olustu! Hata kodu: %errorlevel%
    pause
)
goto menu

:baslat_electron
cls
color 0B
echo ===============================================================================
echo   [2/6] Not Defterim (JavaScript / Electron Surumu) Baslatiliyor...
echo ===============================================================================
echo.
cd /d "%~dp0desktop-electron"

where node >nul 2>&1
if %errorlevel% neq 0 (
    color 0C
    echo [HATA] Node.js sistemde bulunamadi veya PATH ortam degiskenine eklenmemis!
    echo Lutfen https://nodejs.org adresinden Node.js LTS surumunu kurun.
    pause
    cd /d "%~dp0"
    goto menu
)

:: Otomatik 'npm install' ve bagimlilik kontrol mekanizmasi
set "INSTALL_NEEDED=0"
if not exist "node_modules\" set "INSTALL_NEEDED=1"
if not exist "node_modules\electron\" set "INSTALL_NEEDED=1"

if "%INSTALL_NEEDED%"=="1" (
    color 0E
    echo [BILGI] 'desktop-electron\node_modules' veya Electron calisma zamani eksik tespit edildi.
    echo [ISLEM] Bagimliliklar otomatik kuruluyor [npm install]... Lutfen bekleyin.
    echo.
    call npm install
    if %errorlevel% neq 0 (
        color 0C
        echo.
        echo [HATA] npm paket kurulumu basarisiz oldu! Hata kodu: %errorlevel%
        pause
        cd /d "%~dp0"
        goto menu
    )
    echo.
    echo [BASARILI] Bagimliliklar basariyla kuruldu!
    echo.
)

color 0A
echo [BILGI] Electron masaustu uygulamasi baslatiliyor [npm start]...
echo.
call npm start
if %errorlevel% neq 0 (
    color 0C
    echo.
    echo [BILGI] Uygulama sonlandi veya hata kodu dondu: %errorlevel%
    pause
)
cd /d "%~dp0"
goto menu

:paketle
cls
color 0B
echo ===============================================================================
echo   [3/6] Windows Kurulum Paketi Uret (.msi)
echo ===============================================================================
echo.
if not exist "%~dp0gradlew.bat" (
    color 0C
    echo [HATA] 'gradlew.bat' dosyasi bulunamadi!
    pause
    goto menu
)
echo [ISLEM] WiX Toolset ile Windows Kurulum Paketi (.msi) uretiliyor...
echo Gorev: ':desktop:packageMsi'
echo.
call "%~dp0gradlew.bat" :desktop:packageMsi
if %errorlevel% neq 0 (
    color 0C
    echo.
    echo [HATA] Paketleme islemi basarisiz oldu! Hata kodu: %errorlevel%
) else (
    color 0A
    echo.
    echo [BASARILI] Kurulum paketi (.msi) basariyla olusturuldu!
    echo Hedef Konum: desktop\build\compose\binaries\main\msi
)
echo.
pause
goto menu

:testler
cls
color 0B
echo ===============================================================================
echo   [4/6] Tum Birim Testlerini Calistir (Kotlin + JS)
echo ===============================================================================
echo.
echo [1/2] Kotlin Compose Testleri Baslatiliyor...
if exist "%~dp0gradlew.bat" (
    call "%~dp0gradlew.bat" :desktop:test
    if %errorlevel% neq 0 (
        color 0E
        echo.
        echo [UYARI] Kotlin testleri sirasinda uyari veya hata olustu.
    ) else (
        echo.
        echo [BASARILI] Kotlin birim testleri basariyla tamamlandi.
    )
) else (
    echo [ATLANDI] 'gradlew.bat' bulunamadi.
)

echo.
echo [2/2] Electron JavaScript Testleri Baslatiliyor...
cd /d "%~dp0desktop-electron"
if exist "package.json" (
    if not exist "node_modules\" (
        echo [BILGI] Testler oncesi eksik bagimliliklar yukleniyor...
        call npm install
    )
    call npm test
    if %errorlevel% neq 0 (
        color 0E
        echo.
        echo [UYARI] JavaScript testleri sirasinda uyari veya hata olustu.
    ) else (
        echo.
        echo [BASARILI] JavaScript birim testleri basariyla tamamlandi.
    )
) else (
    echo [ATLANDI] 'desktop-electron\package.json' bulunamadi.
)
cd /d "%~dp0"

echo.
echo ===============================================================================
echo Tum birim test dongusu tamamlandi.
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
echo [BILGI] 'tools\sync_tester' veya 'tools_sync_tester' test paketi henuz tamamlanmadi.
echo.
echo Mevcut alternatif Electron testlerini calistirmak ister misiniz?
echo   [1] Electron Birim/Entegrasyon Testlerini Calistir (npm test)
echo   [2] Ana Menuye Don
echo.
set /p sync_alt="Seciminiz [1/2]: "
if "%sync_alt%"=="1" (
    cd /d "%~dp0desktop-electron"
    call npm test
    cd /d "%~dp0"
)
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
echo   [6/6] Hizli Teshis ve Sistem Durumu (Node.js ve Ag Portu Kontrolu)
echo ===============================================================================
echo.

:: 1. Node.js ve npm Durumu
echo [1/4] Node.js ve npm Calisma Zamani Denetimi:
where node >nul 2>&1
if %errorlevel% equ 0 (
    for /f "tokens=*" %%v in ('node -v 2^>nul') do echo   - Node.js Surumu : %%v [KURULU - TAMAM]
) else (
    echo   - Node.js        : [BULUNAMADI - UYARI] - Electron surumu icin gereklidir
)

where npm >nul 2>&1
if %errorlevel% equ 0 (
    for /f "tokens=*" %%v in ('npm -v 2^>nul') do echo   - npm Surumu     : %%v [KURULU - TAMAM]
) else (
    echo   - npm            : [BULUNAMADI - UYARI]
)

:: 2. Java JDK Durumu
echo.
echo [2/4] Java JDK Calisma Zamani Denetimi:
where java >nul 2>&1
if %errorlevel% equ 0 (
    echo   - Java [JDK]     : [KURULU - TAMAM] - Compose Desktop icin hazir
) else (
    echo   - Java [JDK]     : [BULUNAMADI - UYARI] - Compose Kotlin icin gereklidir
)

:: 3. Wi-Fi Senkronizasyon Portu (8765) Kontrolu
echo.
echo [3/4] Wi-Fi Senkronizasyon Portu [8765] Ag Denetimi:
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
echo [4/4] Yerel IP Adresleri - Wi-Fi Senkronizasyon Cihaz Eslesmesi Icin:
for /f "tokens=2 delims=:" %%i in ('ipconfig ^| findstr /i "IPv4"') do (
    echo   - Yerel IP :%%i
)

:: 5. Dosya ve Bilesen Kontrolleri
echo.
echo [BILGI] Proje Bilesen Durumlari:
if exist "%~dp0desktop-electron\node_modules\" (
    echo   - desktop-electron\node_modules : [MEVCUT]
) else (
    echo   - desktop-electron\node_modules : [EKSIK] - Secenek [2] ile otomatik yuklenir
)
if exist "%~dp0gradlew.bat" (
    echo   - Gradle Wrapper [gradlew.bat]   : [MEVCUT]
) else (
    echo   - Gradle Wrapper [gradlew.bat]   : [EKSIK]
)
if exist "%~dp0tools\sync_tester\" (
    echo   - tools\sync_tester             : [MEVCUT]
) else if exist "%~dp0..\tools_sync_tester\" (
    echo   - tools_sync_tester             : [UST DIZINDE MEVCUT]
) else (
    echo   - tools\sync_tester             : [HENUZ OLUSTURULMADI]
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
