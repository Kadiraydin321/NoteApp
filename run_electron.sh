#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PATH="/home/birin/.local/node/bin:$PATH"

cd "$SCRIPT_DIR/desktop-electron"
echo "Starting Not Defterim (Electron JavaScript Desktop)..."
npm start
