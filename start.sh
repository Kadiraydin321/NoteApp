#!/usr/bin/env bash
# ByteRescue Masaüstü Veri Kurtarma Uygulaması Başlatıcı

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

echo "=========================================================="
echo "  ⚡ BYTERESCUE - Ham Sektör Veri Kurtarma Laboratuvarı"
echo "=========================================================="
echo "Uygulama başlatılıyor..."

# Dist kontrolü
if [ ! -d "$DIR/dist" ]; then
    echo "Ön yüz derleniyor (vite build)..."
    npm run build
fi

# Electron ile başlat
exec "$DIR/node_modules/.bin/electron" . --no-sandbox "$@"
