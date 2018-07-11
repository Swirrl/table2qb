#!/bin/bash
set -e

SCRIPT_DIR=$(dirname "$0")
JAR="$SCRIPT_DIR/target/table2qb.jar"
OUT_DIR="$SCRIPT_DIR/tmp"
CSV_DIR="$SCRIPT_DIR/examples/regional-trade/csv"
COLUMN_CONFIG="$SCRIPT_DIR/resources/columns.csv"
BASE_URI="http://gss-data.org.uk/"

# build uberjar if it does not exist
if [ ! -f $JAR ]; then
  pushd $SCRIPT_DIR
  lein uberjar
  popd
fi

if [ ! -d $OUT_DIR ]; then
  mkdir $OUT_DIR
fi

java -jar $JAR exec components-pipeline --input-csv "$CSV_DIR/components.csv" --base-uri $BASE_URI --output-file "$OUT_DIR/components.ttl"
java -jar $JAR exec codelist-pipeline --codelist-csv "$CSV_DIR/flow-directions.csv" --codelist-name "Flow Directions" --codelist-slug flow-directions --base-uri $BASE_URI --output-file "$OUT_DIR/flow-directions.ttl"
java -jar $JAR exec codelist-pipeline --codelist-csv "$CSV_DIR/sitc-sections.csv" --codelist-name "SITC Sections" --codelist-slug sitc-sections --base-uri $BASE_URI --output-file "$OUT_DIR/sitc-sections.ttl"
java -jar $JAR exec codelist-pipeline --codelist-csv "$CSV_DIR/units.csv" --codelist-name "Measurement Units" --codelist-slug measurement-units --base-uri $BASE_URI --output-file "$OUT_DIR/measurement-units.ttl"
java -jar $JAR exec cube-pipeline --input-csv "$CSV_DIR/input.csv" --dataset-name "Regional Trade" --dataset-slug regional-trace --column-config $COLUMN_CONFIG --base-uri $BASE_URI --output-file "$OUT_DIR/cube.ttl"

