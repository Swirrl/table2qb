 # Scottish Government - Employment data example

This example aims to demonstrate how [table2qb](https://github.com/Swirrl/table2qb) can be used to convert a sample of the [Scottish government's employment data](http://statistics.gov.scot/resource?uri=http%3A%2F%2Fstatistics.gov.scot%2Fdata%2Femployment) from CSV to RDF.


## Directory overview

There are 2 folders in this directory: 

1. [csv](https://github.com/Swirrl/table2qb/tree/employment-example/examples/employment/csv) folder contains the inputs:

    1.1 reference data:
      - components: [components.csv](https://github.com/Swirrl/table2qb/tree/employment-example/examples/employment/csv/components.csv)
      - codelists: [gender.csv](https://github.com/Swirrl/table2qb/tree/employment-example/examples/employment/csv/gender.csv) and [units.csv](https://github.com/Swirrl/table2qb/tree/employment-example/examples/employment/csv/units.csv)

    1.2 observation data:
      - [input.csv](https://github.com/Swirrl/table2qb/tree/employment-example/examples/employment/csv/input.csv)

2. [ttl](https://github.com/Swirrl/table2qb/tree/employment-example/examples/employment/ttl) folder contains the outputs:

    2.1 reference data:
      - components: [components.ttl](https://github.com/Swirrl/table2qb/blob/employment-example/examples/employment/ttl/components.ttl)
      - codelists: [gender.ttl](https://github.com/Swirrl/table2qb/blob/employment-example/examples/employment/ttl/gender.ttl) and [measurement-units.ttl](https://github.com/Swirrl/table2qb/blob/employment-example/examples/employment/ttl/measurement-units.ttl)
     
     2.2 observation data:
      - [cube.ttl](https://github.com/Swirrl/table2qb/blob/employment-example/examples/employment/ttl/cube.ttl)


## Input file preparation

All the reference data files in the [csv](https://github.com/Swirrl/table2qb/tree/employment-example/examples/employment/csv) folder need to be manually created prior to running table2qb.

The below briefly outlines the preparation steps:

1. [components.csv](https://github.com/Swirrl/table2qb/tree/employment-example/examples/employment/csv/components.csv):

    - Should contain a list of those components (i.e. dimensions/attributes/measures) from the [input.csv](https://github.com/Swirrl/table2qb/tree/employment-example/examples/employment/csv/input.csv) file that you want to define vocabularies for. 
    - In addition to the list of components, this file should also contain the uniques value of the `Measure Type` dimension. For example, in this employment data example the `Measure Type` dimension only contains one value of `Count`, which should be added to the [components.csv](https://github.com/Swirrl/table2qb/tree/employment-example/examples/employment/csv/components.csv) file.
 
 2. codelists ([gender.csv](https://github.com/Swirrl/table2qb/tree/employment-example/examples/employment/csv/gender.csv) and [units.csv](https://github.com/Swirrl/table2qb/tree/employment-example/examples/employment/csv/units.csv))
 
    - A separate codelist file should be created for every component from the input file that is defined by a codelist.
    - The codelist files should contain all the possibles values for each codelist.

Observation data is contained in [input.csv](https://github.com/Swirrl/table2qb/tree/employment-example/examples/employment/csv/input.csv) and is simply a CSV file with tabular data.


## columns.csv configuration

Aside from preparing the reference data files as described above, an additional [/resources/columns.csv](https://github.com/Swirrl/table2qb/blob/employment-example/resources/columns.csv) file should be created.

This file defines the mapping between a column name and the relevant component and sets out any preparatory transformations and URI templates.
It should contain one row per component, as well as one row per each unique value of the `Measure Type` dimension (as above).


## Running table2qb

Clone the `cli` branch of the project and build the jar by running:

`lein uberjar` - This will create the `/target` directory.


In order to execute the full table2qb process, the following 3 pipelines should be run (in no particular order):

- `components-pipeline`
- `codelist-pipeline` (run for each codelist CSV input file)
- `cube-pipeline`


1. To run the `components-pipeline` for this example of Scottish government employment data use the following command:

    ```BASE_URI=http://statistics.gov.scot/ java -jar target/table2qb-0.1.3-SNAPSHOT-standalone.jar exec components-pipeline --input-csv examples/employment/csv/components.csv --column-config resources/columns.csv --output-file examples/employment/ttl/components.ttl```


2. To run the `codelist-pipeline` for each of the codelist files in this example of Scottish government employment data use the following commands:

    ```BASE_URI=http://statistics.gov.scot/ java -jar target/table2qb-0.1.3-SNAPSHOT-standalone.jar exec codelist-pipeline --codelist-csv examples/employment/csv/gender.csv --codelist-name "Gender" --codelist-slug "gender" --column-config resources/columns.csv --output-file examples/employment/ttl/gender.ttl```
    
    
    ```BASE_URI=http://statistics.gov.scot/ java -jar target/table2qb-0.1.3-SNAPSHOT-standalone.jar exec codelist-pipeline --codelist-csv examples/employment/csv/units.csv --codelist-name "Measurement Units" --codelist-slug "measurement-units" --column-config resources/columns.csv --output-file examples/employment/ttl/measurement-units.ttl```
    

3. To run the `cube-pipeline` for this example of Scottish government employment data use the following command:

    ```BASE_URI=http://statistics.gov.scot/ java -jar target/table2qb-0.1.3-SNAPSHOT-standalone.jar exec cube-pipeline --input-csv examples/employment/csv/input.csv --dataset-name "Employment" --dataset-slug "employment" --column-config resources/columns.csv --output-file examples/employment/ttl/cube.ttl```


The outputs of the pipelines will be stored as TTL files in the given output directories.
