(ns table2qb.configuration.column
  "Namespace defining the structure of a column descriptor represented by a row in a columns configuration file."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [grafter.extra.cell.uri :as gecu]
            [table2qb.csv :as csv]))

;;TODO: make these more specific
(s/def ::URITemplate string?)
(s/def ::CSVWDatatype string?)
(s/def ::CSVWName string?)
(s/def ::TransformFn fn?)

(s/def ::title string?)
(s/def ::name ::CSVWName)
(s/def ::property_template ::URITemplate)
(s/def ::value_template ::URITemplate)
(s/def ::datatype ::CSVWDatatype)
(s/def ::value_transformation ::TransformFn)

(s/def ::type #{:dimension :attribute :measure :value})

(s/def ::Column (s/keys :req-un [::title ::name ::type]
                        :opt-un [::property_template ::value_template ::datatype ::value_transformation]))

(defn column-key [column]
  (keyword (:name column)))

(def column-title :title)
(def column-type :type)
(def column-name :name)
(defn component-attachment [column]
  (get {:dimension "qb:dimension"
        :attribute "qb:attribute"
        :measure "qb:measure"}
       (:type column)))
(def property-template :property_template)
(def value-transformation :value_transformation)

(defn value-column? [column]
  (= :value (:type column)))

(defn dimension-column? [column]
  (= :dimension (:type column)))

(defn- replace-symbols [s]
  (string/replace s #"£" "GBP"))

(defn unitize [s]
  (gecu/slugize (replace-symbols s)))

;; TODO resolve on the basis of other component attributes? https://github.com/Swirrl/table2qb/issues/18
(def column-transformers
  {"slugize" gecu/slugize
   "unitize" unitize})

(def ^:private attachment->type {"qb:dimension" :dimension
                                 "qb:measure" :measure
                                 "qb:attribute" :attribute})

(defn validate-column-type [row column value]
  (if (string/blank? value)
    :value
    (let [type (get attachment->type (string/trim value) ::missing)]
      (if (= ::missing type)
        (csv/throw-cell-validation-error row column "Value must be blank or one of qb:dimension, qb:measure or qb:attribute" {})
        type))))

(defn validate-name [row column value]
  (if (string/includes? value "-")
    (csv/throw-cell-validation-error row column "csvw:name cannot contain hyphens (use underscores instead)" {})
    value))

(defn- validate-csvw-datatype [row column value]
  ;;TODO: validate valid CSVW datatype
  value)

(def csv-columns [{:title    "title"
                   :key      :title
                   :validate [csv/validate-not-blank]
                   :required true}
                  {:title "name"
                   :key :name
                   :required true
                   :validate [csv/validate-not-blank validate-name]}
                  {:title     "component_attachment"
                   :key       :type
                   :transform validate-column-type}
                  {:title "property_template"
                   :key :property_template
                   :transform (csv/optional csv/uri-template)}
                  {:title "value_template"
                   :key :value_template
                   :transform (csv/optional csv/uri-template)}
                  {:title "datatype"
                   :key :datatype
                   :transform (csv/optional validate-csvw-datatype)}
                  {:title "value_transformation"
                   :key :value_transformation
                   :transform (csv/optional (csv/validate-mapping column-transformers))}])

(defn normalise-column-record [row]
  (let [optional-keys [:property_template :value_template :datatype :value_transformation]]
    (reduce (fn [m opt-key]
              (if (nil? (get row opt-key))
                (dissoc m opt-key)
                m))
            row
            optional-keys)))

(defn is-qb-measure-type-column?
  "Whether the given column represents a qb:measureType dimension"
  [column]
  (and (dimension-column? column)
       (= "http://purl.org/linked-data/cube#measureType" (property-template column))))