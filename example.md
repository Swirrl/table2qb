# Example table2qb process

This example shows how to load the follow csv file:

```csv
Year,Count
2015,5
2016,10
2017,20
```

Note this is ["tidy data"](http://vita.had.co.nz/papers/tidy-data.pdf): (normalised to) one observation per row, one column per variable (component) - a date dimension, and a count measure.

There is also an overview of the [architecture](./architecture.md) (which also has a more detailed treatment of a few issues) and a summary of the [specification](./specification.md) (written much earlier but may still help to explain the problems and requirements that have brought us to this point).

## 1. Load metadata

### 1.1 Create Components

Load the sdmx/ qb vocabularies (which include a definition of `sdmx-dimension:refPeriod` for the date dimension).

Create the "count" measure-property; either via a UI or a pipeline with something like the following csv upload:

```csv
Measure
count
```

This would create a labelled property (e.g. `eg:count`) and class (for the `rdfs:range` of the property) in a measures ontology.

### 1.2 Create Codelists

Load a codelist for years. Perhaps something like:

```csv
code-uri
http://reference.data.gov.uk/id/year/2015
http://reference.data.gov.uk/id/year/2016
http://reference.data.gov.uk/id/year/2017
http://reference.data.gov.uk/id/year/2018
```

This example isn't really very insightful, it would make more sense for codes which don't already exist as linked-data (i.e. where labels and notations exist but no URIs).

We'd likely also want a way to related codelists to dimensions (i.e. to specify `?dim qb:codeList ?concept_scheme`).

### 1.3 Create Conventions

This will probably be server-side configuration for the time being, but it could one day be user-configurable. For now, this might just be an edn file like:

```edn
(def components
  {"Year" "year",
   "Count" "count"})

(def component-properties
  {"year" "sdmx-dim:refPeriod",
   "count" "eg:count"})

(def component-valueUrls
  {"year" "http://reference.data.gov.uk/id/year/{year}"})

(def component-value-transformations
  {"count" ::grafter.extra.cell.string/parseNumber})
```




## 2. Load Data

### 2.1 Prepation

#### 2.1.1 Identify Components

We would begin with the csv above and a target dataset-uri e.g. `http://example.com/data/simple`.

Using the above conventions and the table headers we can establish the lion's share of the csvw metadata:

```json
{
        "@context": [ "http://www.w3.org/ns/csvw", {
                "@language": "en",
		"@base": "http://example.org/",
                "eg": "http://example.org/",
                "sdmx-dimension": "http://purl.org/linked-data/sdmx/2010/dimension#",
                "qb": "http://purl.org/linked-data/cube#"
        }],
        "@id": "http://example.com/data/simple",
        "qb:structure": {
                "qb:component": [
                        { "qb:dimension": "sdmx-dimension:refPeriod" },
                        { "qb:measure": "eg:count" }
                ]
        },
        "tableSchema": {
                "columns": [
                        {"name": "year", "titles": "Year", "propertyUrl":"sdmx-dimension:refPeriod"},
                        {"name": "count", "titles": "Count", "propertyUrl":"eg:count"} 
                ],
		"aboutUrl": ":/data/simple/date/{year}/count"
        }
}
```

This gives us the basis for the `qb:DataStructureDefinition`, `qb:ComponentSpecifications` and the mapping from the table to RDF that will allow us to create a `qb:Observation` from each row.

We probably ought to include more "@id" values (subject uris) for the intermediate resources like the `qb:ComponentSpecification` (rather than having these be blank nodes).

#### 2.1.2 Transform URIs and Literals

At this stage we can either rely on URI templates e.g.

```json
{
        "tableSchema": {
                "columns": [{
			   "name": "period",
			   "titles": "Year",
			   "propertyUrl":"sdmx-dimension:refPeriod",
			   "valueUrl": "http://reference.data.gov.uk/id/year/{year}"
		}]
	}
}
```

Or, for more complex transformations, we could convert the table cells instead, e.g. using CURIEs (where `PREFIX @year: <http://reference.data.gov.uk/id/year/>`):

```csv
Year,Count
year:2015,5
year:2016,10
year:2017,20
```

Similarly we would transform literals at this stage (e.g. parsing strings to numbers).

#### 2.1.3 Validate Cells

This step would compare the transformed URIs to codelists, or the literals to other rules (e.g. count cannot be missing or negative). Much of this should follow from the above configuration.

#### 2.1.3 Extract Marginals

This step would add a concept scheme (to the json):


```json
{
	"qb:structure": {
                "qb:component": [{ "qb:dimension": "sdmx-dimension:refPeriod",
			"pmd:usedCodes": {
				"skos:member": [
					"http://reference.data.gov.uk/id/year/2015",
					"http://reference.data.gov.uk/id/year/2016",
					"http://reference.data.gov.uk/id/year/2017"
				]
			}
		}]
	}
}
```

NB: In contrast with the dimension's `qb:codeList`, the `pmd:usedCodes` list doesn't include 2018 (perhaps because no data has been published for that year yet).

### 2.2 Conversion

The json metadata should now be sufficient to load with a csv2rdf pipeline. Note that the [cswv qb example](https://github.com/w3c/csvw/blob/gh-pages/examples/rdf-data-cube-example.md) suggests that the json-ld metadata be quite terse leaving the remaining statements (i.e. to set rdf-types like the `<row-x> a qb:Observation` etc) to be filled out through [cube normalisation](https://www.w3.org/TR/vocab-data-cube/#normalize) (fyi: [grafter.extra.validation.cube/normalise-cube](https://github.com/Swirrl/grafter-extra/blob/master/src/grafter/extra/validation/cube.clj#L22-L26)).
