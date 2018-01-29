# CSVW CSV2RDF++ Design Notes

Notes from a design & architecture discussion between @RicSwirrl and
@RickMoynihan `[2018-01-24 Wed]`.  Written up by @RickMoynihan.

The ideas are probably not fundamentally different from Robins work.
This document is intended to help align perspectives and facilitate
discussion.

## Scope

These notes are concerned primarily with the CSV2RDF++ phase, though
touch on other aspects, e.g. a prep phase, and dataset/vocab creation.
It is assumed that these phases have occured as pre-requisites.

## Outline

This design is intended to operate under the following constraints:

1. Make as much of the process as possible pure and free of side effects.
2. Leave the responsibility of naming `csvw:Table` inputs to a side
   effecting step at the end.
3. Be testable by providing useful inspection points.
4. Provide several different wrapping applications around a pure core
   for use in different contexts.  e.g. a command line tool for
   running transformations, and validating output; along with a HTTP
   service tool.

## High Level Design

![table2qb overview](img/table2qb-overview-1.svg)

The diagram above describes at a high level the processes involved
that should be applicable within all the different contexts.

The contexts described later, show how the bulk of this high level
process can be materialised across several different environments:

- A purely local command line environment (e.g. onsite at ONS)
- A publismydata environment (featuring draftsets/pipelines/etc)
- A hypothetical user using the opensource tools with their own "side
  effector"

We envision that `csv2rdf++` is a pure (and reusable) function that
takes as input two "values" as arguments the `input.csv` file as a
sequence of a rows and the `tabuar-metadata.jsonld` as a EDN map/tree.
Both of these inputs are provided by the prep phase.  This function
emits a sequence of quads, which depending on the "application
wrapper" will be materialised into a trig/nquads file, a draftset, and
possibly also a SPARQL update endpoint.

The wrapping applications should support several modes which can
selectively be turned on and combined at will.  These are currently
`:observation`, `:dsd` & `:coverage`.  When a mode is enabled triples
of that classification will be output into a graph identified by a
corresponding URN, e.g. `<urn:swirrl:csv2rdf++:observation>` or
`<urn:swirrl:csv2rdf++:dsd>`.  These graphs can then be used to easily
filter out groups of data for either debugging, validation or
operational purposes.

Additionally we believe it is important to propogate state and capture
the user intention / operation throughout the wider process.  We
therefore propose using `prov-o` to represent simultaneously:

- the operations that have occured, 
- the current state of the transformation/workflow and
- improve the capture/publication of the provinance and transformation
  of data as additional metadata which may also be published.

@Robsteranium mentioned on slack that CSVW/csv2rdf has similar
provisions for using `prov` in standard mode, so we may wish to
consider aligning these perspectives.  Though @RicSwirrl and I
discussed

