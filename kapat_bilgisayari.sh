#!/usr/bin/env bash
echo "==================================================="
echo "Bilgisayar 60 saniye içinde kapatılacak..."
echo "İptal etmek isterseniz: shutdown.exe /a"
echo "==================================================="

if [ -f "/mnt/c/WINDOWS/system32/shutdown.exe" ]; then
    /mnt/c/WINDOWS/system32/shutdown.exe /s /t 60 /c "Not Defterim gelistirme islemleri tamamlandi. Bilgisayar kapatiliyor."
else
    shutdown -h +1 "Bilgisayar kapatılıyor" 2>/dev/null || true
fi
