(ns table2qb.pipelines.components
  (:require [table2qb.util :refer [tempfile create-metadata-source]]
            [csv2rdf.csvw :as csvw]
            [clojure.java.io :as io]
            [table2qb.csv :refer [write-csv-rows read-csv reader]]
            [grafter.extra.cell.uri :as gecu]
            [table2qb.configuration.uris :as uri-config]
            [table2qb.configuration.csvw :refer [csv2rdf-config]])
  (:import [java.io File]))

(defn components-metadata [csv-url domain-def]
  (let [ontology-uri (str domain-def "ontology/components")]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "@id" ontology-uri,
     "url" (str csv-url)
     "dc:title" "Components Ontology",
     "rdfs:label" "Components Ontology",
     "rdf:type" {"@id" "owl:Ontology"},
     "tableSchema"
     {"columns"
                 [{"name" "label",
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
                   "valueUrl" (str domain-def "{class_slug}")}
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
      "aboutUrl" (str domain-def "{component_type_slug}/{notation}")}}))

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

(defn components->csvw->rdf
  "Annotates an input components CSV file and uses it to generate RDF."
  [components-csv domain-def ^File intermediate-file]
  (components->csvw components-csv intermediate-file)
  (let [components-meta (components-metadata (.toURI intermediate-file) domain-def)]
    (csvw/csv->rdf intermediate-file (create-metadata-source components-csv components-meta) csv2rdf-config)))

(defn components-pipeline
  "Generates RDF for the given components CSV file."
  [input-csv base-uri]
  (let [domain-def (uri-config/domain-def base-uri)
        components-csv (tempfile "components" ".csv")]
    (components->csvw->rdf input-csv domain-def components-csv)))

(derive ::components-pipeline :table2qb.pipelines/pipeline)
