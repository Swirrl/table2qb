 # Regional Trade Example

This example is executed by the project's test suite. Be careful not to replace the files by running the pipeline (or the tests will always pass!).

The [./csv](csv) folder contains the inputs:

- reference data:
  - components: [components.csv](./csv/components.csv)
  - codelists: [flow-directions.csv](./examples/regional-trade/flow-directions.csv),  [sitc-sections.csv](./examples/regional-trade/sitc-sections.csv), and [units.csv](./examples/regional-trade/units.csv)
- observation data:
  - [input.csv](./examples/regional-trade/input.csv)

This is premised on configuration in [/resources/columns.csv](/resources/columns.csv). This will need changing to support further examples. It should ultimately be extracted from a database so that adding components makes them available as columns that can be provided in observation csv.

The [./csvw](csvw) folder contains the outputs from table2qb and the [./ttl](ttl) folder shows the resulting RDF translation. The [./vocab/](vocab) folder provides additional vocabularies required to make the example work.


## Running the example

You can get the demo working from the repl:

```shell
$ lein repl
```
```clojure
(require 'table2qb.code)
(ns table2qb.core)
```

To serialise everything to a tmp dir call `(serialise-demo)`. Alternatively you can go through the pieces one-at-a-time...

Build components ontology:

```clojure
(components-pipeline
 "./examples/regional-trade/components.csv"
 "./tmp")
 ;; => components.csv,components.json
```

Build codelists:

```clojure
(codelist-pipeline
 "./examples/regional-trade/flow-directions.csv"
 "./tmp" "Flow Directions" "flow-directions")
 ;; => flow-directions.csv,flow-directions.json
 
(codelist-pipeline
 "./examples/regional-trade/sitc-sections.csv"
 "./tmp" "SITC Sections" "sitc-sections")
 ;; => sitc-sections.csv, sitc-sections.json

(codelist-pipeline
 "./examples/regional-trade/units.csv"
 "./tmp" "Measurement Units" "measurement-units")
 ;; => measurement-units.csv, measurement-units.json
```

Buid the cube itself:

```clojure
(data-pipeline
 "./examples/regional-trade/input.csv"
 "./tmp" "Regional Trade" "regional-trade")
 ;; => component-specifications.csv, dataset.json, data-structure-definition.json, component-specifications.json
 ;; => observations.csv, observations.json, used-codes-codelists.json, used-codes-codes.json
```

Ultimately we'll translate this into linked-data using the [csv2rdf library](https://github.com/Swirrl/csv2rdf). For now there's some helper functions to call the RDF::Tabular csv2rdf translator using the `rdf` cli tool (you can get this with `gem install linkeddata`).

For the metadata (each should be loaded into PMD as a vocabulary):

```clojure
(csv2rdf "./tmp" "components") ;;=> components.ttl
(csv2rdf "./tmp" "flow-directions") ;;=> flow-directions.ttl
(csv2rdf "./tmp" "sitc-sections") ;;=> sitc-sections.ttl
(csv2rdf "./tmp" "measurement-units") ;;=> measurement-units.ttl
```

For the cube (each can be loaded into one PMD Dataset that covers the whole cube):

```clojure
(csv2rdf-qb "./tmp")
;;=> dataset.ttl, data-structure-definition.ttl, component-specifications.ttl
;;=> observations.ttl, used-codes-codelists.ttl, used-codes-codes.ttl
```

You'll also need some external vocabularies. [General ones](/examples/vocabularies/) like rdf-cube, sdmx, and csvw and some [specific to this dataset](./vocabularies) like reference time and UK statistical geography.
