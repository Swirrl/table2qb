 # Regional Trade Example

This example is executed by the project's test suite. Be careful not to replace the files by running the pipeline (or the tests will always pass!).

The [csv](csv) folder contains the inputs:

- reference data:
  - components: [components.csv](./csv/components.csv)
  - codelists: [flow-directions.csv](./csv/flow-directions.csv),  [sitc-sections.csv](./csv/sitc-sections.csv), and [units.csv](./csv/units.csv)
- observation data:
  - [input.csv](./csv/input.csv)

This is premised on configuration in [/resources/columns.csv](/resources/columns.csv). This will need changing to support further examples. It should ultimately be extracted from a database so that adding components makes them available as columns that can be provided in observation csv.

The [csvw](./csvw) folder contains the outputs from table2qb and the [ttl](./ttl) folder shows the resulting RDF translation. The [vocabularies](./vocabularies) folder provides additional vocabularies required to make the example work.


## Running the example

You can serialise the example with e.g. `(table2qb.core/serialise-demo "/path/to/output/dir")`. Look at the body for that method to call the `components-pipeline`, `codelist-pipeline` and `cube-pipeline`. If you're interested in seeing the intermediate csvw results then look at `components->csvw`, `codelist->csvw` and `cube->csvw`.

