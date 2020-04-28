(ns table2qb.pipelines.cube
  (:require [table2qb.util :refer [tempfile create-metadata-source] :as util]
            [table2qb.configuration.cube :as cube-config]
            [table2qb.csv :refer [write-csv-rows reader]]
            [clojure.java.io :as io]
            [csv2rdf.util :refer [liberal-concat]]
            [clojure.string :as string]
            [table2qb.configuration.uris :as uri-config]
            [table2qb.configuration.column :as column]
            [table2qb.configuration.csvw :refer [csv2rdf-config]]
            [integrant.core :as ig])
  (:import [java.io File]))

(defn suppress-value-column
  "Suppresses the output of a metadata column definition if it corresponds to a value component"
  [column is-value-p]
  (if (is-value-p (keyword (get column "name")))
    (assoc column "suppressOutput" true)
    column))

(defn component->column [{:keys [name property_template value_template datatype]}]
  (let [col {"name" name
             "titles" name ;; could revert to title here (would need to do so in all output csv too)
             "datatype" datatype
             "propertyUrl" property_template}]
    (if (some? value_template)
      (assoc col "valueUrl" value_template)
      col)))

(defn used-codes-codes-schema [csv-url domain-data dataset-slug cube-config]
  (let [components (cube-config/ordered-columns cube-config)
        columns (mapv (fn [comp]
                        (-> comp
                            (component->column)
                            (assoc "propertyUrl" "skos:member")
                            (suppress-value-column (cube-config/values cube-config))))
                      components)
        codelist-uri (str domain-data dataset-slug "/codes-used/{_name}")]
    {"url" (str csv-url)
     "tableSchema" {"columns" columns
                    "aboutUrl" codelist-uri}}))

(defn dataset-link-column [domain-data dataset-slug]
  (let [ds-uri (str domain-data dataset-slug)]
    {"name" "DataSet"
     "virtual" true
     "propertyUrl" "qb:dataSet"
     "valueUrl" ds-uri}))

(def observation-type-column
  {"name" "Observation"
   "virtual" true
   "propertyUrl" "rdf:type"
   "valueUrl" "qb:Observation"})

