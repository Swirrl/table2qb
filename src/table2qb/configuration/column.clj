(ns table2qb.configuration.column
  "Namespace defining the structure of a column descriptor represented by a row in a columns configuration file."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [grafter.extra.cell.uri :as gecu]
            [table2qb.util :as util]))

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

(defn- replace-symbols [s]
  (string/replace s #"£" "GBP"))

(defn unitize [s]
  (gecu/slugize (replace-symbols s)))

;; TODO resolve on the basis of other component attributes? https://github.com/Swirrl/table2qb/issues/18
(def column-transformers
  {"slugize" gecu/slugize
   "unitize" unitize})

(defn- resolve-value-transformation
  "Resolves the value_transformation cell of a configuration row to the corresponding transform function. Returns
   an exception if the function cannot be resolved."
  [transform]
  (if-let [tf (get column-transformers transform)]
    tf
    (let [msg (format "Invalid value_transformation function %s. Valid transformations: %s"
                      transform
                      (string/join ", " (keys column-transformers)))]
      (throw (ex-info msg {:type      :invalid-transform
                           :transform transform})))))

(defn- validate-title [title]
  (when (string/blank? title)
    (throw (ex-info "Title cannot be blank" {:type :blank-title}))))

(defn- validate-column-name [column-name]
  (when (string/blank? column-name)
    (throw (ex-info "csvw:name cannot be blank" {:type :blank-name})))

  (when (string/includes? column-name "-")
    (throw (ex-info (format "csvw:name %s cannot contain hyphens (use underscores instead): " column-name)
                    {:type :invalid-name
                     :name column-name}))))

(def ^:private column-attachment->type
  {"qb:dimension" :dimension
   "qb:measure" :measure
   "qb:attribute" :attribute
   nil :value})

(defn- get-column-type
  "Infers the type of a column from its component_attachment."
  [component-attachment]
  (if-let [type (get column-attachment->type component-attachment)]
    type
    (let [msg (format "Invalid component attachment: '%s'" component-attachment)]
      (throw (ex-info msg {:type                 :invalid-component-attachment
                           :component-attachment component-attachment})))))

(def required-input-keys #{:title :name :component_attachment})

(defn parse-column [{:keys [title name] :as row}]
  (validate-title title)
  (validate-column-name name)
  (let [{vt :value_transformation :as normalised} (util/map-values row util/blank->nil)
        optional-keys [:property_template :value_template :datatype :value_transformation]]
    (merge
      {:title                title
       :name                 name
       :type                 (get-column-type (:component_attachment normalised))}
      (util/filter-vals some? (select-keys normalised optional-keys))
      (when vt
        {:value_transformation (resolve-value-transformation vt)}))))