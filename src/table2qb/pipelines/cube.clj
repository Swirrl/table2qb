(ns table2qb.pipelines.cube
  (:require [table2qb.util :refer [tempfile create-metadata-source] :as util]
            [table2qb.configuration :as config]
            [table2qb.csv :refer [write-csv-rows csv-records reader]]
            [clojure.java.io :as io]
            [csv2rdf.csvw :as csvw]
            [csv2rdf.util :refer [liberal-concat]]
            [clojure.data.csv :as csv]
            [table2qb.csv :as tcsv]
            [clojure.set :as set]
            [clojure.string :as string]
            [grafter.extra.cell.string :as gecs]
            [grafter.extra.cell.uri :as gecu])
  (:import [java.io File]))

(defn nil-if-blank [s] (if (= "" s) nil s))

(defn get-header-keys [header-row column-config]
  (let [title->name (fn [title] (config/title->name column-config title))]
    (mapv title->name header-row)))

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

(defn- observation-components
  [header-row column-config]
  (let [name->component (config/name->component column-config)
        title->name (fn [title] (config/title->name column-config title))
        header-names (mapv title->name header-row)
        names (set header-names)
        dimensions (set/intersection names (config/dimensions column-config))
        attributes (set/intersection names (config/attributes column-config))
        values (set/intersection names (config/values column-config))]
    (map name->component (concat dimensions attributes values))))

(defn- get-header-names
  "Resolves the titles within a CSV header row to the corresponding header names."
  [header-row column-config]
  (let [title->name (fn [title] (config/title->name column-config title))]
    (mapv (comp name title->name) header-row)))

