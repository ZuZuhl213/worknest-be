#!/usr/bin/env sh
set -eu

docker compose up -d postgres redis mailpit minio minio-init
