(ns table2qb.pipelines.codelist
  (:require [table2qb.configuration.uris :as uri-config]
            [csv2rdf.csvw :as csvw]
            [clojure.java.io :as io]
            [table2qb.csv :refer [write-csv-rows read-csv reader]]
            [table2qb.util :refer [create-metadata-source tempfile]]
            [clojure.string :as string])
  (:import [java.io File]))

(defn codelist-metadata [csv-url domain-def codelist-name codelist-slug]
  (let [codelist-uri (str domain-def "concept-scheme/" codelist-slug)
        code-uri (str domain-def "concept/" codelist-slug "/{notation}")
        parent-uri (str domain-def "concept/" codelist-slug "/{parent_notation}")]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "@id" codelist-uri,
     "url" (str csv-url)
     "dc:title" codelist-name,
     "rdfs:label" codelist-name,
     "rdf:type" {"@id" "skos:ConceptScheme"},
     "tableSchema"
     {"aboutUrl" code-uri,
      "columns"
      [{"name" "label",
        "titles" "label",
        "datatype" "string",
        "propertyUrl" "rdfs:label"}
       {"name" "notation",
        "titles" "notation",
        "datatype" "string",
        "propertyUrl" "skos:notation"}
       {"name" "parent_notation",
        "titles" "parent_notation",
        "datatype" "string",
        "propertyUrl" "skos:broader",
        "valueUrl" parent-uri}
       {"name" "sort_priority"
        "titles" "sort_priority"
        "datatype" "integer"
        "propertyUrl" "http://www.w3.org/ns/ui#sortPriority"}
       {"name" "description"
        "titles" "description"
        "datatype" "string"
        "propertyUrl" "rdfs:comment"}
       {"name" "top_concept_of"
        "titles" "top_concept_of"
        "propertyUrl" "skos:topConceptOf"
        "valueUrl" codelist-uri}
       {"name" "has_top_concept"
        "titles" "has_top_concept"
        "aboutUrl" codelist-uri
        "propertyUrl" "skos:hasTopConcept"
        "valueUrl" code-uri}
       {"name" "pref_label"
        "titles" "pref_label"
        "propertyUrl" "skos:prefLabel"}
       {"propertyUrl" "skos:inScheme",
        "valueUrl" codelist-uri,
        "virtual" true}
       {"propertyUrl" "skos:member",
        "aboutUrl" codelist-uri,
        "valueUrl" code-uri,
        "virtual" true}
       {"propertyUrl" "rdf:type"
        "valueUrl" "skos:Concept"
        "virtual" true}]}}))

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

(defn codes [reader]
  (let [data (read-csv reader {"Label" :label
                               "Notation" :notation
                               "Parent Notation", :parent_notation
                               "Sort Priority", :sort_priority
                               "Description" :description})]
    (map annotate-code data)))

(defn codelist->csvw
  "Annotates an input codelist CSV file and writes it to the specified destination file."
  [codelist-csv dest-file]
  (with-open [reader (reader codelist-csv)
              writer (io/writer dest-file)]
    (let [output-columns [:label :notation :parent_notation :sort_priority :description :top_concept_of :has_top_concept :pref_label]]
      (write-csv-rows writer output-columns (codes reader)))))

;;TODO: merge CSV2RDF configs
(def csv2rdf-config {:mode :standard})

(defn codelist->csvw->rdf
  "Annotates an input codelist CSV file and uses it to generate RDF for the given codelist name and slug."
  [codelist-csv domain-def codelist-name codelist-slug ^File intermediate-file]
  (codelist->csvw codelist-csv intermediate-file)
  (let [codelist-meta (codelist-metadata (.toURI intermediate-file) domain-def codelist-name codelist-slug)]
    (csvw/csv->rdf intermediate-file (create-metadata-source codelist-csv codelist-meta) csv2rdf-config)))

(defn codelist-pipeline
  "Generates RDF for the given codelist CSV file"
  [codelist-csv codelist-name codelist-slug base-uri]
  (let [domain-def (uri-config/domain-def base-uri)
        intermediate-file (tempfile codelist-slug ".csv")]
    (codelist->csvw->rdf codelist-csv domain-def codelist-name codelist-slug intermediate-file)))

(derive ::codelist-pipeline :table2qb.pipelines/pipeline)
