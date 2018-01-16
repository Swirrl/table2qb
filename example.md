# Example table2qb process

This example shows how to load the follow csv file:

```csv
Year,Count
2015,5
2016,10
2017,20
```

## 1. Load metadata

### 1.1 Create Component

Load the sdmx/ qb vocabularies (which include a definition of `sdmx-dimension:refPeriod`)

Create the "count" measure-property; either via a UI or a pipeline with something like the following csv upload:

```csv
Measure
count
```

This would create a property (e.g. `eg:count`), label, and class (for the `rdfs:range` of the property) in a measures ontology.

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

### 1.3 Create Conventions

This might just be an edn file like:

```edn
(def components
  {"Year": "year",
   "Count": "count"})

(def component-properties
  {"year": "sdmx-dim:refPeriod",
   "count": "eg:count"})

(def component-valueUrls
  {"year": "http://reference.data.gov.uk/id/year/{year}"})

(def component-value-transformations
  {"count": gec/parseNumber})
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

This gives us the basis for the DSD and the mapping from the table to RDF.

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

This step would compare the transformed URIs to codelists, or the literals to other rules (e.g. count cannot be missing or negative).

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

The json metadata should now be sufficient to load with a csv2rdf pipeline.