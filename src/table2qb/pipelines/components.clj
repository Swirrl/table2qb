(ns table2qb.pipelines.components
  (:require [table2qb.util :refer [tempfile create-metadata-source] :as util]
            [csv2rdf.csvw :as csvw]
            [clojure.java.io :as io]
            [table2qb.csv :refer [write-csv-rows read-csv reader]]
            [grafter.extra.cell.uri :as gecu]
            [table2qb.configuration.uris :as uri-config])
  (:import [java.io File]))

(defn- resolve-uris [uri-defs base-uri]
  (uri-config/expand-uris uri-defs {:base-uri (uri-config/strip-trailing-path-separator base-uri)}))

(defn get-uris [base-uri]
  (let [uri-map (util/read-edn (io/resource "uris/components-pipeline-uris.edn"))]
    (resolve-uris uri-map base-uri)))

(defn components-metadata [csv-url {:keys [ontology-uri component-uri component-class-uri] :as uris}]
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

(defn annotate-component
  "Derives extra column data for a component row"
  [{:keys [label component_type] :as row}]
  (-> row
      (assoc :notation (gecu/slugize label))
      (assoc :component_type_slug ({"Dimension" "dimension"
                                    "Measure" "measure"
                                    "Attribute" "attribute"}
                                    component_type))
      (assoc :property_slug (gecu/propertize label))
      (assoc :class_slug (gecu/classize label))
      (update :component_type {"Dimension" "qb:DimensionProperty"
                               "Measure" "qb:MeasureProperty"
                               "Attribute" "qb:AttributeProperty"})
      (assoc :parent_property (if (= "Measure" component_type)
                                "http://purl.org/linked-data/sdmx/2009/measure#obsValue"))))

(defn components [reader]
  (let [data (read-csv reader {"Label" :label
                               "Description" :description
                               "Component Type" :component_type
                               "Codelist" :codelist})]
    (map annotate-component data)))


(defn components->csvw
  "Annotates an input component CSV file and writes the result to the specified destination file."
  [components-csv dest-file]
  (with-open [reader (reader components-csv)
              writer (io/writer dest-file)]
    (let [component-columns [:label :description :component_type :codelist :notation :component_type_slug :property_slug :class_slug :parent_property]]
      (write-csv-rows writer component-columns (components reader)))))

;;TODO: merge CSV2RDF configs
(def csv2rdf-config {:mode :standard})

(defn components->csvw->rdf
  "Annotates an input components CSV file and uses it to generate RDF."
  [components-csv ^File intermediate-file uris]
  (components->csvw components-csv intermediate-file)
  (let [components-meta (components-metadata (.toURI intermediate-file) uris)]
    (csvw/csv->rdf intermediate-file (create-metadata-source components-csv components-meta) csv2rdf-config)))

(defn components-pipeline
  "Generates RDF for the given components CSV file."
  ([input-csv base-uri] (components-pipeline input-csv base-uri nil))
  ([input-csv base-uri uris-file]
   (let [components-csv (tempfile "components" ".csv")
         uri-defs (uri-config/resolve-uri-defs (io/resource "uris/components-pipeline-uris.edn") uris-file)
         uris (resolve-uris uri-defs base-uri)]
     (components->csvw->rdf input-csv components-csv uris))))

(derive ::components-pipeline :table2qb.pipelines/pipeline)
