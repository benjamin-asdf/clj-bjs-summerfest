#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Starting Summer Fest dev server..."
clojure -M:dev
