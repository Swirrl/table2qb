(ns table2qb.core
  (:require [clojure.data.csv :as csv]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [grafter.extra.cell.uri :as gecu]
            [grafter.extra.cell.string :as gecs]
            [clojure.string :as st]
            [clojure.java.shell :refer [sh]]
            [csv2rdf.csvw :as csvw]
            [csv2rdf.util :refer [liberal-concat]]
            [csv2rdf.source :as source]
            [clojure.string :as string]
            [table2qb.util :refer [exception? map-values] :as util]
            [table2qb.csv :refer :all]
            [table2qb.configuration :as config]
            [clojure.set :as set]
            [integrant.core :as ig]))

;; JSON handling
(def read-json json/read)
(defn write-json [writer data]
  (json/write data writer))

;; Identifying Components

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
              measure-names (->> records
                                 (map measure-col)
                                 (distinct)
                                 (map title->name))]
          (concat dimensions attributes measure-names))
      (throw (ex-info "Too many measure type columns" {:measure-type-columns-found (keys measure-types)})))))

(defn component-specifications
  "Takes an filename for csv of observations and returns a sequence of components"
  [reader column-config]
  (let [lines (csv/read-csv reader)
        component-names (identify-component-names (first lines) (rest lines) column-config)
        name->component (config/name->component column-config)]
    (map (fn [component-name]
           (let [{:keys [name component_attachment property_template]} (name->component component-name)]
             {:component_slug       name
              :component_attachment component_attachment
              :component_property   property_template}))
         component-names)))

(defn component-specification-template [domain-data dataset-slug]
  (str domain-data dataset-slug "/component/{component_slug}"))

