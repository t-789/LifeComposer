#!/bin/bash
# LifeComposer standalone data import CLI wrapper.
# Does NOT start the web server and does NOT bootstrap the default admin.
#
# Examples:
#   ./import.sh --import-dir=../样例 --report=target/import-report.json
#   ./import.sh --import-dir=../样例 --dry-run
#   ./import.sh --import-dir=../样例 --rag-chunks --rebuild-embeddings
set -e
cd "$(dirname "$0")"
exec ./mvnw -q spring-boot:run \
  -Dspring-boot.run.main-class=org.example.lifecomposer.importer.DataImportCli \
  -Dspring-boot.run.arguments="$*"
