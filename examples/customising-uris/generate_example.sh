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
--uri-templates templates/codelists.edn \
--output-file ttl/substanties.ttl
#--output-directory "$CSVW_DIR"

$TABLE2QB exec components-pipeline \
--input-csv csv/components.csv \
--base-uri $BASE_URI \
--uri-templates templates/components.edn \
--output-file ttl/components.ttl

$TABLE2QB exec cube-pipeline \
--input-csv csv/observations.csv \
--dataset-name "kubus luchtemissies" \
--dataset-slug "luchtemisses" \
--column-config columns.csv \
--base-uri $BASE_URI \
--uri-templates templates/cube.edn \
--output-file ttl/cube.ttl
