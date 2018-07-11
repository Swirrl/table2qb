#!/bin/bash
set -e

SCRIPT_DIR=$(dirname "$0")

if ! [ -x "$(command -v java)" ]; then
  echo 'Error: java not found. Install java 8.'
  exit 1
fi

java -jar "$SCRIPT_DIR/table2qb.jar" "$@"
