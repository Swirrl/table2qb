# Regional Trade Example

- Load all prerequisite reference data first: vocabularies, components and codelists (see the [metadata directory](./metadata) for the specifics).
- Establish [conventions](./conventions.clj) that map from the terminology used in the file to the reference data.
- Run the prepare step on the [input regional-trade.csv](./regional-trade.csv) to produce a [intermediate regional-trade.prepared.csv](./regional-trade.prepared.csv) and [regional-trade.prepared.csv-metadata.json](./regional-trade.prepared.csv-metadata.json)
- Run the final csv2rdf step on those last two files to produce the Observations, DataStructureDefinition and ComponentSpecs
