# Reference Data

## Vocabularies

- [RDF Data Cube (qb)](./qb.ttl)

## Components

### External

- dimensions:
  - [SDMX Reference Period and Area](./sdmx-dimension.ttl)
  - Measure Type (included in qb spec)
- attributes:
  - [SDMX Measurement Unit](./sdmx-attribute.ttl)


### Internal

The components ontology pipeline provides:

- dimensions:
  - SITC
  - Flow
- measures:
  - Value
  - Net Mass

This comes from a source [components.csv](./components.csv) that is transformed to create [components-transformed.csv](./components-transformed.csv) and [components-transformed.csv-metadata.json](./components-transformed.csv-metadata.json) which in turn can be translated into [components.ttl](./components.ttl).

## Codes and Codelists:

### External

- [The Year 2016 and Ontology](./2016.rdf)
- [The UK Statistical Geography and Ontology](./uk.ttl)

### Internal

- [SITC Sections Concepts and Scheme](./sitc-sections.ttl)
- [Flow Directions Concepts and Scheme](./flow-directions.ttl)
- [Measurements Units Scheme](./units.ttl): "GBP-million", and "Tonnes"
