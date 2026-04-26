#!/bin/bash
set -e

HF_BASE="https://huggingface.co"

check_file() {
    local path="$1"
    local min_size="$2"
    if [ ! -f "$path" ]; then
        echo "[entrypoint] MISSING: $path"
        return 1
    fi
    local size
    size=$(stat -c%s "$path" 2>/dev/null || stat -f%z "$path" 2>/dev/null)
    if [ -n "$min_size" ] && [ "$size" -lt "$min_size" ]; then
        echo "[entrypoint] CORRUPT (too small, $size < $min_size bytes): $path"
        rm -f "$path"
        return 1
    fi
    echo "[entrypoint] VALID: $path ($size bytes)"
    return 0
}

download_if_missing() {
    local path="$1"
    local url="$2"
    local min_size="${3:-10000}"
    if check_file "$path" "$min_size"; then
        return 0
    fi
    echo "[entrypoint] Downloading $(basename "$path")..."
    local attempts=0
    local max_attempts=3
    while [ $attempts -lt $max_attempts ]; do
        attempts=$((attempts + 1))
        if wget --no-check-certificate -q -O "$path.tmp" "$url"; then
            if check_file "$path.tmp" "$min_size"; then
                mv "$path.tmp" "$path"
                echo "[entrypoint] Downloaded: $path"
                return 0
            fi
            echo "[entrypoint] Attempt $attempts: downloaded file too small"
        else
            echo "[entrypoint] Attempt $attempts: wget failed (exit $?)"
        fi
        rm -f "$path.tmp"
        if [ $attempts -lt $max_attempts ]; then
            echo "[entrypoint] Retrying in 5 seconds..."
            sleep 5
        fi
    done
    echo "[entrypoint] ERROR: failed to download $url after $max_attempts attempts"
    exit 1
}

mkdir -p /app/model/onnx /app/model/reranker/onnx /app/index /vault

download_if_missing "/app/model/onnx/model.onnx" \
    "$HF_BASE/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx" 50000000

download_if_missing "/app/model/onnx/vocab.txt" \
    "$HF_BASE/sentence-transformers/all-MiniLM-L6-v2/raw/main/vocab.txt" 1000

download_if_missing "/app/model/reranker/onnx/model.onnx" \
    "$HF_BASE/cross-encoder/ms-marco-MiniLM-L-6-v2/resolve/main/onnx/model.onnx" 10000000

download_if_missing "/app/model/reranker/onnx/vocab.txt" \
    "$HF_BASE/cross-encoder/ms-marco-MiniLM-L-6-v2/raw/main/vocab.txt" 1000

exec "$@"