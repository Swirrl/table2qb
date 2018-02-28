# table2cube

This project transforms tables of observations and reference data into [rdf data cube](https://www.w3.org/TR/vocab-data-cube/) resources specified as [csvw](https://github.com/w3c/csvw).

## The pipeline

### Observation Data

The observation input table should be arranged as [tidy-data](http://vita.had.co.nz/papers/tidy-data.pdf) e.g. one row per observation, one column per component (i.e. dimension, attribute or measure). The output is a set of csvw documents - i.e. csv with json-ld metadata - that can be translated into RDF via a [csv2rdf](http://www.w3.org/TR/csv2rdf/) processor. The outputs that make up the cube are:

- `observations.csv`: this goes through some transformations to standardise the cell values from arbitrary strings to slugs or other notations that are ready to be combined into URIs
- `component-specifications.csv`: this is a normalisation of the observations table that has one row per component
- `dataset.json`: the `qb:DataSet`
- `data-structure-definition.json`: the `qb:DataStructureDefinition`
- `component-specifications.json`: the set of `qb:ComponentSpecification`s (one per dimension, attribute and measure in the input)
- `observations.json`: the set of `qb:Observation`s (one per row in the input)

We also provide a set of `skos:ConceptScheme`s enumerating all of the codes used in each of the componentss (via `used-codes-scheme.json` and `used-codes-concepts.json`). These are useful for navigating within a cube by using the marginals - in other words this saves you from having to scan through all of the observations in order to establish the extent of the cube.

### Reference Data

The project provides pipelines for preparing reference data. These can be used for managing reference data across multiple `qb:DataSet`s.

- Components: given a tidy-data input of one component per row, this pipeline creates a `components.csv` file and a `components.json` for creating `qb:ComponentProperty`s in an `owl:Ontology`. Note that components are the dimensions, attributes and measures themselves whereas the component-specifications are what links these to a given data-structure-definition.
- Codelists: given a tidy-data input of one code per row, this pipeline creates a `codelist.csv` file and a `codelist.json` for creating `skos:Concepts` in an `skos:ConceptScheme`. Note that these codelists describe the universal set of codes that may be the object of a component (making it a `qb:CodedProperty`) not the (sub)set that have been used within a cube.

## Configuration

The table2qb pipeline is configured with a dataset describing the columns it can expect to find in the input csv files. We'll currently provide this as a [columns.csv](./resources/columns.csv) file although we may later store/ retreive this from the database.

The dataset should have the following columns:

- `title` - a human readable title (like csvw:title) that will be provided in the (first) header row of the input
- `name` - a machine-readable identifier (like csvw:name) used in uri templates
- `component_attachment` - how the component in the column should be attached to the Data Structure Definition (i.e. one of `qb:dimension`, `qb:attribute`, `qb:measure` or nil)
- `property_template` - the predicate used to attach the (cell) values to the observations
- `value_template` - the URI template applied to the cell values
- `datatype` - as per csvw:datatype, how the cell value should be parsed (typically `string` for everything except the value column which will be `number`)

This initial draft also includes several conventions in the code that ought to be generalised to configuration - particularly how cell values are slugged.


## Example

The [./test/resources/](./test/resources) directory provides examples of the above inputs:

- reference data:
  - components: [components.csv](./test/resources/trade-example/components.csv)
  - codelists: [flow-directions.csv](./test/resources/trade-example/flow-directions.csv),  [sitc-sections.csv](./test/resources/trade-example/sitc-sections.csv), and [units.csv](./test/resources/trade-example/units.csv)
- observation data:
  - [input.csv](./test/resources/trade-example/input.csv)

It is also premised on configuration in [./resources/columns.csv](./resources/columns.csv). This will need changing to support further examples. It should ultimately be extracted from the database such that adding components makes them available as columns that can be provided in observation csv.

You can get the demo working from the repl:

```
$ lein repl

(require 'table2qb.code)
(ns table2qb core)
```

To serialise everything to a tmp dir call `(serialise-demo)`. Alternatively you can go through the pieces one-at-a-time...

Build components ontology:

```clojure
(components-pipeline
 "./test/resources/trade-example/components.csv"
 "./tmp")
 ;; => components.csv,components.json
```

Build codelists:

```clojure
(codelist-pipeline
 "./test/resources/trade-example/flow-directions.csv"
 "./tmp" "Flow Directions" "flow-directions")
 ;; => flow-directions.csv,flow-directions.json
 
(codelist-pipeline
 "./test/resources/trade-example/sitc-sections.csv"
 "./tmp" "SITC Sections" "sitc-sections")
 ;; => sitc-sections.csv, sitc-sections.json

(codelist-pipeline
 "./test/resources/trade-example/units.csv"
 "./tmp" "Measurement Units" "measurement-units")
 ;; => measurement-units.csv, measurement-units.json
```

Buid the cube itself:

```clojure
(data-pipeline
 "./test/resources/trade-example/input.csv"
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
(csv2rdf-qb "./tmp") ;;=> dataset.ttl, data-structure-definition.ttl, component-specifications.ttl, observations.ttl, used-codes-codelists.ttl, used-codes-codes.ttl
```

You'll also need the rdf-cube, sdmx, time and uk geo reference vocabularies ([from here](../examples/ons-trade/metadata/)) to make this work.


## License

Copyright © 2018 Swirrl IT Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
