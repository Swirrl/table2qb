(ns table2qb.core
  (:require [clojure.data.csv :as csv]
            [clojure.data.json :as json]
            [net.cgrand.xforms :as x]
            [clojure.java.io :as io]
            [grafter.extra.cell.uri :as gecu]
            [grafter.extra.cell.string :as gecs]
            [grafter.rdf.io :as gio]
            [clojure.string :as st]
            [clojure.java.shell :refer [sh]]
            [environ.core :as environ]
            [csv2rdf.csvw :as csvw]
            [csv2rdf.util :refer [liberal-concat]]
            [csv2rdf.source :as source]
            [grafter.rdf :as rdf]
            [clojure.string :as string]
            [table2qb.util :refer [exception? map-values] :as util]))

;; Config
(def domain (environ/env :base-uri "http://gss-data.org.uk/"))
(def domain-data (str domain "data/"))
(def domain-def (str domain "def/"))

;; CSV handling

(defn- csv-rows
  "Returns a lazy sequence of CSV row records given a header row, data row and row heading->key name mapping."
  [header-row data-rows header-mapping]
  (map zipmap
       (->> header-row
            (map header-mapping)
            repeat)
       data-rows))

(defn read-csv
  ([reader]
   "Reads converting headers to keywords"
   (read-csv reader keyword))
  ([reader header-mapping]
   "Reads csv into seq of hashes, with mapping from headers to keys"
   (let [csv-data (csv/read-csv reader)]
     (csv-rows (first csv-data) (rest csv-data) header-mapping))))

(defn write-csv [writer data]
  (csv/write-csv writer
                 (cons (map name (keys (first data)))
                       (map vals data))))

(defn write-csv-rows [writer column-keys data-rows]
  (let [header (map name column-keys)
        extract-cells (apply juxt column-keys)
        data-records (map extract-cells data-rows)]
    (csv/write-csv writer (cons header data-records))))

;; JSON handling

(def read-json json/read)
(defn write-json [writer data]
  (json/write data writer))

;; Conventions

(defn blank->nil [value]
  (if (= "" value) nil value))

(defn get-configuration
  "Loads the configuration from a resource"
  []
  (with-open [r (io/reader (io/resource "columns.csv"))]
    (doall (read-csv r))))

(defn configuration-row->column
  "Creates a column definition from a row of the configuration file. Returns an Exception if the row is invalid."
  [row-index {:keys [name] :as row}]
  (cond
    (string/blank? name) (RuntimeException. (format "Row %d: csvw:name cannot be blank" row-index))
    (string/includes? name "-") (RuntimeException. (format "Row %d: csvw:name %s cannot contain hyphens (use underscores instead): " row-index name))
    :else (map-values row blank->nil)))

