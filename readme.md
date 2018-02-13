# table2qb

This project provides a two step process for building rdf-cubes from tabular statistics. The first step is a preparatory transformation that converts a tidy csv (one row per observation) into several csv-on-the-web resources that may be translated into rdf using a csv2rdf processor.

For more information please see the [proposed architecture](./architecture.md) and the [worked example](./example.md).

The [grafter pipeline for the preparatory step is in the table2qb subdirectory](./table2qb/).

If you need more info check the original [specification](./specification.md) which discusses the requirements.
