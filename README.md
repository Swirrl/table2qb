# table2qb

![animated tesseract](https://upload.wikimedia.org/wikipedia/commons/thumb/d/df/Tesseract-1K.gif/240px-Tesseract-1K.gif)

## Overview

Table2qb (pronounced 'table to cube') is designed for representation of statistical data as Linked Data, using the [RDF Data Cube Vocabulary](https://www.w3.org/TR/vocab-data-cube/). It is aimed at users who understand statistical data and are comfortable with common data processing tools, but it does not require programming skills or detailed knowledge of RDF.

Choices of predicates and URI design are encapsulated in a configuration file that can be prepared once and used for all datasets in a collection.

Table2qb separates out the three main aspects of data in a data-cube structure: the observations, components (dimensions, measures, attributes) and the codelists that define possible values of the components.

The input to table2qb takes the form of a CSV file in the ['tidy data'](http://vita.had.co.nz/papers/tidy-data.pdf) structure.  The interpretation of the columns in the input data is defined by the configuration file and follows set conventions, described in more detail below.

The implementation makes use of the W3C [Tabular Data and Metadata on the Web](https://www.w3.org/TR/tabular-data-model/) standards, in particular [CSV2RDF](https://www.w3.org/TR/csv2rdf/).  It incorporates this open source [CSV2RDF processing library](https://github.com/swirrl/csv2rdf).  Behind the scenes there is a two step process, first converting the defined tabular inputs into an updated table and accompanying JSON metadata as defined by the CSV2RDF standard, then using the standard CSV2RDF processor to create the final outputs.  The generated RDF includes links from observations to table rows, as per that standard.  A future version of table2qb will allow the option of outputting the JSON metadata and accompanying table for the CSV2RDF representation.

The output of table2qb is RDF in [Turtle](https://www.w3.org/TR/turtle/) format. Future versions will allow alternative RDF serialisations as an option.

## How to run table2qb

Table2qb has three main options, each with its own data transformation 'pipeline' to create the different aspects of a data cube: the components (dimensions, measures, attributes), the codelists (defined possible values of components) and the observations themselves. 

Components and codelists can often be re-used by many data cubes, so if those pipelines have been run previously it may not always be necessary to run them again, but to create a new data cube from scratch will require all three pipelines to be run, and possibly the codelist pipeline to be run once for each dimension.

In order to execute the full table2qb process, the following 3 pipelines should be run (in no particular order):

- `components-pipeline`
- `codelist-pipeline` (run for each codelist CSV input file)
- `cube-pipeline`

After cloning or downloading the repository, run the following commands from the root folder (or add an appropriate entry to your _PATH_ environment variable).

_UPDATE THIS ONCE WE'VE DONE ISSUES [47](https://github.com/Swirrl/table2qb/issues/47) and [45](https://github.com/Swirrl/table2qb/issues/45)_.


__To run the `components-pipeline` use the following command:__

```BASE_URI=your_domain java -jar target/table2qb-0.1.3-SNAPSHOT-standalone.jar exec components-pipeline --input-csv input_file --column-config config_file --output-file output_file```



__To run the `codelist-pipeline` for each of the codelist files use the following command:__

```BASE_URI=your_domain java -jar target/table2qb-0.1.3-SNAPSHOT-standalone.jar exec codelist-pipeline --codelist-csv codelist_file --codelist-name codelist_name --codelist-slug codelist_slug --column-config config_file --output-file output_file```



__To run the `cube-pipeline` use the following command:__

```BASE_URI=your_domain java -jar target/table2qb-0.1.3-SNAPSHOT-standalone.jar exec cube-pipeline --input-csv input_file --dataset-name dataset_name --dataset-slug dataset_slug --column-config config_file --output-file output_file```

### base_uri

Defines the domain and any other URI sections that will be prefixed to all generated URIs in the output.


### input_file

Input CSV file of the correct structure - the contents must correspond to the choice of pipeline. More details on the required structure are provided below.  See also the [worked example](./examples/employment/README.md).

- `components-pipeline` uses the CSV defining the components (e.g. `components.csv`) as input_file
- `codelist-pipeline` uses individual codelist CSV files as input_files
- `cube-pipeline` uses the input observation data file ( e.g. `input.csv`) as input_file

The config_file (described below) is used to determine how the data in the input_file is interpreted.

### config_file

config_file (e.g. `columns.csv`) defines the mapping between a column name and the relevant component and sets out any preparatory transformations and URI templates.  This parameter is only needed for the cube-pipeline.

### output_file

The output of the process: a single file as RDF in Turtle format.

### codelist_file

Input CSV codelist file.  This parameter is used only with the codelist-pipeline.

### codelist_name

Name of the output codelist file in Turtle format. The value of codelist_name is used as the value of the `dcterms:title` property of the created codelist.  This parameter is used only with the codelist-pipeline.

### codelist_slug

[Slug](http://patterns.dataincubator.org/book/url-slug.html) of the output codelist file. The slug string provided will appear in a particular position in the generated URLs - e.g. a URL containing the `codelist_slug` "gender" might look like this: <http://statistics.gov.scot/def/concept/gender/female> .  This parameter is used only with the codelist-pipeline.

### dataset_name

Name of the output file in Turtle format. The value of dataset_name is used as the value of the `dcterms:title` property of the created dataset.  This parameter is used only with the cube-pipeline.

### dataset_slug

[Slug](http://patterns.dataincubator.org/book/url-slug.html) of the output file. The slug string provided will appear in a particular position in the generated URLs - e.g. a URL containing the `dataset_slug` "employment" might look like this: <http://statistics.gov.scot/data/employment/S12000039/2017-Q1/Female/count/people> . This parameter is used only with the cube-pipeline.


### Observation Data

The observation input table should be arranged as [tidy-data](http://vita.had.co.nz/papers/tidy-data.pdf) e.g. one row per observation, one column per component (i.e. dimension, attribute or measure). This should be done prior to running table2qb using your choice of tool/software.

The column headers and column contents in the observation input file correspond to the components listed in the column configuration file (e.g. `columns.csv`). This means that column headers in the input CSV file (e.g. `input.csv`) should match the values under the "Title" column in the column configuration file (e.g. `columns.csv`).


### Definition of components

As mentioned above, components are the dimensions, attributes and measures present in the input observation data. The CSV components file (e.g. `components.csv`) should contain a list of those components from the input file that you want to define vocabularies for. If there are external vocabularies that are reused for certain components, then they don't need to be listed here.

The CSV file should have the following columns:

- `Label` - a human readable label that corresponds to the column heading in the input observation data file, and to the `Title` of the component in the configuration file.
- `Description` - a human readable description of the component
- `Component Type` - what type of component this is - i.e. a Measure, Dimension or Attribute
- `Codelist` - for Dimension components only, a URI for the codelist of all possible values for this dimension.  

In addition to the list of components, this file should also contain the unique values of the `Measure Type` dimension. For example, if the `Measure Type` dimension contains values of "Count" and "Net Mass", then both of them should be added to the components list.


### Definition of codelists

Codelists describe the universal set of codes that may be the object of a component, not just the (sub)set that has been used within a cube. In other words, all the possible codes for a given codelist should be listed in the input CSV file for each codelist.

Each codelist CSV file should have the following columns:

- `Label` - a code from the codelist, there should be one row per code
- `Notation` - every item in the codelist must have a different value for notation.  It will be used in the creation of the URI for this item, so should only contain URI-compatible characters (no spaces).  A common option is to make a slug of the label,  e.g. `3 Mineral Fuels` becomes `3-mineral-fuels`, or to use a pre-existing set of alpha-numeric codes.
- `Parent Notation` - optional: to support hierarchical codelists, if this item has a skos:broader relationship to one of the other items in the codelist, then include the notation of the parent item in this column. 

## Configuration

The table2qb pipeline is configured with a CSV file (e.g. `columns.csv`) describing the components it can expect to find in the observation data file. The location of this file is specified with the `--column-config` parameter.

This CSV file should have the following columns:

- `title` - a human readable title for a component, that is used as a column header in the observations input file. Each row of the configuration file (and hence each column of an observations file) must have a different title.
- `name` - a machine-readable identifier used in uri templates. Each row of the configuration file (and hence each column of an observations file) must have a different title.
- `component_attachment` - how the component defined by this row of the configuration file should be attached to the Data Structure Definition (i.e. one of `qb:dimension`, `qb:attribute`, `qb:measure` or nil if the configuration file row is defining the value of the observation).
- `property_template` - the predicate used to attach the (cell) values to the observations.  This can either be a full URI, or it can be a URI template, where the `name` of any item in the configuration file can be inserted inside {} to indicate which column of the pipeline input file should be used to provide a value.
- `value_template` - the URI template applied to the cell values. The argument inside the {} should match the corresponding value of the `name` column.
- `datatype` - how the cell value should be parsed (typically `string` for everything except the value column which will be `number`)
- `value-transformation` - possible values here are: `slugize`, `unitize` or blank. `slugize` converts column values into URI components, e.g. `(slugize "Some column value")` is `some-column-value`. `unitize` works similarly to `slugize` and in addition also translates literal `£` into `GBP`, e.g. `(unitize "£ 100")` is `gpb-100`. Other unit-related special cases will be added to `unitize` in future.

The configuration file should contain one row for each dimension, measure and attribute used in any input file. 

## Example

The [./examples/employment](./examples/employment) directory provides a full example and instructions for running it.

## How to compile table2qb

The repository includes a compiled Java jar file, so no compilation is necessary to use table2qb.

If you want to edit your copy of the code, you will need to recompile before use.  Table2qb is written in Clojure, and so [Leiningen](https://leiningen.org/) should be installed in order to compile the code.

The following version of Java is recommended: [Java 8](https://www.oracle.com/technetwork/java/javase/overview/java8-2100321.html)

To create a new jar file, run the following command in the root folder of the project: `lein uberjar`.
The jar file is created in `./target`.

## License

Copyright © 2018 Swirrl IT Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
