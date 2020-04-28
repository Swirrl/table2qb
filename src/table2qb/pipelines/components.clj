(ns table2qb.pipelines.components
  (:require [table2qb.util :refer [tempfile create-metadata-source] :as util]
            [csv2rdf.csvw :as csvw]
            [clojure.java.io :as io]
            [table2qb.csv :refer [write-csv-rows reader]]
            [grafter.extra.cell.uri :as gecu]
            [table2qb.configuration.uris :as uri-config]
            [table2qb.csv :as csv]
            [table2qb.configuration.csvw :refer [csv2rdf-config]]
            [integrant.core :as ig])
  (:import [java.io File]))

(defn- resolve-uris [uri-defs base-uri]
  (uri-config/expand-uris uri-defs {:base-uri (uri-config/strip-trailing-path-separator base-uri)}))

(defn get-uris [base-uri]
  (let [uri-map (util/read-edn (io/resource "uris/components-pipeline-uris.edn"))]
    (resolve-uris uri-map base-uri)))

(defn components-schema [csv-url {:keys [ontology-uri component-uri component-class-uri] :as uris}]
  {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
   "@id" ontology-uri,
   "url" (str csv-url)
   "dc:title" "Components Ontology",
   "rdfs:label" "Components Ontology",
   "rdf:type" {"@id" "owl:Ontology"},
   "tableSchema"
   {"columns" [{"name" "label",
                "titles" "label",
                "datatype" "string",
                "propertyUrl" "rdfs:label"}
               {"name" "description",
                "titles" "description",
                "datatype" "string",
                "propertyUrl" "dc:description"}
               {"name" "component_type",
                "titles" "component_type",
                "propertyUrl" "rdf:type",
                "valueUrl" "{+component_type}"}
               {"name" "codelist",
                "titles" "codelist",
                "datatype" "string",
                "propertyUrl" "qb:codeList",
                "valueUrl" "{+codelist}"}
               {"name" "notation",
                "titles" "notation",
                "datatype" "string",
                "propertyUrl" "skos:notation"}
               {"name" "component_type_slug",
                "titles" "component_type_slug",
                "datatype" "string",
                "suppressOutput" true}
               {"name" "property_slug",
                "titles" "property_slug",
                "datatype" "string",
                "suppressOutput" true}
               {"name" "class_slug",
                "titles" "class_slug",
                "datatype" "string",
                "propertyUrl" "rdfs:range",
                "valueUrl" component-class-uri}
               {"name" "parent_property",
                "titles" "parent_property",
                "datatype" "string",
                "propertyUrl" "rdfs:subPropertyOf",
                "valueUrl" "{+parent_property}"}
               {"propertyUrl" "rdfs:isDefinedBy",
                "virtual" true,
                "valueUrl" ontology-uri}
               {"propertyUrl" "rdf:type",
                "virtual" true,
                "valueUrl" "rdf:Property"}],
    "aboutUrl" component-uri}})

(def component-type-mapping {"Dimension" "qb:DimensionProperty"
                             "Measure"   "qb:MeasureProperty"
                             "Attribute" "qb:AttributeProperty"})

(defn annotate-component
  "Derives extra column data for a component row"
  [{:keys [label component_type] :as row}]
  (-> row
      (assoc :notation (gecu/slugize label))
      (assoc :component_type_slug ({"Dimension" "dimension"
                                    "Measure" "measure"
                                    "Attribute" "attribute"}
                                    component_type))
      (update :component_type component-type-mapping)
      (assoc :property_slug (gecu/propertize label))
      (assoc :class_slug (gecu/classize label))
      (assoc :parent_property (if (= "Measure" component_type)
                                "http://purl.org/linked-data/sdmx/2009/measure#obsValue"))))

(def csv-columns [{:title    "Label"
                   :key      :label
                   :required true
                   :validate [csv/validate-not-blank]}
                  {:title "Description"
                   :key :description}
                  {:title "Component Type"
                   :key :component_type
                   :required true
                   :validate [(csv/validate-one-of (set (keys component-type-mapping)))]}
                  {:title "Codelist"
                   :key :codelist}])

(defn component-records [reader]
  (let [data (csv/read-csv-records reader csv-columns)]
    (map annotate-component data)))

(defn- components->csvw
  "Annotates an input component CSV file and writes the result to the specified destination file."
  [components-csv dest-file]
  (with-open [reader (reader components-csv)
              writer (io/writer dest-file)]
    (let [component-columns [:label :description :component_type :codelist :notation :component_type_slug :property_slug :class_slug :parent_property]]
      (write-csv-rows writer component-columns (component-records reader)))))

(defn components-pipeline
  "Generates component specifications."
  [output-dir {:keys [input-csv base-uri uris-file]}]
  (let [components-csv (io/file output-dir "components.csv")
        metadata-file (io/file output-dir "metadata.json")
        uri-defs (uri-config/resolve-uri-defs (io/resource "uris/components-pipeline-uris.edn") uris-file)
        uris (resolve-uris uri-defs base-uri)]
    (components->csvw input-csv components-csv)
    (util/write-json-file metadata-file (components-schema (.toURI components-csv) uris))
    {:metadata-file metadata-file}))

(defmethod ig/init-key :table2qb.pipelines.components/components-pipeline [_ opts]
  (assoc opts
         :table2qb/pipeline-fn components-pipeline
         :description (:doc (meta #'components-pipeline))))

(derive :table2qb.pipelines.components/components-pipeline :table2qb.pipelines/pipeline)
