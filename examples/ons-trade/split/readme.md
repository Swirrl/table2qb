# ONS-Trade with split pipelines

This variation of the example involves multiple csvw pipelines that each create a different aspect of the cube.

This would allow distinct outputs to handled separately (loaded into distinct graphs etc).

Although we can get quite far using the same input file, it becomes difficult dealing with the multiple subjects per row (essentially components etc are one per column). Every time you want to add another statement about the components, you need to create a virtual columns for each; this quickly becomes unmanageable.

Instead what we can do is to first transform the input file into a semi-normalised version with one component per row. This might be considered semi-normalised because the measures dimensions come from the rows of the measureType column. That is to say, all columns except for `Measure` and `Value` are normalised (forming rows), the `Measure` column has it's distinct row values kept as rows, and a `MeasureType` row is appended.