Here we assume the [prep phase](#prep-phase) has already happened.  

## Wrapping Applications

We envision implementing several different wrapping applications, that
wrap the core `csv2rdf++` function and expose its core functionality to
support different environments, usecases and contexts.

In addition to the applications, we also envision a component
currently called the "Side Effector" who's job is to apply the
declarations made by the pipeline process to the world.  Such tasks
include:

- Loading triples materialised in the previous steps into the database
- Uploading the `input.csv` (and probably its accompanying
  `tabular-metadata.jsonld`) to a place on the web where it can later
  be published.

This process is necessarily bespoke to Swirrl/PMD, as it will require
configuration & credentials for web services we use such as S3,
drafter, and knowledge of the protocols involved.

The set of functions included in the Side Effectors will likely be
published in a `csv2rdf++-app-helper` project.

## Command Line Wrapper

The command line wrapper (name `qb-tool.jar` is placeholder) will be
usable like this (details of command line illustrative and open to
more bike shedding):

```
$ qb-tool.jar help

QB Tool Help:

   # Translate a `tabular-metadata.jsonld` and its corresponding CSV 
   # to a set of RDF graphs 
   
   translate tabular-metadata from <web|filesystem> <source-tabular-metadata.jsonld> to <draftset|filesystem> <destination> [options]

   # Load RDF from a prior translate run with the supplied loader

   load tabular-metadata from <drafter|filesystem> <source> to <drafter|filesystem> <destination> [options]
   
   # Validate the translated RDF output

   validate from <drafter|filesystem> <source> to <drafter|filesystem> <report-destination>

```

### qb-tool translate

This command takes
a [tabular-metadata.jsonld](https://www.w3.org/TR/tabular-metadata/)
file and translates it into RDF.

`translate` is runnable in the following modes:

- `:default` (implicit mode), a conservative combination of modes probably `:coverage,:observations,:dsd,:prov`
- `:all` applies all modes at once.
- `:coverage` mode will scan the CSV file and coin new
  `dimension-values` for values that don't already exist in the DSD.
  Coverage mode will only add dimension-values to the dsd.  And will
  not add values to the global codeList.
- `:codelist-values` similar to coverage, but will use the codeList
  from the DSD and construct new values found in the csv and append
  them to the universal codelist.


The json-ld file is expected to contain amongst the other data a
reference to the `.csv` file:

```
  "url": "regional-trade.csv"
```

This property is as the spec says resolved relative to the
`tabular-metadata.jsonld` file, which is passed on the commandline, e.g.

```
$ ls 
regional-trade.csv regional-trade.jsonld
$ qb-tool.jar translate tabular-metadata from filesystem ./regional-trade.jsonld to filesystem ./translated-output.trig :observations,:dsd,:coverage,:prov
```

The above command says to use the `filesystem` "from backend" to read
the jsonld file, and write a trig file to disk using the `filesystem`
"to backend".  The output above should be equivalent to having
specified `:all` classes of data.  The implementation will split
output by classification into separate temporarily named graphs, for
ease of filtering/processing/debugging.

If ./translated-output.trig already existed the implementation may
append quads into the destination file.  Allowing you to rerun the
process multiple times with different modes, and verify output at each
step.

```
$ cat ./translated-output.trig
```

```turtle
@prefix sd: <http://www.w3.org/ns/sparql-service-description#> .
@prefix qb: <http://purl.org/linked-data/cube#> .
@prefix prov: <http://www.w3.org/ns/prov#> .
@prefix obs: <http://ons.domain.here/dataset-name/observations/> .
@prefix : <http://publishmydata.com/vocab/csv2qb> . # TODO...

<urn:prov-graph-a3ba54494462ce193f7b87ba75b47e3f3a9df5fd> {

# Append step. 
<urn:d3b07384d113edec49eaa6238ad5ff00.csv> a :CSVFile ; 
     prov:wasGeneratedBy :csv2rdf++ .

<urn:1f2051184882afa1ef8d9cb5fd3c0362.jsonld> a :TabularMetadataJSONLD ; 
     prov:wasGeneratedBy :csv2rdf++ .

# probably a hash of all inputs (maybe also with a hash of the uberjar that ran it)
<urn:prov-output-3371371f52471ea05bc841d71bcc6fcb> a prov:Collection ;
    <urn:dsd-graph-3371371f52471ea05bc841d71bcc6fcb> a :DSDGraph ;
    <urn:observation-graph-6598829bf663f9e939ae42b5a6a09b25> a :ObservationGraph ;
    <urn:prov-graph-a3ba54494462ce193f7b87ba75b47e3f3a9df5fd> a :ObservationGraph ; # graph inception...
    # ...
    .

## VOCABS
:ValidationReport rdfs:subClassOf prov:Entity .

:CSVWInput rdfs:subClassOf prov:Entity .
:CSVFile rdfs:subClassOf :CSVWInput, csvw:Table .

:Table2QBOutput rdfs:subClassOf prov:Entity .
:DSDGraph rdfs:subClassOf sd:Graph, :Table2QBOutput .
:ObservationGraph rdfs:subClassOf sd:Graph, :Table2QBOutput .
:ProvenanceGraph rdfs:subClassOf sd:Graph, :Table2QBOutput .

:TabularMetadataJSONLD rdfs:subClassOf :CSVWInput .

:table2qb a prov:SoftwareAgent ;
          rdfs:label "Table 2 QB Process" .


:validator a prov:SoftwareAgent ; 
           rdfs:label "QB validator prior to publication"
}


## Data on Processes / Prov Agents 

#### steps 

## obs output...
<urn:observation-graph-6598829bf663f9e939ae42b5a6a09b25> {
        obs:7e98660b43feeb2772b9d30cdd8237e6 a qb:Observation ;
        # ...
        .
}

# dsd output...
<urn:dsd-graph-3371371f52471ea05bc841d71bcc6fcb> {
}
```


### validate

Validation mode will make use of the intention captured in `prov`
metadata so it knows what it should be validating, i.e. it will know
whether it should expect to see new dimension-values in the output
etc.

# Notes

### Rewriting table "url"

At load time the side-effector may need to rewrite triples for the
`"url"` parameter of the `input-table.csv` and
`tabular-metadata.jsonld` file to set them to whereever it stores
them, e.g. on S3.

### PMD / HTTP Wrapper

There will be a HTTP API which will allow more or less the same
options as the command line app, but apply them over a PMD HTTP
service.

### @base

We should use `@base` to delay providing prefixes on domain/client URIs until the final phases, where we can simply add an `@base` header to the RDF.

This should allow us to decide location without requiring knowledge of
the client/pmd-domain etc.
