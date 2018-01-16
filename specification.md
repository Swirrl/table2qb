# table2qb Specification

This document provides a brief overview of the problem/ proposed solution, then goes into more detail about the challenges and ends with a set of examples.

## Overview

### Requirements

The primary aim is to provide a specification for preparing hypercubes as tables that may be converted into RDF.

Meeting the specification should be as simple as possible, the more common scenarios being covered by convention, with support for exceptional cases coming through additional configuration.

The basic structure is the [tidy data format](http://vita.had.co.nz/papers/tidy-data.pdf). Each row is one observation, and each column a variable.

The most typical case is that a dataset of observations will be defined along with a reference area, date, measurement type, unit of measure, and zero or more additional dimensions.

More advanced cases allow for:

- dimensions using pre-existing properties (e.g. sdmx-dim:gender)
- codes using pre-existing codelists (to allow code-list reuse/ disambiguation etc)
- datasets to be split across files (to allow partial uploads)

### Proposed Architecture

One possible solution is to distinguish two modes:

Strict mode - expects each column to have a property and code values already as slugs (i.e. CURIE suffixes)
Relaxed mode - expects strings, uses lookups and heuristics to get code values into slugs and to identify properties etc

We can then create two pipelines, a standalone strict pipeline and a relaxed one that prepares data then feeds it into the strict pipeline. In effect one acts as a tolerant reader/ anti-corruption layer for the other and includes all of the simplifying conventions etc.

Strict mode takes a table and a json-ld configuration. This configuration specifies, for each column:

- header: a string used to identify the column in the input regardless of order (JSON-LD doesn't preserve order by default anyway)
- component: the component property e.g. sdmx-dim:refArea
- component-type: could be infered from the vocab, but specifying it here would allow us to remove that dependency i.e. qb:dimension, qb:measure, or qb:dimension
- value-prefix: if provided, applies to all values in the column, otherwise (i.e. if you want to use different prefixes within a column) the values should already include prefixes
- value: if provided, applies a default value for that component to every observation

The configuration might also include things like:

- dataset-uri: where this already exists (i.e. for updates)
- dataset-slug: used to namespace the qb entities to avoid collision (usually taken by looking-up the name from the database)

Relaxed mode just requires a table. It prepares the table and correspondig configuration for strict mode.

- uses column labels to identify known properties (e.g. "Geography" => sdmx:refArea)
- uses column labels to identify whether these components are dimensions, measures or attributes
- uses column labels to identify known string->identifier converters for rows values (i.e. date heuristics, lookups etc)
- converts row values to identifiers, based on the above (e.g. slugify, "Female" => sdmx-code:sex-F)

We might also want relaxed mode to allow values that are already codes to pass through unchanged. This would allow a fuzzier boundary between client-specific preparations and relaxed-mode.

Relaxed mode may also need to generate associated data (i.e. codelists/ vocabularies etc) although this might ultimately be better handled by another process for managing reference data:

- creates vocabularies e.g. for new dimensions
- creates concept schemes e.g. for new dimension-values
- creates arbitrary reference data e.g. time intervals

There are a couple of [examples](./examples) of relaxed-input.csv which would lead to strict-input.csv and strict-input.json.

## Challenges

### Strings as Identifiers - how to derive URIs

Ideally the input data would only be URIs. There isn't a URI available for every datum people wish to publish. Indeed the pipeline generally has to create these.

We've tended to follow the [Tolerant Reader pattern](https://www.martinfowler.com/bliki/TolerantReader.html) in our pipelines. This means we've allowed:

- 1. known codes - e.g. Area specified as GSS code is converted to a URI in the statistics.gov.uk domain
- 2. semi-standardised Strings - e.g. Time is converted to a URI with some heuristics that try to disambiguate e.g. 2007/8 into 2007M08 and the year span 2007-2008, the dimension "Age" is converted into sdmx-dim:age
- 3. open-text strings - i.e. Most other dimensions and values are turned into URIs by slugging (i.e. removing non-URL compatible elements)

This presents several problems:

- 1a. if an unknown code is given to a coded-column (e.g. a NUTS code instead of a GSS code) we must detect that and process it differently (the URI scheme is different) - ultimately the problem is that these codes may are not unique as a URL would be
- 1b. if a non-conformant string is given to a semi-standardised column (e.g. 2007/8 should be interpreted as _government year_ 2007-04-01 - 2008-03-31)
- 2. if a string shouldn't be interpreted as it's standard (e.g. an "age of asset" dimension shouldn't be converted to sdmx-dim:age)
- 3a. if open-text strings are misspelled
- 3b. if there's a collision (either within strings or slugs)

Possible solutions include:

- 1a. namespacing all codes (including geography and time).
- 1b. (require codes or) require stricter standardisation (i.e. a slightly less tolerant reader) - e.g. using ISO date format.
- 2. require metadata specifying, for each column in the csv, a component property (i.e. typically the dimension property but might also includes attribute and measure properties). This could be optional (with a defaults/ interpretation heuristics as now).
- 3a. (require codes or) only accept known strings or provide warnings (e.g. "this upload requires that we created a new age band, are you sure you meant to do that?")
- 3b. namespacing would help to avoid collisions

If we do require codes, there would need to be some other means of defining the corresponding labels.

### Run context - import vs update, with vs without pmd

We generally see two scenarios:

- import: we're given a set of spreadsheets and need to organise it and generate cubes, this tends to happen outside of pmd
- update: a set of qb:DataSets exists in a pmd instance and we need to add new observations (e.g. data for the latest period)

In the import case, we generate the dataset-uri (and slugs for the URIs of all the included qb entities) from a dataset name. Generally the validation is quite loose as what is provided is what should be inserted.

In the update case, we already have a dataset-uri that should be used, in order to namespace entity URIs we require a slug (which we have so far found by querying the dataset name). New data must be validated against existing data.

Whereas the import case may be standalone. The update case requires that we query a database to configure the pipeline.

Ideally we would be able to support both contexts with a minimum of duplication/ complexity.

### Using third party vocabularies and codelists

We can, of course, create new RDF for all of the uploaded content. It's preferable to re-use vocabularies where possible, for instance recognising that a "Gender" dimension is already available as smdx-dim:gender.

Even more desirable, will be a means of adopting existing reference data, so the pipeline can use codelists to validate or conform the data. Conformance might require a lookup table from source identifier to canonical identifier (e.g. "Female" => sdmx-code:sex-F), or it might simply be an abitrary function (e.g. time interval heuristics).


## Compatibility with/ Re-use of existing projects

### Scottish Government - sns-graft

The upload format we've previously given SG, is basically the relaxed-mode variant. Relaxed mode input as per [relaxed-input.csv](./examples/age-gender/relaxed-input.csv). This would prepare some inputs for strict mode as per [strict-input.csv](./examples/age-gender/strict-input.csv) and [strict-input.json](./examples/age-gender/strict-input.json).

We'll want to extract some of the logic/ heuristics out from sns-graft into the relaxed pipeline.

The sns-graft project also offers some additional functionality - aggregation from small geographies to larger ones and the calculation of ratio data. These features could be implemented as a preliminary step, creating another input for the relaxed pipeline (this is basically what happens underneath).

If we adopted the same approach for ratio data the that provenance information could be lost (e.g. cube:hasDenominator etc). Instead we could provide e.g. qb:attributes (we would need to revise the pmd-cube ontology accordingly as these are currently just plain old rdf:Property).

### ONS - ons-graft

We ought to extract things out of here (rather than using sns-graft as a starting point).

The project has a namespace per entity in the cube. This includes most of the qb types (DataSet, DataStructureDefinition, ComponentSpecification, Observation, MeasureProperty) and there's stuff for reference times, statistical geographies, measurement units and wiring for the way we build custom dimensions etc.

It has pipelines which call their dependencies (e.g. DSD pipeline builds comp-specs). In order to avoid circular depenencies between namespaces there's a cube-common namespace that includes URIs that are re-used.

It includes a bunch of data-baker specific stuff that ought to be separated-out.

### ONS wda/ data-baker formats

#### data-baker v2

Data-baker has one observation per row, which is great. It uses two columns per variable though - dimension and code, which is problematic as it makes it possible to have 2 dimensions in the same column. If we process the whole table into one cube, it will contain observations with different dimensions producing an invalid cube.

This isn't consequent (we have validations to prevent it) but it would be better if it wasn't possible. Furthermore, once you enforce that a given column may only contain one value, then it becomes redundant (or at least very inefficient).

The format we use with scottish government instead puts the dimension label in the column heading. We have a fixed number of known dimensions in the leftmost columns with a variable number of extra dimensions to the right.

These known dimensions do restrict us to cubes that have refArea, refPeriod, measureType and unitMeasure properties. The v3 data-baker format, by contrast, appears to treat all dimensions as optional which is more powerful.

A pipeline which takes data-baker v2 inputs and produces an input for relaxed-mode would:

- scan dimensions to ensure there's only one per column
- replace the dimension-value column-headers with the (single) value from the dimension column
- either convert column headers into canonical form or build the appropriate heuristic into the relaxed (e.g. disambiguat "Area", "Geography", "GeographyCode", "geographic_area" etc)

#### data-baker v3

Adds namespaces per dimension. Generally speaking this is a good improvement (better than heuristics). It certainly looks like it's going to be necessary on geography and time.

The new proposed (v3) format includes, for each dimension/value column, an additional column to describe the registry for the value.

In theory this is equivalent to splitting the identifier for the dimension's value into two columns (namespace and code). In itself that's not really a problem but it does require that application logic (i.e. not provided by the serialisation) ensures that the namespace and code are handled as one (e.g. atomic updates etc). Something like CURIEs might resolve that.

Furthermore it will be inefficient in the most common scenario where all codes in a column use the same namespace. In that scenario, column metadata would be more efficient. Column metadata would not, however, easily declare that multiple namespaces had been used in a column. Separate tables/ uploads would be required and some means to ensure consistency between them (e.g. dimensional consistency checks within cubes in statistics.gov.scot).

A pipeline which takes data-baker v3 inputs and produces an input for strict-mode would (like an enhanced version of relaxed-mode):

- convert the registry to a namespace prefix
- convert the code values to CURIEs accordingly
- identify components etc from the dimensions provided

It could of course, prepare results for relaxed mode, but that would likely involve unnecessary intermediate conversions.

### Measure modelling approaches

We've tended to standardise on the measures dimension approach. This section describes options for compatibility with alternative models in future.

Measure dimension approach - value column identified with `"qb:measure": null` (or `true` or something) means find the `"qb:dimension": "qb:measureType"` column to determine the measure.

Single measure approach - in relaxed mode, measure type is value column header (e.g. observation values in "Count" column instead of separate "Measurement"+"Value" columns), strict mode `"qb:measure": "measure:count"` is specified.

Multi measure approach - in relaxed mode, again uses column headers per measure (so demonstrating that value for each measure required for all columns), strict mode applies e.g. `"qb:measure": "measure:count"` to each.

### Import vs Update distinction

As noted above we need to support two run contexts.

Relaxed-mode pipeline, or client-specific preparatory ones, could either derive certain things (e.g. dataset-uri) from the data or from a query. The example shows how a URI and slug are provided in the strict-mode json.

Simiarly a client-specific import pipeline could create things that are normally provided by the pmd admin tool (dataset modified dates, folders etc).
