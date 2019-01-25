(ns table2qb.pipelines.cube
  (:require [table2qb.util :refer [tempfile create-metadata-source] :as util]
            [table2qb.configuration.cube :as cube-config]
            [table2qb.csv :refer [write-csv-rows csv-records reader]]
            [clojure.java.io :as io]
            [csv2rdf.csvw :as csvw]
            [csv2rdf.util :refer [liberal-concat]]
            [clojure.string :as string]
            [table2qb.configuration.uris :as uri-config]
            [table2qb.configuration.column :as column]
            [table2qb.configuration.csvw :refer [csv2rdf-config]])
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

(defn used-codes-codes-metadata [csv-url domain-data dataset-slug cube-config]
  (let [components (cube-config/ordered-columns cube-config)
        columns (mapv (fn [comp]
                        (-> comp
                            (component->column)
                            (assoc "propertyUrl" "skos:member")
                            (suppress-value-column (cube-config/values cube-config))))
                      components)
        codelist-uri (str domain-data dataset-slug "/codes-used/{_name}")]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "url" (str csv-url)
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

(defn observations-metadata [csv-url domain-data dataset-slug cube-config]
  (let [components (cube-config/ordered-columns cube-config)
        component-columns (map component->column components)
        columns (concat component-columns [observation-type-column (dataset-link-column domain-data dataset-slug)])
        dimension-names (cube-config/get-ordered-dimension-names cube-config)]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "url" (str csv-url)
     "tableSchema"
                {"columns" (vec columns)
                 "aboutUrl" (observation-template domain-data dataset-slug dimension-names)}}))

(defn used-codes-codelists-metadata [csv-url domain-data dataset-slug]
  (let [codelist-uri (str domain-data dataset-slug "/codes-used/{component_slug}")]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "url" (str csv-url)
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

(defn data-structure-definition-metadata [csv-url domain-data dataset-name dataset-slug]
  (let [dsd-uri (str domain-data dataset-slug "/structure")
        dsd-label (derive-dsd-label dataset-name)]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "@id" dsd-uri,
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

(defn component-specification-metadata [csv-url domain-data dataset-name dataset-slug]
  {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
   "url" (str csv-url)
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

(defn dataset-metadata [csv-url domain-data dataset-name dataset-slug]
  (let [ds-uri (str domain-data dataset-slug)
        dsd-uri (str ds-uri "/structure")
        ds-label (util/blank->nil dataset-name)]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "@id" ds-uri,
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

(defn read-component-specifications [cube-config]
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
    (write-csv-rows writer [:component_slug :component_attachment :component_property] (read-component-specifications cube-config))))

(defn- observations->csvw
  "Writes an intermediate observations CSV file for a given column configuration to the specified location."
  [observations-csv output-csv cube-config]
  (with-open [reader (reader observations-csv)
              writer (io/writer output-csv)]
    (cube-config/write-observation-records writer (cube-config/observation-records reader cube-config) cube-config)))

(defn cube->csvw
  "Writes intermediate CSV files for component specifications and observations to component-specifications-csv and
   observations-csv respectively given a column configuration and input observations CSV file."
  [input-csv component-specifications-csv observations-csv cube-config]
  (components->csvw component-specifications-csv cube-config)
  (observations->csvw input-csv observations-csv cube-config))

(defn cube->csvw->rdf [input-csv dataset-name dataset-slug ^File component-specifications-csv observations-csv cube-config base-uri]
  (cube->csvw input-csv component-specifications-csv observations-csv cube-config)

  (let [domain-data (uri-config/domain-data base-uri)
        component-specifications-uri (.toURI component-specifications-csv)
        component-specification-metadata-meta (component-specification-metadata component-specifications-uri domain-data dataset-name dataset-slug)
        dataset-metadata-meta (dataset-metadata component-specifications-uri domain-data dataset-name dataset-slug)
        dsd-metadata-meta (data-structure-definition-metadata component-specifications-uri domain-data dataset-name dataset-slug)
        observations-metadata-meta (observations-metadata (.toURI observations-csv) domain-data dataset-slug cube-config)
        used-codes-codelists-metadata-meta (used-codes-codelists-metadata component-specifications-uri domain-data dataset-slug)
        used-codes-codes-metadata-meta (used-codes-codes-metadata (.toURI observations-csv) domain-data dataset-slug cube-config)]
    (liberal-concat
      (csvw/csv->rdf component-specifications-csv (create-metadata-source input-csv component-specification-metadata-meta) csv2rdf-config)
      (csvw/csv->rdf component-specifications-csv (create-metadata-source input-csv dataset-metadata-meta) csv2rdf-config)
      (csvw/csv->rdf component-specifications-csv (create-metadata-source input-csv dsd-metadata-meta) csv2rdf-config)
      (csvw/csv->rdf observations-csv (create-metadata-source input-csv observations-metadata-meta) csv2rdf-config)
      (csvw/csv->rdf component-specifications-csv (create-metadata-source input-csv used-codes-codelists-metadata-meta) csv2rdf-config)
      (csvw/csv->rdf observations-csv (create-metadata-source input-csv used-codes-codes-metadata-meta) csv2rdf-config))))

(defn cube-pipeline
  "Generates cube RDF for the given input CSV with dataset name and slug."
  [input-csv dataset-name dataset-slug column-config base-uri]
  (let [cube-config (cube-config/get-cube-configuration input-csv column-config)
        component-specifications-csv (tempfile "component-specifications" ".csv")
        observations-csv (tempfile "observations" ".csv")]
    (cube->csvw->rdf input-csv dataset-name dataset-slug
                     component-specifications-csv observations-csv
                     cube-config base-uri)))

(derive ::cube-pipeline :table2qb.pipelines/pipeline)
