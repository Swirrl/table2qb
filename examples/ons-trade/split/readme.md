# ONS-Trade with split pipelines

This variation of the example involves multiple csvw pipelines that each create a different aspect of the cube.

This would allow distinct outputs to handled separately (loaded into distinct graphs etc).

For the most part it works, but it becomes difficult dealing with multiple subjects per row (essentially components etc are one per column). See the components pipeline for example, which need to create a virtual column for each `?cs a qb:ComponentSpecification` statement. This would need doing for `?cs pmd:codesUsed ?used_codes_list` (in the same pipeline) and `?used_codes_list a skos:ConceptScheme` (in the code-used pipeline).

It may be possible to include json-ld in the metadata document instead of a csv2rdf translation. Alternatively we may prefer to normalise the data (one row per component first instead). This will be a little complicated as the measures dimensions come from the rows of the measureType column.
