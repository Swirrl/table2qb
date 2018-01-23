# Regional Trade Example

- Load all prerequisite reference data first: vocabularies, components and codelists (see the [metadata directory](./metadata) for the specifics).
- Establish [conventions](./conventions.clj) that map from the terminology used in the file to the reference data.
- Run the prepare step on the [input regional-trade.csv](./regional-trade.csv) to produce a [intermediate regional-trade.slugged.csv](./regional-trade.slugged.csv) and [regional-trade.slugged.csv-metadata.json](./regional-trade.slugged.csv-metadata.json)
- Run the final csv2rdf step on those last two files to produce the Observations, DataStructureDefinition and ComponentSpecs:

```
gem install linkeddata
rdf serialize --input-format tabular regional-trade.slugged.csv --output-format ttl
```
