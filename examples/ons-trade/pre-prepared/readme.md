# Pre-prepared uploads - division of responsibilities between DataBaker and table2qb

The [table2qb](../table2qb) prepare step will transform the csv that is uploaded and create a json metadata file.

Some of this responsibility could be taken on by the uploader. The directory provides some examples of what that might look like.


## CSV Transformation

In essence, the heart of this problem is creating [URI slugs from strings](https://github.com/Swirrl/table2qb/blob/master/specification.md#strings-as-identifiers---how-to-derive-uris).

In certain cases, the uploader may know how a given input ought to be interpreted as a URI. For example, the uploader may know from which codelist or registry a given code comes.

Ultimately we could allow the upload format to include URIs in the cells: [as in this example, where all the cells are provided as CURIES](./regional-trade.curie.csv) (with the exception of the value column which is a literal in the RDF). This would require some agreement (shared config) over prefixes. We would probably need some way to determine whether a cell ought to be interpreted as a string or a URI/CURIE (e.g. heuristics on cell contents - like the presence of a ":" in the cell, conventions on column names "Geography" vs "Geography URI", or perhaps in csvw json metadata).

In theory, we could extend this to allow the `qb:ComponentProperty`s to be specified as URIs in the column headers (instead of relying on conventions mapping from label to URI).

It's important to realise that as we move in this direction, the upload/download specifications begin to diverge (although arguably we might want to allow end-users to download csvs of curies). 


## JSON Creation

At the moment, we're working on the assumption that this will be created from scratch by table2qb based upon the uploaded csv.

Perhaps we could receive a partial json metadata file and decorate this?

Maybe we can create a common json template and then allow alternate implementations to fill-in-the-blanks.