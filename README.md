# table2qb <img src="https://upload.wikimedia.org/wikipedia/commons/thumb/d/df/Tesseract-1K.gif/240px-Tesseract-1K.gif" align="right" height="139" alt="tesseract animation"/>

[![Build Status](https://travis-ci.com/Swirrl/table2qb.svg?branch=master)](https://travis-ci.com/github/Swirrl/table2qb)

## Build Statistical Linked-Data with CSV-on-the-Web

Create statistical linked-data by deriving CSV-on-the-Web annotations for your data tables using the [RDF Data Cube Vocabulary](https://www.w3.org/TR/vocab-data-cube/).

Build up a knowledge graph from spreadsheets without advanced programming skills or RDF modelling knowledge.

Simply prepare CSV inputs according to the templates and `table2qb` will output standards-compliant CSVW or RDF.

Once you're happy with the results you can adjust the configuration to tailor the URI patterns to your heart's content.

## Turn Data Tables into Data Cubes 

Table2qb expects three types of CSV tables as input:

- observations: a ['tidy data'](http://vita.had.co.nz/papers/tidy-data.pdf) table with one statistic per row (what the standard calls an _observation_)
- components: another table defining the columns used to describe observations (what the standard calls _component properties_ such as _dimensions_, _measures_, and _attributes_)
- codelists: a further set of tables that enumerate and describe the values used in cells of the observation table (what the standard calls _codes_, grouped into _codelists_)

For example, [the ONS says](https://www.ons.gov.uk/peoplepopulationandcommunity/populationandmigration/populationestimates/articles/overviewoftheukpopulation/january2021) that:

> In mid-2019, the population of the UK reached an estimated 66.8 million

This is a single observation value (66.8 million) with two dimensions (date and place) which respectively have two code values (mid-2019 and UK), a single measure (population estimate), and implicitly an attribute for the unit (people).

The [regional-trade example](https://github.com/Swirrl/table2qb/tree/master/examples/regional-trade) goes into more depth. The [colour-coded spreadsheet](./all-colour-coded.ods) should help illustrate how the three types of table come together to describe a cube.

Each of these inputs is processed by it's own pipeline which will output [CSVW](https://w3c.github.io/csvw/metadata/) - i.e. a processed version of the CSV table along with a JSON metadata annotation which describes the translation into RDF. Optionally you can also ask `table2qb` to perform the translation outputting RDF directly that can be loaded into a graph database and queried with SPARQL.

Table2qb also relies on a fourth CSV table for configuration:

- columns: this describes how the observations table should be interpreted - i.e. which components and codelists should be used for each column in the observation tables

This configuration is designed to be used for multiple data cubes across a data collection (so that you can re-use e.g. a "Year" column without having to configure anew it each time) to encourage harmonisation and alignment of identifiers.

Ultimately `table2qb` provides a foundation to help you build a collection of interoperable statistical linked open data.

## Install table2qb

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
{:deps {swirrl/table2qb {:git/url "https://github.com/Swirrl/table2qb.git"
                         :sha "8c4b22778db0c160b06f2f3b0b3df064d8f8452b"}
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