(defn component-specification-metadata [csv-url domain-data dataset-name dataset-slug]
  {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
   "url" (str csv-url)
   "dc:title" dataset-name,
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
        ds-label dataset-name]
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

(defn data-structure-definition-metadata [csv-url domain-data dataset-name dataset-slug]
  (let [dsd-uri (str domain-data dataset-slug "/structure")
        dsd-label (str dataset-name " (Data Structure Definition)")]
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

(defn replace-symbols [s]
  (st/replace s #"£" "GBP"))

 ;; TODO resolve on the basis of other component attributes? https://github.com/Swirrl/table2qb/issues/18
(def resolve-transformer
  {"slugize" gecu/slugize
   "unitize" (comp gecu/slugize replace-symbols)})

(defn- get-header-names
  "Resolves the titles within a CSV header row to the corresponding header names."
  [header-row column-config]
  (let [title->name (fn [title] (config/title->name column-config title))]
    (mapv (comp name title->name) header-row)))

(defn get-header-keys [header-row column-config]
  (let [title->name (fn [title] (config/title->name column-config title))]
    (mapv title->name header-row)))

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

(defn observation-rows [header-row data-rows column-config]
  (let [header-keys (get-header-keys header-row column-config)
        column-transforms (identify-header-transformers header-row column-config)]
    (map (fn [row]
           (-> row
               (transform-columns column-transforms)
               (validate-columns column-config)))
         (csv-records header-keys data-rows))))

(defn observations
  [reader column-config]
  (let [lines (csv/read-csv reader)]
    (observation-rows (first lines) (rest lines) column-config)))

(defn component->column [{:keys [name property_template value_template datatype]}]
  (let [col {"name" name
             "titles" name ;; could revert to title here (would need to do so in all output csv too)
             "datatype" datatype
             "propertyUrl" property_template}]
    (if (some? value_template)
      (assoc col "valueUrl" value_template)
      col)))

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
  "Builds an observation URI template from a domain data prefix, dataset slug, sequence of observation component
   names and a predicate for identifying value components."
  [dataset-slug component-names domain-data-prefix is-value-component-p]
  (let [uri-parts (->> component-names
                       (remove #(is-value-component-p (keyword %)))
                       (map #(str "/{+" % "}")))]
    (str domain-data-prefix dataset-slug (st/join uri-parts))))

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

(defn- ordered-observation-components
  "Returns a sequence of component records in the order they occur within an observations CSV file."
  [reader column-config]
  (let [csv-records (csv/read-csv reader)
        header-row (first csv-records)
        column-names (get-header-names header-row column-config)
        column-order (util/target-order column-names)
        components (observation-components header-row column-config)]
    (sort-by #(column-order (get % :name)) components)))

(defn observations-metadata [reader csv-url domain-data dataset-slug column-config]
  (let [components (ordered-observation-components reader column-config)
        component-columns (sequence (map component->column) components)
        columns (concat component-columns [observation-type-column (dataset-link-column domain-data dataset-slug)])]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "url" (str csv-url)
     "tableSchema"
     {"columns" (vec columns)
      "aboutUrl" (observation-template dataset-slug (map :name components) domain-data (config/values column-config))}}))

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

(defn suppress-value-column
  "Suppresses the output of a metadata column definition if it corresponds to a value component"
  [column is-value-p]
  (if (is-value-p (keyword (get column "name")))
    (assoc column "suppressOutput" true)
    column))

(defn used-codes-codes-metadata [reader csv-url domain-data dataset-slug column-config]
  (let [components (ordered-observation-components reader column-config)
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
      "aboutUrl" (str domain-def "{component_type_slug}/{notation}")}})) ;; property-slug?

(defn add-code-hierarchy-fields [{:keys [parent_notation] :as row}]
  "if there is no parent notation, the current notation is a top
  concept of the scheme. This is indicated by a non-empty value in the
  top_concept_of and has_top_concept columns. The actual value is not
  significant since it is not referenced in cell URI templates."
  (let [tc (if (string/blank? parent_notation) "yes" "")]
    (assoc row :top_concept_of tc :has_top_concept tc)))

(def annotate-code add-code-hierarchy-fields)

(defn codes [reader]
  (let [data (read-csv reader {"Label" :label
                               "Notation" :notation
                               "Parent Notation", :parent_notation
                               "Sort Priority", :sort_priority
                               "Description" :description})]
    (map annotate-code data)))

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
       {"propertyUrl" "skos:inScheme",
        "valueUrl" codelist-uri,
        "virtual" true}
       {"propertyUrl" "skos:member",
        "aboutUrl" codelist-uri,
        "valueUrl" code-uri,
        "virtual" true}
       {"propertyUrl" "skos:prefLabel",
        "value" "{label}",
        "virtual" true}]}}))

;; pipelines

(defn csv-file->metadata-uri [csv-file]
  (.resolve (.toURI csv-file) "meta.json"))

(defn create-metadata-source [csv-file-str metadata-json]
  (let [meta-uri (csv-file->metadata-uri (io/file csv-file-str))]
    (source/->MapMetadataSource meta-uri metadata-json)))

(defn tempfile [filename extension]
  (java.io.File/createTempFile filename extension))

(def csv2rdf-config {:mode :standard})

(defn codelist->csvw
  "Annotates an input codelist CSV file and writes it to the specified destination file."
  [codelist-csv dest-file]
  (with-open [reader (io/reader codelist-csv)
              writer (io/writer dest-file)]
    (let [output-columns [:label :notation :parent_notation :sort_priority :description :top_concept_of :has_top_concept]]
      (write-csv-rows writer output-columns (codes reader)))))

(defn codelist->csvw->rdf
  "Annotates an input codelist CSV file and uses it to generate RDF for the given codelist name and slug."
  [codelist-csv domain-def codelist-name codelist-slug intermediate-file]
  (codelist->csvw codelist-csv intermediate-file)
  (let [codelist-meta (codelist-metadata intermediate-file domain-def codelist-name codelist-slug)]
    (csvw/csv->rdf intermediate-file (create-metadata-source codelist-csv codelist-meta) csv2rdf-config)))

(defn codelist-pipeline
  "Generates RDF for the given codelist CSV file"
  [codelist-csv codelist-name codelist-slug column-config]
  (let [domain-def (config/domain-def column-config)
        intermediate-file (tempfile codelist-slug ".csv")]
    (codelist->csvw->rdf codelist-csv domain-def codelist-name codelist-slug intermediate-file)))

(defn components->csvw
  "Annotates an input component CSV file and writes the result to the specified destination file."
  [components-csv dest-file]
  (with-open [reader (io/reader components-csv)
              writer (io/writer dest-file)]
    (let [component-columns [:label :description :component_type :codelist :notation :component_type_slug :property_slug :class_slug :parent_property]]
      (write-csv-rows writer component-columns (components reader)))))

(defn components->csvw->rdf
  "Annotates an input components CSV file and uses it to generate RDF."
  [components-csv domain-def intermediate-file]
  (components->csvw components-csv intermediate-file)
  (let [components-meta (components-metadata intermediate-file domain-def)]
    (csvw/csv->rdf intermediate-file (create-metadata-source components-csv components-meta) csv2rdf-config)))

(defn components-pipeline
  "Generates RDF for the given components CSV file."
  [input-csv column-config]
  (let [domain-def (config/domain-def column-config)
        components-csv (tempfile "components" ".csv")]
    (components->csvw->rdf input-csv domain-def components-csv)))

(defn cube->csvw [input-csv component-specifications-csv observations-csv column-config]
  (with-open [reader (io/reader input-csv)
              writer (io/writer component-specifications-csv)]
    (write-csv-rows writer [:component_slug :component_attachment :component_property] (component-specifications reader column-config)))

  (with-open [reader (io/reader input-csv)
              writer (io/writer observations-csv)]
    (let [csv-records (csv/read-csv reader)
          header-row (first csv-records)
          header-keys (get-header-keys header-row column-config)]
      (write-csv-rows writer header-keys (observation-rows header-row (rest csv-records) column-config)))))

(defn cube->csvw->rdf [input-csv dataset-name dataset-slug component-specifications-csv observations-csv column-config]
  (cube->csvw input-csv component-specifications-csv observations-csv column-config)

  (let [domain-data (config/domain-data column-config)
        component-specification-metadata-meta (component-specification-metadata component-specifications-csv domain-data dataset-name dataset-slug)
        dataset-metadata-meta (dataset-metadata component-specifications-csv domain-data dataset-name dataset-slug)
        dsd-metadata-meta (data-structure-definition-metadata component-specifications-csv domain-data dataset-name dataset-slug)
        observations-metadata-meta (with-open [reader (io/reader input-csv)]
                                     (observations-metadata reader observations-csv domain-data dataset-slug column-config))
        used-codes-codelists-metadata-meta (used-codes-codelists-metadata component-specifications-csv domain-data dataset-slug)
        used-codes-codes-metadata-meta (with-open [reader (io/reader input-csv)]
                                         (used-codes-codes-metadata reader observations-csv domain-data dataset-slug column-config))]
    (liberal-concat
      (csvw/csv->rdf component-specifications-csv (create-metadata-source input-csv component-specification-metadata-meta) {:mode :standard})
      (csvw/csv->rdf component-specifications-csv (create-metadata-source input-csv dataset-metadata-meta) {:mode :standard})
      (csvw/csv->rdf component-specifications-csv (create-metadata-source input-csv dsd-metadata-meta) {:mode :standard})
      (csvw/csv->rdf observations-csv (create-metadata-source input-csv observations-metadata-meta) {:mode :standard})
      (csvw/csv->rdf component-specifications-csv (create-metadata-source input-csv used-codes-codelists-metadata-meta) {:mode :standard})
      (csvw/csv->rdf observations-csv (create-metadata-source input-csv used-codes-codes-metadata-meta) {:mode :standard}))))

(defn cube-pipeline
  "Generates cube RDF for the given input CSV with dataset name and slug."
  [input-csv dataset-name dataset-slug column-config]
  (let [component-specifications-csv (tempfile "component-specifications" ".csv")
        observations-csv (tempfile "observations" ".csv")]
    (cube->csvw->rdf input-csv dataset-name dataset-slug
                     component-specifications-csv observations-csv
                     column-config)))

(defn get-config []
  (ig/read-string (slurp (io/resource "table2qb-config.edn"))))

(defmethod ig/init-key ::pipeline [key config]
  (let [ns (find-ns (symbol (namespace key)))
        pipeline-name (symbol (name key))
        ;;TODO: require namespace?
        var (ns-resolve ns pipeline-name)]
    (assoc config :name pipeline-name :var var :description (:doc (meta var)))))

(defmethod ig/init-key ::pipeline-runner [_ pipelines]
  pipelines)

(derive ::cube-pipeline ::pipeline)
(derive ::components-pipeline ::pipeline)
(derive ::codelist-pipeline ::pipeline)
