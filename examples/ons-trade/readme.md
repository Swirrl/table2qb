**This example has since been superceded by the [one in the pipeline](https://github.com/Swirrl/table2qb/tree/master/table2qb#example)**

---------------------

# Regional Trade Example

- Load all prerequisite reference data first: vocabularies, components and codelists (see the [metadata directory](./metadata) for the specifics).
- Establish [conventions](./conventions.clj) that map from the terminology used in the file to the reference data.
- Run the prepare step on the [input regional-trade.csv](./regional-trade.csv) to produce a [intermediate regional-trade.slugged.csv](./regional-trade.slugged.csv) and [regional-trade.slugged.csv-metadata.json](./regional-trade.slugged.csv-metadata.json)
- Run the final csv2rdf step on those last two files to produce the Observations, DataStructureDefinition and ComponentSpecs:

```
gem install linkeddata
rdf serialize --input-format tabular regional-trade.slugged.csv --output-format ttl
```

This produces the output in [regional-trade.ttl](./regional-trade.ttl).

## Split pipeline variation

The [Split pipelines](./split) involves multiple csvw pipelines that each create a different resource of the cube - i.e. one pipeline per `rdf:type`. This would allow distinct outputs to handled separately (loaded into distinct graphs etc). It also obviates the need to include json-ld in the metadata; instead of creating this during the preparation phase (and having it passed through the translation unchanhed) we can use the translation phase to create this directly.
