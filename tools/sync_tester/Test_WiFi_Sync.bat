@echo off
title LAN / Wi-Fi Sync Tester ve Dogrulama Cercevesi
color 0B
cd /d "%~dp0"

:MENU
cls
color 0B
echo ===============================================================================
echo        CROSS-PLATFORM LAN / WI-FI SYNC TEST VE DOGRULAMA CERCEVESI
echo               Ileriye Donuk, Bagimsiz Wi-Fi Test ve Raporlama Paketi
echo ===============================================================================
echo.
echo   [1] Yerel Not Defterim Sunucusunu Test Et [127.0.0.1:8765 - PIN Sorar]
echo   [2] Ozel IP / Port / PIN ile Test Et [Telefon veya Agdaki Baska Cihaz]
echo   [3] Yerel Agda (LAN) Sunucu Otomatik Kesif Taramasi [Auto-Discovery]
echo   [4] Dahili Mock Sunucu ile Kendi Kendini Test Et [Node.js Self-Test]
echo   [5] Dahili Mock Sunucu ile Kendi Kendini Test Et [Python 3 Self-Test]
echo   [6] Bagimsiz Mock Sunucu Baslat [Gelistirme / Test icin acik tut]
echo   [7] Uretilen Test Raporunu Ac [SYNC_TEST_REPORT.md]
echo   [0] Cikis
echo.
echo ===============================================================================
set /p SECIM="Lutfen bir islem secin [0-7]: "
if defined SECIM set "SECIM=%SECIM: =%"

if "%SECIM%"=="1" goto LOCAL_TEST
if "%SECIM%"=="2" goto CUSTOM_TEST
if "%SECIM%"=="3" goto DISCOVERY_TEST
if "%SECIM%"=="4" goto MOCK_NODE_TEST
if "%SECIM%"=="5" goto MOCK_PY_TEST
if "%SECIM%"=="6" goto RUN_MOCK_SERVER
if "%SECIM%"=="7" goto OPEN_REPORT
if "%SECIM%"=="0" goto EXIT_PROMPT

color 0C
echo Gecersiz secim! Tekrar deneyin.
timeout /t 2 >nul 2>&1 || ping -n 3 127.0.0.1 >nul
goto MENU

:LOCAL_TEST
cls
color 0B
echo [!] Yerel sunucu testi baslatiliyor [127.0.0.1:8765]...
set /p PIN_INPUT="Not Defterim Masaustu uygulamasinda gorunen 4 haneli PIN kodunu girin [Varsayilan: 1234]: "
if "%PIN_INPUT%"=="" set PIN_INPUT=1234
echo.
call node "%~dp0run_tests.js" --host 127.0.0.1 --port 8765 --pin %PIN_INPUT%
echo.
pause
goto MENU

:CUSTOM_TEST
cls
color 0B
echo [!] Ozel Sunucu Yapilandirmasi:
set /p HOST_INPUT="Hedef IP Adresi [Orn: 192.168.1.45]: "
set /p PORT_INPUT="Hedef Port [Varsayilan: 8765]: "
if "%PORT_INPUT%"=="" set PORT_INPUT=8765
set /p PIN_INPUT="Eslesme PIN Kodu [Varsayilan: 1234]: "
if "%PIN_INPUT%"=="" set PIN_INPUT=1234
echo.
call node "%~dp0run_tests.js" --host %HOST_INPUT% --port %PORT_INPUT% --pin %PIN_INPUT%
echo.
pause
goto MENU

:DISCOVERY_TEST
cls
color 0B
echo [!] Agdaki aktif senkronizasyon sunuculari taraniyor...
echo.
call node "%~dp0run_tests.js" --discover
echo.
pause
goto MENU

:MOCK_NODE_TEST
cls
color 0B
echo [!] Node.js dahili Mock Sunucu baslatiliyor ve tum testler kosturuluyor...
echo.
call node "%~dp0run_tests.js" --mock
echo.
pause
goto MENU

:MOCK_PY_TEST
cls
color 0B
echo [!] Python 3 dahili Mock Sunucu baslatiliyor ve tum testler kosturuluyor...
echo.
call python "%~dp0run_tests.py" --mock
echo.
pause
goto MENU

:RUN_MOCK_SERVER
cls
color 0B
echo ===============================================================================
echo  Dahili Standalone Mock Wi-Fi Sunucusu Baslatiliyor...
echo  Port: 8765 | PIN: 1234
echo  Kapatmak icin bu pencereyi kapatabilir veya Ctrl+C yapabilirsiniz.
echo ===============================================================================
echo.
call node "%~dp0mock_server.js" --port 8765 --pin 1234
pause
goto MENU

:OPEN_REPORT
cls
color 0B
if exist "%~dp0SYNC_TEST_REPORT.md" (
    start "" "%~dp0SYNC_TEST_REPORT.md"
    echo Rapor varsayilan metin/markdown goruntuleyicide acildi.
) else (
    echo Henuz uretilmis bir SYNC_TEST_REPORT.md dosyasi bulunamadi. Lutfen once bir test calistirin.
)
timeout /t 3 >nul
goto MENU

:EXIT_PROMPT
cls
color 07
echo Cikiliyor...
timeout /t 1 >nul
exit /b 0
