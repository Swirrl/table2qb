#!/bin/bash

set -e

SCRIPT_PATH=$(realpath "$0")
EXAMPLE_DIR=$(dirname $SCRIPT_PATH)
PROJECT_DIR=$(dirname $(dirname $EXAMPLE_DIR))
JAR="$PROJECT_DIR/target/table2qb.jar"
TABLE2QB="java -jar $JAR"

BASE_URI="https://id.milieuinfo.be/"

$TABLE2QB exec codelist-pipeline \
--codelist-csv $EXAMPLE_DIR/csv/substanties.csv \
--codelist-name "substanties (IMJV)" \
--codelist-slug "substantie" \
--base-uri $BASE_URI \
--uri-templates $EXAMPLE_DIR/templates/codelists.edn \
--output-file $EXAMPLE_DIR/ttl/substanties.ttl
#--output-directory "csvw"

$TABLE2QB exec components-pipeline \
--input-csv $EXAMPLE_DIR/csv/components.csv \
--base-uri $BASE_URI \
--uri-templates $EXAMPLE_DIR/templates/components.edn \
--output-file $EXAMPLE_DIR/ttl/components.ttl

$TABLE2QB exec cube-pipeline \
--input-csv $EXAMPLE_DIR/csv/observations.csv \
--dataset-name "kubus luchtemissies" \
--dataset-slug "luchtemisses" \
--column-config $EXAMPLE_DIR/columns.csv \
--base-uri $BASE_URI \
--uri-templates $EXAMPLE_DIR/templates/cube.edn \
--output-file $EXAMPLE_DIR/ttl/cube.ttl
