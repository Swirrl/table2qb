(ns table2qb.pipelines.codelist
  (:require [table2qb.configuration.uris :as uri-config]
            [clojure.java.io :as io]
            [table2qb.csv :refer [write-csv-rows reader]]
            [table2qb.util :refer [create-metadata-source tempfile] :as util]
            [clojure.string :as string]
            [grafter.extra.cell.uri :as gecu]
            [table2qb.csv :as csv]
            [table2qb.configuration.csvw :refer [csv2rdf-config]]
            [integrant.core :as ig]))

(defn resolve-uris [uri-defs base-uri codelist-slug]
  (let [vars {:base-uri (uri-config/strip-trailing-path-separator base-uri) :codelist-slug codelist-slug}]
    (uri-config/expand-uris uri-defs vars)))

(defn get-uris [base-uri codelist-slug]
  (let [uri-map (util/read-edn (io/resource "templates/codelist-pipeline-uris.edn"))]
    (resolve-uris uri-map base-uri codelist-slug)))

(defn- type-column [type]
  {"propertyUrl" "rdf:type"
   "valueUrl" type
   "virtual" true})

(defn codelist-schema [csv-url codelist-name {:keys [codelist-uri code-uri parent-uri concept-types] :as column-config}]
  (let [base-columns [{"name"        "label",
                       "titles"      "label",
                       "datatype"    "string",
                       "propertyUrl" "rdfs:label"}
                      {"name"        "notation",
                       "titles"      "notation",
                       "datatype"    "string",
                       "propertyUrl" "skos:notation"}
                      {"name"        "parent_notation",
                       "titles"      "parent_notation",
                       "datatype"    "string",
                       "propertyUrl" "skos:broader",
                       "valueUrl"    parent-uri}
                      {"name"        "sort_priority"
                       "titles"      "sort_priority"
                       "datatype"    "integer"
                       "propertyUrl" "http://www.w3.org/ns/ui#sortPriority"}
                      {"name"        "description"
                       "titles"      "description"
                       "datatype"    "string"
                       "propertyUrl" "rdfs:comment"}
                      {"name"        "top_concept_of"
                       "titles"      "top_concept_of"
                       "propertyUrl" "skos:topConceptOf"
                       "valueUrl"    codelist-uri}
                      {"name"        "has_top_concept"
                       "titles"      "has_top_concept"
                       "aboutUrl"    codelist-uri
                       "propertyUrl" "skos:hasTopConcept"
                       "valueUrl"    code-uri}
                      {"name"        "pref_label"
                       "titles"      "pref_label"
                       "propertyUrl" "skos:prefLabel"}
                      {"propertyUrl" "skos:inScheme",
                       "valueUrl"    codelist-uri,
                       "virtual"     true}]
        type-columns (map type-column concept-types)]
    {"@context"   ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "@id"        codelist-uri
     "url"        (str csv-url)
     "dc:title"   codelist-name
     "rdfs:label" codelist-name
     "rdf:type"   {"@id" "skos:ConceptScheme"},
     "tableSchema" {"aboutUrl" code-uri,
                    "columns" (vec (concat base-columns type-columns))}}))

(defn add-code-hierarchy-fields [{:keys [parent_notation] :as row}]
  "if there is no parent notation, the current notation is a top
  concept of the scheme. This is indicated by a non-empty value in the
  top_concept_of and has_top_concept columns. The actual value is not
  significant since it is not referenced in cell URI templates."
  (let [tc (if (string/blank? parent_notation) "yes" "")]
    (assoc row :top_concept_of tc :has_top_concept tc)))

(defn add-pref-label [{:keys [label] :as row}]
  (assoc row :pref_label label))


(defn annotate-code [row]
  (-> row
      (add-code-hierarchy-fields)
      (add-pref-label)))

(defn- valid-integer? [row column s]
  (try
    (Integer/parseInt s)
    (catch NumberFormatException _ex
      (csv/throw-cell-validation-error row column (str "Invalid integer " s) {:value s}))))

(def csv-columns [{:title "Label"
                   :key :label
                   :required true}
                  {:title "Notation"
                   :key :notation
                   :validate [csv/validate-not-blank]
                   :default (fn [row] (gecu/slugize (:label row)))}
                  {:title "Parent Notation"
                   :key :parent_notation
                   :default ""}
                  {:title "Description"
                   :key :description}
                  {:title "Sort Priority"
                   :key :sort_priority
                   :validate [(csv/optional valid-integer?)]}])

(defn code-records [reader]
  (let [data (csv/read-csv-records reader csv-columns)]
    (map annotate-code data)))

(defn codelist->csvw
  "Annotates an input codelist CSV file and writes it to the specified destination file."
  [codelist-csv dest-file]
  (with-open [reader (reader codelist-csv)
              writer (io/writer dest-file)]
    (let [output-columns [:label :notation :parent_notation :sort_priority :description :top_concept_of :has_top_concept :pref_label]]
      (write-csv-rows writer output-columns (code-records reader)))))

(defn codelist-pipeline
  "Generates a codelist from a CSV file describing its members"
  [output-directory {:keys [codelist-csv codelist-name codelist-slug base-uri uri-templates]}]
  (let [metadata-file (io/file output-directory "metadata.json")
        output-csv (io/file output-directory "codelist.csv")
        uri-defs (uri-config/resolve-uri-defs (io/resource "templates/codelist-pipeline-uris.edn") uri-templates)
        uris (resolve-uris uri-defs base-uri codelist-slug)
        metadata (codelist-schema (.toURI output-csv) codelist-name uris)]
    (codelist->csvw codelist-csv output-csv)
    (util/write-json-file metadata-file metadata)
    {:metadata-file metadata-file}))

(defmethod ig/init-key :table2qb.pipelines.codelist/codelist-pipeline [_ opts]
  (assoc opts
         :table2qb/pipeline-fn codelist-pipeline
         :description (:doc (meta #'codelist-pipeline))))

(derive :table2qb.pipelines.codelist/codelist-pipeline :table2qb.pipelines/pipeline)
