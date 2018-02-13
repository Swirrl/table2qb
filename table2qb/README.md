# table2cube

This project transforms tables of observations and reference data into [rdf data cube](https://www.w3.org/TR/vocab-data-cube/) resources specified as [csvw](https://github.com/w3c/csvw).

The input tables should be arranged as [tidy-data](http://vita.had.co.nz/papers/tidy-data.pdf) e.g. one row per observation, one column per component (i.e. dimension, attribute or measure). The output is a set of csvw documents - i.e. csv with json-ld metadata - that can be translated into RDF via a [csv2rdf](http://www.w3.org/TR/csv2rdf/) processor.

The outputs that make up the cube are:

- `observations.csv`: this goes through some transformations to standardise the cell values from arbitrary strings to slugs or other notations that are ready to be combined into URIs
- `component-specifications.csv`: this is a normalisation of the observations table that has one row per component
- `dataset.json`: the `qb:DataSet`
- `data-structure-definition.json`: the `qb:DataStructureDefinition`
- `component-specifications.json`: the set of `qb:ComponentSpecification`s (one per dimension, attribute and measure in the input)
- `observations.json`: the set of `qb:Observation`s (one per row in the input)

We also provide a set of `skos:ConceptScheme`s enumerating all of the codes used in each of the componentss (via `used-codes-scheme.json` and `used-codes-concepts.json`). These are useful for navigating within a cube by using the marginals - in other words this saves you from having to scan through all of the observations in order to establish the extent of the cube.

The project also provides pipelines for preparing reference data. These can be used for managing reference data across multiple `qb:DataSet`s.

- Components: given a tidy-data input of one component per row, this pipeline creates a `components.csv` file and a `components.json` for creating `qb:ComponentProperty`s in an `owl:Ontology`. Note that components are the dimensions, attributes and measures themselves whereas the component-specifications are what links these to a given data-structure-definition.
- Codelists: given a tidy-data input of one code per row, this pipeline creates a `codelist.csv` file and a `codelist.json` for creating `skos:Concepts` in an `skos:ConceptScheme`. Note that these codelists describe the universal set of codes that may be the object of a component (making it a `qb:CodedProperty`) not the (sub)set that have been used within a cube.


## Usage

TODO

## License

Copyright © 2018 Swirrl IT Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