(defn observation-template
  "Builds an observation URI template from a domain data prefix, dataset slug, sequence of dimension names."
  [domain-data-prefix dataset-slug dimension-names]
  (let [uri-parts (->> dimension-names
                       (map #(str "/{+" % "}")))]
    (str domain-data-prefix dataset-slug (string/join uri-parts))))

(defn observations-schema [csv-url domain-data dataset-slug cube-config]
  (let [components (cube-config/ordered-columns cube-config)
        component-columns (map component->column components)
        columns (concat component-columns [observation-type-column (dataset-link-column domain-data dataset-slug)])
        dimension-names (cube-config/get-ordered-dimension-names cube-config)]
    {"url" (str csv-url)
     "tableSchema"
                {"columns" (vec columns)
                 "aboutUrl" (observation-template domain-data dataset-slug dimension-names)}}))

(defn used-codes-codelists-schema [csv-url domain-data dataset-slug]
  (let [codelist-uri (str domain-data dataset-slug "/codes-used/{component_slug}")]
    {"url" (str csv-url)
     "tableSchema"
     {"columns"
      [{"name" "component_slug",
        "titles" "component_slug",
        "datatype" "string",
        "suppressOutput" true}
       {"name" "component_attachment",
        "titles" "component_attachment",
        "datatype" "string",
        "suppressOutput" true}
       {"name" "component_property",
        "titles" "component_property",
        "datatype" "string",
        "suppressOutput" true}
       {"name" "type",
        "virtual" true,
        "propertyUrl" "rdf:type",
        "valueUrl" "skos:Collection"}],
      "aboutUrl" codelist-uri}}))

(defn component-specification-template [domain-data dataset-slug]
  (str domain-data dataset-slug "/component/{component_slug}"))

(defn derive-dsd-label
  "Derives the DataSet Definition label from the dataset name"
  [dataset-name]
  (when-let [dataset-name (util/blank->nil dataset-name)]
    (str dataset-name " (Data Structure Definition)")))

(defn data-structure-definition-schema [csv-url domain-data dataset-name dataset-slug]
  (let [dsd-uri (str domain-data dataset-slug "/structure")
        dsd-label (derive-dsd-label dataset-name)]
    {"@id" dsd-uri,
     "url" (str csv-url)
     "dc:title" dsd-label,
     "rdf:type" {"@id" "qb:DataStructureDefinition"},
     "rdfs:label" dsd-label,
     "tableSchema"
     {"columns"
                 [{"name" "component_slug",
                   "titles" "component_slug",
                   "datatype" "string",
                   "propertyUrl" "qb:component",
                   "valueUrl" (component-specification-template domain-data dataset-slug)}
                  {"name" "component_attachment",
                   "titles" "component_attachment",
                   "datatype" "string",
                   "suppressOutput" true}
                  {"name" "component_property",
                   "titles" "component_property",
                   "datatype" "string",
                   "suppressOutput" true}],
      "aboutUrl" dsd-uri}}))

(defn component-specification-schema [csv-url domain-data dataset-name dataset-slug]
  {"url" (str csv-url)
   "dc:title" (util/blank->nil dataset-name)
   "tableSchema"
              {"columns"
                          [{"name" "component_slug",
                            "titles" "component_slug",
                            "datatype" "string",
                            "suppressOutput" true}
                           {"name" "component_attachment",
                            "titles" "component_attachment",
                            "datatype" "string",
                            "suppressOutput" true}
                           {"name" "component_property",
                            "titles" "component_property",
                            "datatype" "string",
                            "propertyUrl" "{+component_attachment}",
                            "valueUrl" "{+component_property}"}
                           {"name" "type",
                            "virtual" true,
                            "propertyUrl" "rdf:type",
                            "valueUrl" "qb:ComponentSpecification"}
                           {"name" "codes_used",
                            "virtual" true,
                            "propertyUrl" "http://publishmydata.com/def/qb/codesUsed",
                            "valueUrl" (str domain-data dataset-slug "/codes-used/{component_slug}")}],
               "aboutUrl" (component-specification-template domain-data dataset-slug)}})

(defn dataset-schema [csv-url domain-data dataset-name dataset-slug]
  (let [ds-uri (str domain-data dataset-slug)
        dsd-uri (str ds-uri "/structure")
        ds-label (util/blank->nil dataset-name)]
    {"@id" ds-uri,
     "url" (str csv-url)
     "dc:title" ds-label
     "rdfs:label" ds-label
     "tableSchema"
     {"columns"
                 [{"name" "component_slug", "titles" "component_slug", "suppressOutput" true}
                  {"name" "component_attachment", "titles" "component_attachment", "suppressOutput" true}
                  {"name" "component_property", "titles" "component_property", "suppressOutput" true}
                  {"name" "type","virtual" true,"propertyUrl" "rdf:type","valueUrl" "qb:DataSet"}
                  {"name" "structure","virtual" true,"propertyUrl" "qb:structure","valueUrl" dsd-uri}],
      "aboutUrl" ds-uri}}))

(defn component-specification-records [cube-config]
  (map (fn [column]
         {:component_slug (column/column-name column)
          :component_attachment (column/component-attachment column)
          :component_property (column/property-template column)})
       (cube-config/dimension-attribute-measure-columns cube-config)))

(defn- components->csvw
  "Writes an intermediate components CSV file to the specified output-csv given a column configuration and input
   observations CSV file"
  [output-csv cube-config]
  (with-open [writer (io/writer output-csv)]
    (write-csv-rows writer [:component_slug :component_attachment :component_property] (component-specification-records cube-config))))

(defn- observations->csvw
  "Writes an intermediate observations CSV file for a given column configuration to the specified location."
  [observations-csv output-csv cube-config]
  (with-open [reader (reader observations-csv)
              writer (io/writer output-csv)]
    (cube-config/write-observation-records writer (cube-config/observation-records reader cube-config) cube-config)))

(defn cube-pipeline
  "Generates cube RDF for the given input CSV with dataset name and slug."
  [output-directory {:keys [input-csv dataset-name dataset-slug column-config base-uri]}]
  (let [cube-config (cube-config/get-cube-configuration input-csv column-config)
        domain-data (uri-config/domain-data base-uri)
        metadata-file (io/file output-directory (str dataset-slug "-metadata.json"))
        component-specifications-csv (io/file output-directory "component-specifications.csv")
        component-specifications-url (util/csvw-url output-directory "component-specifications.csv")
        observations-csv (io/file output-directory "observations.csv")
        observations-url (util/csvw-url output-directory "observations.csv")]
    ;;write csv files
    (components->csvw component-specifications-csv cube-config)
    (observations->csvw input-csv observations-csv cube-config)

    (util/write-json-file
      metadata-file
      {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}]
       "tables" [(dataset-schema component-specifications-url domain-data dataset-name dataset-slug)
                 (data-structure-definition-schema component-specifications-url domain-data dataset-name dataset-slug)
                 (component-specification-schema component-specifications-url domain-data dataset-name dataset-slug)
                 (used-codes-codelists-schema component-specifications-url domain-data dataset-slug)
                 (used-codes-codes-schema observations-url domain-data dataset-slug cube-config)
                 (observations-schema observations-url domain-data dataset-slug cube-config)]})

    {:metadata-file metadata-file}))

(defmethod ig/init-key :table2qb.pipelines.cube/cube-pipeline [_ opts]
  (assoc opts
         :table2qb/pipeline-fn cube-pipeline
         :description (:doc (meta #'cube-pipeline))))

(derive :table2qb.pipelines.cube/cube-pipeline :table2qb.pipelines/pipeline)
