(ns conventions
  (:require [grafter.vocabularies.sdmx-dimension :all]
            [grafter.vocabularies.sdmx-attribute :all]
            [grafter.url :refer [->url]]))

(def components
  "Mapping from a csvw:header to a csvw:name (i.e. external text to internal variable)"
  {"GeographyCode" :geography
   "DateCode" :date
   "Measurement" :measure
   "Units" :unit
   "Value" :value
   "SITC Section" :sitc
   "Flow" :flow})

(def component-properties
  "Mapping from csvw:name to qb:ComponentProperty"
  {:geography sdmx-dimension:refArea
   :date sdmx-dimension:refPeriod
   :measure sdmx-dimension:measureType
   :unit sdmx-attribute:unitMeasure
   :value (->url "http://statistics.data.gov.uk/def/measure-properties/{measure-slug}")
   :sitc (->url "http://statistics.data.gov.uk/def/dimension/sitcSection")
   :flow (->url "http://statistics.data.gov.uk/def/dimension/flow")})

(def component-value-urls
  "Mapping from csvw:name to a uri template for the cell values"
  {:geography "http://statistics.data.gov.uk/id/statistical-geography/{geography}"
   :date "http://reference.data.gov.uk/id/year/{date}"
   :measure "http://statistics.data.gov.uk/def/measure-properties/{measure-slug}"
   :unit "http://statistics.data.gov.uk/def/concept/measurement-unit/{unit-slug}"
   :sitc "http://statistics.data.gov.uk/def/concept/sitc-section/{sitc-slug}"
   :flow "http://statistics.data.gov.uk/def/concept/flow/{flow-slug}"})

(def component-value-transformations
  "Mapping from csvw:name to a reference to a transformation function.
   Where a single csvw:name is provided, the update should be made in place.
   Where a pair of csvw:names are provided, the value in the first is where the argument should be found and the second where the result should be placed"
  {:value ::grafter.extra.cell.string/parseNumber
   :measure ::grafter.extra.cell.uri/slugize
   :unit ::reference.to.something.that.can/slugize-gbp
   :sitc ::grafter.extra.cell.uri/slugize
   :flow ::grafter.extra.cell.uri/slugize})
