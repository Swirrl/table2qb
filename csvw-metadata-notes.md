# Relaxed vs Strict

Does this distinction still exist?


# Date Format

Description object for a date column:

    {
      "name": "DateCode",
      "titles": "Date Code",
      "datatype": {
        "base": "date",
        "format": "d/M/yyyy"
      }
    }

Quite prescriptive about date format. Not necessarily a problem if individual files stick to a single format.

Output value would be canonical (XSD) representation of the date - i.e. YYYY-MM-DD.

How to present ranges? We probably ought to generate our own datatypes for the reference.data.gov.uk periods/ intervals.


# Lookups

Perform with table group?

Retreive from database or elsewhere (provide csv URL for completeness/ interop, but then ignore it)?



# Loss of component type

We'll need to lookup component properties (i.e. propertyUrl for each column) to determine it's specific type in order to attach them to the DSD. Perhaps we ought to prepare a separate tabular-metadata schema that creates the DSD?
