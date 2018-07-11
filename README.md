# table2cube

![animated tesseract](https://upload.wikimedia.org/wikipedia/commons/thumb/d/df/Tesseract-1K.gif/240px-Tesseract-1K.gif)

## Overview

This project transforms tabular input files in CSV format into various RDF serializations such as [Turtle](https://www.w3.org/TR/turtle/) or [n-triples](https://www.w3.org/TR/n-triples/).
During the transformation the input files comprised of tables of observations and reference data get converted into [rdf data cube](https://www.w3.org/TR/vocab-data-cube/) resources specified as [csvw](https://github.com/w3c/csvw).

## How to run table2qb

_UPDATE THIS ONCE WE'VE DONE ISSUES [47](https://github.com/Swirrl/table2qb/issues/47) and [45](https://github.com/Swirrl/table2qb/issues/45)_.

```BASE_URI=your_domain java -jar target/table2qb-0.1.3-SNAPSHOT-standalone.jar exec pipeline --input-csv input_file --column-config config_file --output-file output_file```

### pipeline

This parameter must be one of: `cube-pipeline` | `components-pipeline` | `codelist-pipeline`


### input_file

Input CSV file of the correct structure - contents must correspond to the choice of pipeline, as outlined below:

- `components-pipeline` uses the CSV defining the components (e.g. `components.csv`) as input_file
- `codelist-pipeline` uses individual codelist CSV files as input_files
- `cube-pipeline` uses the input observation data file ( e.g. `input.csv`) as input_file

The config_file (described below) is used to determine how the data in the input_file is interpreted.

### config_file

config_file (e.g. `columns.csv`) defines the mapping between a column name and the relevant component and sets out any preparatory transformations and URI templates.

### output_file

The output of the process: a single file as RDF in Turtle format or another RDF serialization.


### Observation Data

The observation input table should be arranged as [tidy-data](http://vita.had.co.nz/papers/tidy-data.pdf) e.g. one row per observation, one column per component (i.e. dimension, attribute or measure). This should be done prior to running table2qb using your choice of tool/software.

### Definition of components

As mentioned above, components are the dimensions, attributes and measures present in the input observation data. The CSV components file (e.g. `components.csv`) should contain a list of those components from the input file that you want to define vocabularies for. If there are external vocabularies that are reused for certain components, then they don't need to be listed here.
In addition to the list of components, this file should also contain the unique values of the `Measure Type` dimension. For example, if the `Measure Type` dimension contains values of "Count" and "Net Mass", then both of them should be added to the components list.


### Definition of codelists

Codelists describe the universal set of codes that may be the object of a component, not just the (sub)set that has been used within a cube. In other words, all the possible codes for a given codelist should be listed in the input CSV file for each codelist.

## Configuration

The table2qb pipeline is configured with a CSV file (e.g. `columns.csv`) describing the components it can expect to find in the observation data file. The location of this file is specified with the `--column-config` parameter.

The CSV file should have the following columns:

- `title` - a human readable title that will be provided in the (first) header row of the input
- `name` - a machine-readable identifier used in uri templates
- `component_attachment` - how the component in the column should be attached to the Data Structure Definition (i.e. one of `qb:dimension`, `qb:attribute`, `qb:measure` or nil)
- `property_template` - the predicate used to attach the (cell) values to the observations
- `value_template` - the URI template applied to the cell values
- `datatype` - how the cell value should be parsed (typically `string` for everything except the value column which will be `number`)
- `value-transformation` - possible values here are: `slugize` and `unitize`. `slugize` converts column values into URI components, e.g. `(slugize "Some column value")` is `some-column-value`. `unitize` works similar to `slugize` and in addition also translates literal `£` into `GBP`, e.g. `(unitize "£ 100")` is `gpb-100`.

The configuration file should contain one row per component, as well as one row per each unique value of the `Measure Type` dimension (as above).

## Example

The [./examples/employment](./examples/employment) directory provides a full example and instructions for running it.

## How to compile table2qb

table2qb is written in Clojure, and so [Leiningen](https://leiningen.org/) should be installed to enable Clojure code to run.

The following version of Java is recommended: [Java 8](https://www.oracle.com/technetwork/java/javase/overview/java8-2100321.html)

## License

Copyright © 2018 Swirrl IT Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