(defn- ordered-observation-components
  "Returns a sequence of component records in the order they occur within an observations CSV file."
  [header-row column-config]
  (let [column-names (get-header-names header-row column-config)
        column-order (util/target-order column-names)
        components (observation-components header-row column-config)]
    (sort-by #(column-order (get % :name)) components)))

(defn- identify-component-names
  "Identifies the named components within an observations CSV file"
  [header-row data-rows column-config]
  (let [title->name (fn [title] (config/title->name column-config title))
        header-names (map title->name header-row)
        names (set header-names)
        dimensions (set/intersection names (config/dimensions column-config))
        attributes (set/intersection names (config/attributes column-config))
        measure-types (set/intersection names (config/measure-types column-config))]
    ;;TODO: refactor?
    (case (count measure-types)
      0 (throw (ex-info "No measure type column" {:measure-type-columns-found nil}))
      1 (let [measure-col (first measure-types)
              records (csv-records header-names data-rows)
              ;;TODO: validate all measures can be resolved
              measure-names (->> records
                                 (map measure-col)
                                 (distinct)
                                 (map title->name))]
          (concat dimensions attributes measure-names))
      (throw (ex-info "Too many measure type columns" {:measure-type-columns-found (keys measure-types)})))))

(defn component-specifications [header-row data-rows column-config]
  (let [component-names (identify-component-names header-row data-rows column-config)
        name->component (config/name->component column-config)]
    (map (fn [component-name]
           (let [{:keys [name component_attachment property_template]} (name->component component-name)]
             {:component_slug       name
              :component_attachment component_attachment
              :component_property   property_template}))
         component-names)))

(defn read-component-specifications
  "Takes an source for csv of observations and returns a sequence of components"
  [observation-source column-config]
  (with-open [r (tcsv/reader observation-source)]
    (let [lines (csv/read-csv r)]
      (vec (component-specifications (first lines) (rest lines) column-config)))))

(defn used-codes-codes-metadata [header-row csv-url domain-data dataset-slug column-config]
  (let [components (ordered-observation-components header-row column-config)
        columns (mapv (fn [comp]
                        (-> comp
                            (component->column)
                            (assoc "propertyUrl" "skos:member")
                            (suppress-value-column (config/values column-config))))
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


(defn get-ordered-dimension-names
  "Returns an ordered list of dimension names given an ordered list of components and a predicate indicating
   when the specified name corresponds to a dimension."
  [ordered-components dimension-component-p]
  (keep (fn [{comp-name :name :as component}]
          (if (dimension-component-p (keyword comp-name))
            comp-name))
        ordered-components))

(defn observations-metadata [observations-header-row csv-url domain-data dataset-slug column-config]
  (let [components (ordered-observation-components observations-header-row column-config)
        component-columns (map component->column components)
        columns (concat component-columns [observation-type-column (dataset-link-column domain-data dataset-slug)])
        dimension-names (get-ordered-dimension-names components (config/dimensions column-config))]
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

(defn- derive-dsd-label
  "Derives the DataSet Definition label from the dataset name"
  [dataset-name]
  (when-let [dataset-name (nil-if-blank dataset-name)]
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
   "dc:title" (nil-if-blank dataset-name)
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
        ds-label (nil-if-blank dataset-name)]
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

(defn transform-columns
  "Applies the specified column transforms to a row"
  [row transformations]
  (reduce (fn [row [col f]] (update row col f)) row transformations))

(defn validate-dimensions
  "Ensures that dimension columns have no missing values"
  [row dimensions]
  (doseq [[dim value] (select-keys row dimensions)]
    (if (gecs/blank? value)
      (throw (ex-info (str "Missing value for dimension: " dim)
                      {:row row})))))

(defn validate-columns [row column-config]
  "Ensures that columns are valid"
  (validate-dimensions row (config/dimensions column-config))
  row)

(defn replace-symbols [s]
  (string/replace s #"£" "GBP"))

;; TODO resolve on the basis of other component attributes? https://github.com/Swirrl/table2qb/issues/18
(def resolve-transformer
  {"slugize" gecu/slugize
   "unitize" (comp gecu/slugize replace-symbols)})


(defn identify-header-transformers
  "Identifies the columns in the CSV header which have associated transformer functions specified in the
   column configuration. Returns a map {header-key transformer-fn} for headers with transformers."
  [header-row column-config]
  (let [header-keys (get-header-keys header-row column-config)
        components (config/name->component column-config)]
    (into {} (map (fn [component-name]
                    (let [transformer-name (get-in components [component-name :value_transformation])]
                      (if-let [transform-fn (get resolve-transformer transformer-name)]
                        [component-name transform-fn])))
                  header-keys))))

(defn observation-rows [header-row data-rows column-config]
  (let [header-keys (get-header-keys header-row column-config)
        column-transforms (identify-header-transformers header-row column-config)]
    (map (fn [row]
           (-> row
               (transform-columns column-transforms)
               (validate-columns column-config)))
         (csv-records header-keys data-rows))))

(defn- components->csvw
  "Writes an intermediate components CSV file to the specified output-csv given a column configuration and input
   observations CSV file"
  [observations-csv output-csv column-config]
  (with-open [writer (io/writer output-csv)]
    (write-csv-rows writer [:component_slug :component_attachment :component_property] (read-component-specifications observations-csv column-config))))

(defn- observations->csvw
  "Writes an intermediate observations CSV file for a given column configuration to the specified location."
  [observations-csv output-csv column-config]
  (with-open [reader (reader observations-csv)
              writer (io/writer output-csv)]
    (let [csv-records (csv/read-csv reader)
          header-row (first csv-records)
          header-keys (get-header-keys header-row column-config)]
      (write-csv-rows writer header-keys (observation-rows header-row (rest csv-records) column-config)))))

(defn cube->csvw
  "Writes intermediate CSV files for component specifications and observations to component-specifications-csv and
   observations-csv respectively given a column configuration and input observations CSV file."
  [input-csv component-specifications-csv observations-csv column-config]
  (components->csvw input-csv component-specifications-csv column-config)
  (observations->csvw input-csv observations-csv column-config))

(def csv2rdf-config {:mode :standard})

(defn cube->csvw->rdf [input-csv dataset-name dataset-slug ^File component-specifications-csv observations-csv column-config base-uri]
  (cube->csvw input-csv component-specifications-csv observations-csv column-config)

  (let [domain-data (config/domain-data base-uri)
        component-specifications-uri (.toURI component-specifications-csv)
        component-specification-metadata-meta (component-specification-metadata component-specifications-uri domain-data dataset-name dataset-slug)
        dataset-metadata-meta (dataset-metadata component-specifications-uri domain-data dataset-name dataset-slug)
        dsd-metadata-meta (data-structure-definition-metadata component-specifications-uri domain-data dataset-name dataset-slug)
        observations-header-row (tcsv/read-header-row input-csv)
        observations-metadata-meta (observations-metadata observations-header-row (.toURI observations-csv) domain-data dataset-slug column-config)
        used-codes-codelists-metadata-meta (used-codes-codelists-metadata component-specifications-uri domain-data dataset-slug)
        used-codes-codes-metadata-meta (used-codes-codes-metadata observations-header-row (.toURI observations-csv) domain-data dataset-slug column-config)]
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
  (let [component-specifications-csv (tempfile "component-specifications" ".csv")
        observations-csv (tempfile "observations" ".csv")]
    (cube->csvw->rdf input-csv dataset-name dataset-slug
                     component-specifications-csv observations-csv
                     column-config base-uri)))

(derive ::cube-pipeline :table2qb.pipelines/pipeline)
