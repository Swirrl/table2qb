# table2qb

[![Build Status](https://travis-ci.com/Swirrl/table2qb.svg?branch=master)](https://travis-ci.com/Swirrl/table2qb)

![animated tesseract](https://upload.wikimedia.org/wikipedia/commons/thumb/d/df/Tesseract-1K.gif/240px-Tesseract-1K.gif)

## Overview

Table2qb (pronounced 'table to cube') is designed for representation of statistical data as Linked Data, using the [RDF Data Cube Vocabulary](https://www.w3.org/TR/vocab-data-cube/). It is aimed at users who understand statistical data and are comfortable with common data processing tools, but it does not require programming skills or detailed knowledge of RDF.

Choices of predicates and URI design are encapsulated in a configuration file that can be prepared once and used for all datasets in a collection.

Table2qb separates out the three main aspects of data in a data-cube structure: the observations, components (dimensions, measures, attributes) and the codelists that define possible values of the components.

The input to table2qb takes the form of a CSV file in the ['tidy data'](http://vita.had.co.nz/papers/tidy-data.pdf) structure.  The interpretation of the columns in the input data is defined by the configuration file and follows set conventions, described in more detail below.

The implementation makes use of the W3C [Tabular Data and Metadata on the Web](https://www.w3.org/TR/tabular-data-model/) standards, in particular [csv2rdf](https://www.w3.org/TR/csv2rdf/).  It incorporates this open source [csv2rdf processing library](https://github.com/swirrl/csv2rdf).  Behind the scenes there is a two step process, first converting the defined tabular inputs into an updated table and accompanying JSON metadata as defined by the csv2rdf standard, then using the standard csv2rdf processor to create the final outputs. A future version of table2qb will allow the option of outputting the JSON metadata and accompanying table for the csv2rdf representation.

The output of table2qb is RDF in [Turtle](https://www.w3.org/TR/turtle/) format. Future versions will allow alternative RDF serialisations as an option.

## How to install table2qb

### Github release

Download the release from [https://github.com/Swirrl/table2qb/releases](https://github.com/Swirrl/table2qb/releases). 

Currently the latest is 0.3.0.

Once downloaded, unzip.  The main 'table2qb' executable is in the directory `./target/table2qb-0.3.0` You can add this directory to your `PATH` environment variable, or just run it with the full file path on your system.

To get help on the available commands, type `table2qb help`.

To see the available pipelines (described in more detail below), type `table2qb list`.

To see the required command structure for one of the pipelines (for example the cube-pipeline), type `table2qb describe cube-pipeline`

### Clojure CLI

Clojure now distributes `clojure` and `cli` command-line programs for running clojure programs. To run `table2qb` through the `clojure` command, first
[install the Clojure CLI tools](https://clojure.org/guides/getting_started). Then create a file `deps.edn` containing the following:   

**deps.edn**
```clojure
{:deps {swirrl/table2qb {:git/url "https://github.com/Swirrl/table2qb.git" :tag "0.3.1"}
        org.apache.logging.log4j/log4j-api {:mvn/version "2.11.0"}
        org.apache.logging.log4j/log4j-core {:mvn/version "2.11.0"}
        org.apache.logging.log4j/log4j-slf4j-impl {:mvn/version "2.11.0"}}
 :aliases
 {:table2qb
  {:main-opts ["-m" "table2qb.main"]}}}
```

You can then run `table2qb` using

    clojure -A:table2qb
    
More details about the `clojure` CLI and the format of the `deps.edn` file can be found [on the Clojure website](https://clojure.org/reference/deps_and_cli)

## Compiling table2qb

Table2qb is written in Clojure and can be built using [Leiningen](https://leiningen.org/). It is recommended you use [Java 8](https://www.oracle.com/technetwork/java/javase/overview/java8-2100321.html) or later.

`table2qb` can be run through `leiningen` with `lein run` e.g.

    lein run list
    
Alternatively it can be run from an uberjar built with `lein uberjar`. The resulting .jar file in the `target` directory can then be run:

    java -jar target/table2qb.jar list

## How to run table2qb

See [using table2qb](doc/usage.md) for documentation on how to generate RDF data cubes using `table2qb`.

## Example

The [./examples/employment](./examples/employment) directory provides an example of creating a data cube from scratch with `table2qb`.

## License

Copyright © 2018 Swirrl IT Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.

## Acknowledgements

The development of table2qb was funded by Swirrl, by the UK Office for National Statistics and by the European Union’s Horizon 2020 research and innovation programme under grant agreement No 693849 (the [OpenGovIntelligence](http://opengovintelligence.eu) project).
