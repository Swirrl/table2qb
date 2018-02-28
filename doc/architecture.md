# table2qb Architecture Proposal

This proposal draws on comments on the [requirements](./requirements.md) (#1, #2) which suggest we should adopt the [csvw](https://www.w3.org/TR/tabular-data-model/) approach to metadata (i.e. json-ld with a csvw vocab). This effectively leads us to writing a [csv2rdf](https://www.w3.org/TR/csv2rdf/) implementation.

## Overview

A couple of principles have been uncontroversial:
- Reference data (including both properties and codes) should be loaded before the data is loaded.
- A data table will go through a preparatory stage that builds up metadata/ URIs before loading.
- The transformation of the data table will use both the cleaned data and the json metadata

The workflow might look like this:

![workflow diagram](workflow.png?raw=true)

See this [slide show](https://docs.google.com/presentation/d/1-wPkjdhzAejKpvf2BVwEblsAbRiff8g0nYt1Hb1BUcQ/edit#slide=id.g2e1ff91010_0_0) for the editable diagram.

More specifically the process would be as follows:

- *Metadata Loading*:
  - Create components: `qb:DimensionProperty`, `qb:AttributeProperty` and `qb:MeasureProperty`
  - Create codelists: `skos:ConceptScheme`, `owl:Class` (these would be related to the above via `qb:codeList` at this stage)
  - Create conventions: common mappings from string headers in csv to a default configuration
- *Data loading*:
  - *Preparation*: 
    - Identify Components: mapping from a column name to a cube component (e.g. "Date" => `smdx:refPeriod`)
    - Transform URIs and Literals: transform strings to URIs or literals based upon transformations (converted in the table) or `csvw:valueUrl` templates (conversion in csvw step) (see #3 [RFC6570](https://tools.ietf.org/html/rfc6570) for template spec).
    - Validate Cells: check if codes are present in codelists, find missing values (blank cells)
    - Extract Marginals: collect codes enumerating cube extent (how we currently use `qb:codeList`)
  - *Conversion*: the above will furnish us with a cleaned csv file and a csvw-metadata json file that may be feed into a csvw pipeline to create RDF

[Here's a minimum example](./minimum-example.md) of how this might work. A more [realistic example](../examples/ons-trade/) is provided for the ONS Regional Trade dataset (taken from the pilot).

## Implications of Implementing csv2rdf

If we adopt the [csv2rdf](https://www.w3.org/TR/csv2rdf/) approach we will effectively build csvw-graft, a clojure implementation of the spec. We only really need to support [minimal mode](https://www.w3.org/TR/csv2rdf/#dfn-minimal-mode) i.e. just converting the cells. By contrast [standard mode](https://www.w3.org/TR/csv2rdf/#dfn-standard-mode) produces lots of bookkeepping output (i.e. how the source cells relate to the rdf) - this may be useful for auditting (describing provenance) and tracking input errors in future.

We would also need to support the transformation of json-ld included in the csvw-metadata. In particular, the [rdf-cube example](https://github.com/w3c/csvw/blob/gh-pages/examples/rdf-data-cube-example.md) includes the DSD in the foo.csv-metadata.json as json-ld. This can probably be done using RDF4j/grafter for json and CURIE expansion etc.

## Conventions/ Rules

As above these would map commonly used headers `csvw:titles` (e.g. `["Date","Dates","Year"]` etc) to an internal identifier `csvw:name` (e.g. `period`) that would in term describe:
- the `propertyUrl` for the `qb:ComponentProperty` (e.g. a dimension property like `sdmx:refPeriod`) (and thus a `qb:codeList`)
- a `valueURL` for converting the value/ slug into an object URI (e.g. a URI template like "http://purl.org/linked-data/sdmx/2009/code#{gender}")
- a transformation function (for more complex `* -> URI` operations - e.g. turning a Datetime into an Interval or enum->uri lookups)
- some validation functions
- a `qb:order` for the component (useful for core dimensions) - otherwise the position in the csv would be used

We should probably start by specifying this in clojure (or maybe even edn) although we may later want to make this user-configurable. Ultimately these conventions should be what standardises client-specific requirements and should be what takes the majority of time required to build pipelines in future.


## URI slugs

This requirement is ubiquitous (for us) but isn't dealt with satisfactorily in the csvw specs. It is typically assumed that variable inputs to URI templates are already URI encoded (mostly IDs from other systems). By contrast we find ourselves slugifying strings very often (either for legibility, because there is no pre-existing unique identifier, or simply because the string *is* the identifier).

The specs provide for something called a [transformation definition](https://www.w3.org/TR/2015/REC-tabular-metadata-20151217/#dfn-transformation-definition) as part of the csvw tabular metadata spec. This is a bit clunky to use as you need to provide:

- url: to a script that MUST be fetched (typically specified relative to the location of the csv)
- targetFormat: a uri for the media-type (and slug doesn't appear to be assigned by IANA)
- scriptFormat: another media-type uri (`application/vnd.swirrl.grafter.edn`?!)

We really don't want to have to go and do all this just for a simple slugify transformation.

It doesn't look like the URI templates provide for this either [RFC6570](https://tools.ietf.org/html/rfc6570). The spec does reserve the `$` operator for external use but if we use this for slugging then we won't be interoperable with others implementing csvw using the basic RFC6570.

We could side-step this (i.e. without violating the spec) by having the slugifying happen in the preparatory step. We can create copies of the relevant columns (as we do at the moment) e.g. `date-slug`.


## Moving from `qb:codeList` to (e.g) `pmd:codesUsed`

There's value in attaching collections (skos:ConceptSchemes) of codes to cubes for two purposes:

- a code list - defines the full range of permissible values for a coded property (indeed this could be the amalgamation of multiple other code lists (e.g. local-authorities + wards). This is useful for validation or visualisation (where gaps are significant).
- a cube marginal - defines the range of used values for that property (i.e. only those that can be found in the observations). This provides indexes for tools (cube viewer) or performance etc.

We're currently using `qb:codeList` to attach a list of used codes (the cube marginal) to the relevant `qb:ComponentSpecification` (although the spec declares the `rdfs:range` of this property to be `qb:CodedProperty` i.e. the dimension).

We could correct this by:
 
- using `qb:codeList` correctly, it would be specified when creating the `qb:DimensionProperty` and the cell content would use this for validation
- attaching `pmd:codesUsed` to the `qb:ComponentSpec` (which, unlike the dimension, is cube-specific). This would be created/ updated when preparing the data (Extract Marginals).

These marginals could get quite unwieldy, perhaps we'd want to load them immediately into a draft (rather than serialising them as json-ld to be loaded as part of the csv2rdf step).