(defn column-configuration
  "Creates lookup of columns (from a csv) for a name (in the component_slug field)"
  []
  (let [config-rows (get-configuration)
        columns (map-indexed configuration-row->column config-rows)
        errors (filter exception? columns)
        valid-columns (remove exception? columns)]
    (if (seq errors)
      (let [msg (string/join "\n" (map #(.getMessage %) errors))]
        (throw (RuntimeException. msg)))
      (into {} (map (fn [col] [(keyword (:name col)) col]) valid-columns)))))

(def name->component ;; TODO: defonce me
  (column-configuration))

(def title->name-lookup
  (zipmap (map :title (vals name->component))
          (map (comp keyword :name) (vals name->component))))

(defn title->name [title]
  (if-let [name (title->name-lookup title)]
    name
    (throw (ex-info (str "Unrecognised column: " title)
                    {:known-columns (keys title->name-lookup)}))))

(defn identify-columns [conventions-map attachment]
  "Returns a predicate (set) of column names where the :component_attachment property is as specified"
  (reduce-kv (fn [s name {:keys [component_attachment]}]
               (if (= component_attachment attachment) (conj s name) s))
             #{}
             conventions-map))

(def is-dimension? (identify-columns name->component "qb:dimension"))
(def is-attribute? (identify-columns name->component "qb:attribute"))
(def is-value? (identify-columns name->component nil)) ;; if it's not attached as a component then it must be a value
(def is-measure? (identify-columns name->component "qb:measure")) ;; as yet unused, will be needed for multi-measure cubes (note this includes single-measure ones not using the measure-dimension approach)
(def is-measure-type? (->> name->component
                           (map val)
                           (filter #(= (:property_template %) "http://purl.org/linked-data/cube#measureType"))
                           (map (comp keyword :name))
                           set))

;; Identifying Components

(defn headers-matching
  "Finds the headers matching a predicate in a sequence of row maps"
  [pred]
  (comp (take 1) (map keys) cat (filter pred)))

(defn append [item]
  (fn [xf]
    (fn
      ([] (xf))
      ([result] (xf (xf result) item))
      ([result input] (xf result input)))))

(def dimensions
  (headers-matching is-dimension?))

(def attributes
  (headers-matching is-attribute?))

(def values
  (headers-matching is-value?))

(defn measure
  "Returns the single measure value for the row. Throws an exception if there is not exactly one measure type."
  ([row] (measure row is-measure-type?))
  ([row measure-types]
   (let [measure-type-columns (select-keys row measure-types)] ;; TODO: this should happen once per table, not per row
     (case (count measure-type-columns)
       0 (throw (ex-info "No measure type column" {:measure-type-columns-found nil}))
       1 (first (vals measure-type-columns))
       (throw (ex-info "Too many measure type columns" {:measure-type-columns-found (keys measure-type-columns)}))))))

(def measures
  (comp (map measure)
        (distinct)
        (map title->name)))

(def identify-components
  (x/multiplex [dimensions attributes measures]))

(defn component-specifications
  "Takes an filename for csv of observations and returns a sequence of components"
  [reader]
  (let [data (read-csv reader title->name)]
    (sequence (comp identify-components
                    (map name->component)
                    (map (fn [{:keys [name component_attachment property_template]}]
                           {:component_slug name
                            :component_attachment component_attachment
                            :component_property property_template}))) data)))

(defn component-specification-template [dataset-slug]
  (str domain-data dataset-slug "/component/{component_slug}"))

(defn component-specification-metadata [csv-url dataset-name dataset-slug]
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
    "aboutUrl" (component-specification-template dataset-slug)}})

(defn dataset-metadata [csv-url dataset-name dataset-slug]
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

(defn data-structure-definition-metadata [csv-url dataset-name dataset-slug]
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
        "valueUrl" (component-specification-template dataset-slug)}
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

(defn identify-transformers
  "Returns a map from column name to transformation function (where provided)"
  ([row] (identify-transformers row name->component))
  ([row components]
   (into {} (map (fn [component-name]
                   (let [transformer-name (get-in components [component-name :value_transformation])]
                     (if-let [transform-fn (get resolve-transformer transformer-name)]
                       [component-name transform-fn])))
                 (keys row)))))

(defn transform-columns [row]
  "Prepares cells for inclusion in URL templates, typically by slugizing"
  (let [transformations (identify-transformers row)] ;; TODO: identify once for whole table (not per row)
    (reduce (fn [row [col f]] (update row col f)) row transformations)))

(defn validate-dimensions [row]
  "Ensures that dimension columns have no missing values"
  (doseq [dimension (select-keys row is-dimension?)]
    (if (gecs/blank? (val dimension))
      (throw (ex-info (str "Missing value for dimension: " (key dimension))
                      {:row row})))))

(defn validate-columns [row]
  "Ensures that columns are valid"
  (validate-dimensions row)
  row)

(defn observations [reader]
  (let [data (read-csv reader title->name)]
    (sequence (map (comp transform-columns
                         validate-columns)) data)))

(defn component->column [{:keys [name property_template value_template datatype]}]
  (let [col {"name" name
             "titles" name ;; could revert to title here (would need to do so in all output csv too)
             "datatype" datatype
             "propertyUrl" property_template}]
    (if (some? value_template)
      (assoc col "valueUrl" value_template)
      col)))

(defn dataset-link-column [dataset-slug]
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
  ([dataset-slug component-names] (observation-template dataset-slug component-names domain-data is-value?))
  ([dataset-slug component-names domain-data-prefix is-value-component-p]
   (let [uri-parts (->> component-names
                        (remove #(is-value-component-p (keyword %)))
                        (map #(str "/{+" % "}")))]
     (str domain-data-prefix dataset-slug (st/join uri-parts)))))

(defn- observation-components [observation-rows]
  (sequence (comp (x/multiplex [dimensions attributes values])
                  (map name->component))
            observation-rows))

(defn- ordered-observation-components
  "Returns a sequence of component records in the order they occur within an observations CSV file."
  [reader]
  (let [csv-records (csv/read-csv reader)
        header-row (first csv-records)
        column-names (map (comp name title->name) header-row)
        column-order (util/target-order column-names)
        data (csv-rows header-row (rest csv-records) title->name)
        components (observation-components data)]
    (sort-by #(column-order (get % :name)) components)))

(defn observations-metadata [reader csv-url dataset-slug]
  (let [components (ordered-observation-components reader)
        component-columns (sequence (map component->column) components)
        columns (concat component-columns [observation-type-column (dataset-link-column dataset-slug)])]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "url" (str csv-url)
     "tableSchema"
     {"columns" (vec columns)
      "aboutUrl" (observation-template dataset-slug (map :name components))}}))

(defn used-codes-codelists-metadata [csv-url dataset-slug]
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
  ([column] (suppress-value-column column is-value?))
  ([column is-value-p]
   (if (is-value-p (keyword (get column "name")))
     (assoc column "suppressOutput" true)
     column)))

(defn used-codes-codes-metadata [reader csv-url dataset-slug]
  (let [components (ordered-observation-components reader)
        columns (mapv (fn [comp]
                        (-> comp
                            (component->column)
                            (assoc "propertyUrl" "skos:member")
                            (suppress-value-column)))
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

(defn components-metadata [csv-url]
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

(defn ensure-default-fields [row]
  (-> row
      (update :sort_priority identity)
      (update :description identity)))

(def prepare-code
  (comp add-code-hierarchy-fields
        ensure-default-fields))

(defn codes [reader]
  (let [data (read-csv reader {"Label" :label
                               "Notation" :notation
                               "Parent Notation", :parent_notation
                               "Sort Priority", :sort_priority
                               "Description" :description})]
    (map prepare-code data)))

(defn codelist-metadata [csv-url codelist-name codelist-slug]
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

(defn codelist->csvw [input-csv codelist-csv]
  (with-open [reader (io/reader input-csv)
              writer (io/writer codelist-csv)]
      (write-csv writer (codes reader))))

(defn codelist->csvw->rdf [input-csv codelist-name codelist-slug codelist-csv]
  (codelist->csvw input-csv codelist-csv)
  (let [codelist-meta (codelist-metadata codelist-csv codelist-name codelist-slug)]
    (csvw/csv->rdf codelist-csv (create-metadata-source input-csv codelist-meta) csv2rdf-config)))

(defn codelist-pipeline [input-csv codelist-name codelist-slug]
  (let [codelist-csv (tempfile codelist-slug ".csv")]
    (codelist->csvw->rdf input-csv codelist-name codelist-slug codelist-csv)))

(defn components->csvw [input-csv components-csv]
  (with-open [reader (io/reader input-csv)
              writer (io/writer components-csv)]
    (let [component-columns [:label :description :component_type :codelist :notation :component_type_slug :property_slug :class_slug :parent_property]]
      (write-csv-rows writer component-columns (components reader)))))

(defn components->csvw->rdf [input-csv components-csv]
  (components->csvw input-csv components-csv)
  (let [components-meta (components-metadata components-csv)]
    (csvw/csv->rdf components-csv (create-metadata-source input-csv components-meta) csv2rdf-config)))

(defn components-pipeline [input-csv]
  (let [components-csv (tempfile "components" ".csv")]
    (components->csvw->rdf input-csv components-csv)))

(defn cube->csvw [input-csv component-specifications-csv observations-csv]
  (with-open [reader (io/reader input-csv)
              writer (io/writer component-specifications-csv)]
    (write-csv writer (component-specifications reader)))

  (with-open [reader (io/reader input-csv)
              writer (io/writer observations-csv)]
    (write-csv writer (observations reader))))

(defn cube->csvw->rdf [input-csv dataset-name dataset-slug component-specifications-csv observations-csv]
  (cube->csvw input-csv component-specifications-csv observations-csv)

  (let [component-specification-metadata-meta (component-specification-metadata component-specifications-csv dataset-name dataset-slug)
        dataset-metadata-meta (dataset-metadata component-specifications-csv dataset-name dataset-slug)
        dsd-metadata-meta (data-structure-definition-metadata component-specifications-csv dataset-name dataset-slug)
        observations-metadata-meta (with-open [reader (io/reader input-csv)]
                                     (observations-metadata reader observations-csv dataset-slug))
        used-codes-codelists-metadata-meta (used-codes-codelists-metadata component-specifications-csv dataset-slug)
        used-codes-codes-metadata-meta (with-open [reader (io/reader input-csv)]
                                         (used-codes-codes-metadata reader observations-csv dataset-slug))]
    (liberal-concat
      (csvw/csv->rdf component-specifications-csv (create-metadata-source input-csv component-specification-metadata-meta) {:mode :standard})
      (csvw/csv->rdf component-specifications-csv (create-metadata-source input-csv dataset-metadata-meta) {:mode :standard})
      (csvw/csv->rdf component-specifications-csv (create-metadata-source input-csv dsd-metadata-meta) {:mode :standard})
      (csvw/csv->rdf observations-csv (create-metadata-source input-csv observations-metadata-meta) {:mode :standard})
      (csvw/csv->rdf component-specifications-csv (create-metadata-source input-csv used-codes-codelists-metadata-meta) {:mode :standard})
      (csvw/csv->rdf observations-csv (create-metadata-source input-csv used-codes-codes-metadata-meta) {:mode :standard}))))

(defn cube-pipeline [input-csv dataset-name dataset-slug]
  (let [component-specifications-csv (tempfile "component-specifications" ".csv")
        observations-csv (tempfile "observations" ".csv")]
    (cube->csvw->rdf input-csv dataset-name dataset-slug
                     component-specifications-csv observations-csv)))

(defn serialise-demo [out-dir]
  (with-open [output-stream (io/output-stream (str out-dir "/components.ttl"))]
     (let [writer (gio/rdf-serializer output-stream :format :ttl)]
       (rdf/add writer
                (components-pipeline "./examples/regional-trade/csv/components.csv"))))

  (with-open [output-stream (io/output-stream (str out-dir "/flow-directions.ttl"))]
     (let [writer (gio/rdf-serializer output-stream :format :ttl)]
       (rdf/add writer
                (codelist-pipeline "./examples/regional-trade/csv/flow-directions.csv"
                                   "Flow Directions" "flow-directions"))))

  (with-open [output-stream (io/output-stream (str out-dir "/sitc-sections.ttl"))]
     (let [writer (gio/rdf-serializer output-stream :format :ttl)]
       (rdf/add writer
                (codelist-pipeline "./examples/regional-trade/csv/sitc-sections.csv"
                                   "SITC Sections" "sitc-sections"))))

  (with-open [output-stream (io/output-stream (str out-dir "/measurement-units.ttl"))]
     (let [writer (gio/rdf-serializer output-stream :format :ttl)]
       (rdf/add writer
                (codelist-pipeline "./examples/regional-trade/csv/units.csv"
                                   "Measurement Units" "measurement-units"))))

  (with-open [output-stream (io/output-stream (str out-dir "/cube.ttl"))]
    (let [writer (gio/rdf-serializer output-stream :format :ttl)]
      (rdf/add writer
               (cube-pipeline "./examples/regional-trade/csv/input.csv"
                              "Regional Trade" "regional-trade")))))

;;(serialise-demo "./tmp")
