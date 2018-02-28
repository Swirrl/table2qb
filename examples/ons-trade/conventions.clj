(ns conventions
  (:require [grafter.vocabularies.rdf :all]
            [grafter.vocabularies.skos :all]
            [grafter.vocabularies.dcterms :all]
            [grafter.vocabularies.sdmx-dimension :all]
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
   "Flow" :flow
   "Label" :label
   "Description" :description
   "Notation" :notation
   "Broader Notation" :broader_notation
   "Component Type" :component_type
   "Code List" :codelist ;;?
   })

(def about-urls
  "Mapping from ? to a csvw:aboutUrl"
  {"?concept?" "http://statistics.data.gov.uk/def/concept/{notation}"
   "?concept-scheme?" "http://statistics.data.gov.uk/def/concept-scheme/{slug}"})

(def component-properties
  "Mapping from csvw:name to an rdf:Property that will form the csvw:propertyUrl"
  {:geography sdmx-dimension:refArea
   :date sdmx-dimension:refPeriod
   :measure sdmx-dimension:measureType
   :unit sdmx-attribute:unitMeasure
   :value (->url "http://statistics.data.gov.uk/def/measure-properties/{measure}")
   :sitc (->url "http://statistics.data.gov.uk/def/dimension/sitcSection")
   :flow (->url "http://statistics.data.gov.uk/def/dimension/flow")
   :label rdfs:label
   :description dcterms:description
   :component_type rdf:a
   :notation skos:notation
   :broader_notation skos:broader
   :codelist qb:codeList})

(def component-value-urls
  "Mapping from csvw:name to a csvw:valueUrl uri template for the cell values"
  {:geography "http://statistics.data.gov.uk/id/statistical-geography/{geography}"
   :date "http://reference.data.gov.uk/id/year/{date}"
   :measure "http://statistics.data.gov.uk/def/measure-properties/{measure}"
   :unit "http://statistics.data.gov.uk/def/concept/measurement-unit/{unit}"
   :sitc "http://statistics.data.gov.uk/def/concept/sitc-section/{sitc}"
   :flow "http://statistics.data.gov.uk/def/concept/flow/{flow}"
   :component_type "{+component_type}"
   :codelist "{+codelist"})

(def component-value-transformations
  "Mapping from csvw:name to a reference to a transformation function.
   Where a single csvw:name is provided, the update should be made in place.
   Where a pair of csvw:names are provided, the value in the first is where the argument should be found and the second where the result should be placed"
  {:value ::grafter.extra.cell.string/parseNumber
   :measure ::grafter.extra.cell.uri/slugize
   :unit ::reference.to.something.that.can/slugize-gbp
   :sitc ::grafter.extra.cell.uri/slugize
   :flow ::grafter.extra.cell.uri/slugize
   [[:notation :label] :notation] ::reference.to/slug-if-blank ;; create a notation if from the label unless one is provided
   [:component_type :type]  {"Dimension" "qb:DimensionProperty", "Measure" "qb:MeasureProperty"} ;; example of a lookup
   [:component_type :component_type_slug] ::grafter.extra.cell.uri/slugize
   [:component_label :component_property_slug] ::grafter.extra.cell.uri/propertize
   [:component_label :component_class_slug] ::grafter.extra.cell.uri/classize
   })

;; probably shouldn't replace as that requires processing to happen in order


;; how to specify additional virtual columns? json template?
