#!/bin/bash
set -e

PROJECT_DIR=$(dirname $(dirname "$0"))
JAR="$PROJECT_DIR/target/table2qb.jar"
TABLE2QB="java -jar $JAR"
#TABLE2QB="lein run"

BASE_URI="http://gss-data.org.uk/"

EXAMPLE_DIR="$PROJECT_DIR/examples/regional-trade"
CSV_DIR="$EXAMPLE_DIR/csv"
CSVW_DIR="$EXAMPLE_DIR/csvw"

rm -f "$CSVW_DIR/*"
$TABLE2QB csvw components-pipeline \
	  --input-csv "$CSV_DIR/components.csv" \
	  --base-uri $BASE_URI \
	  --output-directory "$CSVW_DIR"
$TABLE2QB csvw codelist-pipeline \
	  --codelist-csv "$CSV_DIR/flow-directions.csv" \
	  --codelist-name "Flow Directions Codelist" \
	  --codelist-slug "flow-directions" \
	  --base-uri $BASE_URI \
	  --output-directory "$CSVW_DIR"
$TABLE2QB csvw codelist-pipeline \
	  --codelist-csv "$CSV_DIR/sitc-sections.csv" \
	  --codelist-name "SITC Sections Codelist" \
	  --codelist-slug "sitc-sections" \
	  --base-uri $BASE_URI \
	  --output-directory "$CSVW_DIR"
$TABLE2QB csvw codelist-pipeline \
	  --codelist-csv "$CSV_DIR/units.csv" \
	  --codelist-name "Units" \
	  --codelist-slug "units" \
	  --base-uri $BASE_URI \
	  --output-directory "$CSVW_DIR"
$TABLE2QB csvw cube-pipeline \
	  --column-config "$EXAMPLE_DIR/columns.csv" \
	  --input-csv "$CSV_DIR/input.csv" \
	  --dataset-name "Regional Trade" \
	  --dataset-slug "regional-trade" \
	  --base-uri $BASE_URI \
	  --output-directory "$CSVW_DIR"

