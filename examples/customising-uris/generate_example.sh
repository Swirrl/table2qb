#!/bin/bash

set -e

PROJECT_DIR=$(dirname $(dirname "$0"))
JAR="$PROJECT_DIR/../../target/table2qb.jar"
TABLE2QB="java -jar $JAR"
EXAMPLE_DIR=$PROJECT_DIR
CSVW_DIR="$EXAMPLE_DIR/csvw"

BASE_URI="https://id.milieuinfo.be/"

$TABLE2QB exec codelist-pipeline \
--codelist-csv csv/substanties.csv \
--codelist-name "substanties (IMJV)" \
--codelist-slug "substantie" \
--base-uri $BASE_URI \
--uris-file uri/codelists.edn \
--output-file ttl/substanties.ttl
#--output-directory "$CSVW_DIR"
