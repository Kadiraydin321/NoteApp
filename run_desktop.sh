#!/usr/bin/env bash
set -e
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$DIR"
echo "Not Defterim Masaüstü Başlatılıyor..."
./gradlew :desktop:run
