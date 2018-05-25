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
            [csv2rdf.source :as source]
            [grafter.rdf :as rdf]))

;; Config
(def domain (environ/env :base-uri "http://gss-data.org.uk/"))
(def domain-data (str domain "data/"))
(def domain-def (str domain "def/"))

;; CSV handling

(defn read-csv
  ([reader]
   "Reads converting headers to keywords"
   (read-csv reader keyword))
  ([reader header-mapping]
   "Reads csv into seq of hashes, with mapping from headers to keys"
   (let [csv-data (csv/read-csv reader)]
     (map zipmap
          (->> (first csv-data)
               (map header-mapping)
               repeat)
          (rest csv-data)))))

(defn unkeyword [keyword]
  "Converts a keyword in to a string without the leading colon"
  (subs (str keyword) 1))

(defn write-csv [writer data]
  (csv/write-csv writer
                 (cons (map unkeyword (keys (first data)))
                       (map vals data))))

;; JSON handling

(def read-json json/read)
(defn write-json [writer data]
  (json/write data writer))

;; Conventions

(defn blank->nil [value]
  (if (= "" value) nil value))

;; Creates lookup of columns (from a csv) for an name (in the component_slug field)
(def name->component ;; TODO: defonce me
  (with-open [rdr (-> "columns.csv" io/resource io/reader)]
    (let [columns (read-csv rdr)]
      (zipmap (map (comp keyword :name) columns)
              (map (partial reduce-kv (fn [m k v] (assoc m k (blank->nil v))) {}) columns)))))

(def title->name-lookup
  (zipmap (map :title (vals name->component))
          (map (comp keyword :name) (vals name->component))))

(defn title->name [title]
  (let [name (title->name-lookup title)]
    (if name name
        (throw (ex-info (str "Unrecognised column: " title)
                        { :known-columns (keys title->name-lookup)})))))

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

(defn headers-matching [pred]
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

(defn measure [row]
  (let [measure-type-columns (select-keys row is-measure-type?)] ;; TODO: this should happen once per table, not per row
    (if (not (= 1 (count measure-type-columns)))
      (throw (ex-info
              (if (> (count measure-type-columns) 1)
                "Too many measure type columns" "No measure type column")
              {:measure-type-columns-found (keys measure-type-columns)}))
      (first (vals measure-type-columns)))))

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

(defn identify-transformers [row]
  "Returns a map from column name to transformation function (where provided)"
  (let [columns (keys row)
        name->transformer (comp resolve-transformer :value_transformation name->component)
        transform-map (zipmap columns (map name->transformer columns))]
    (select-keys transform-map
                 (for [[k v] transform-map :when (not (nil? v))] k)))) ;; removes columns that have no transform

(defn transform-columns [row]
  "Prepares cells for inclusion in URL templates, typically by slugizing"
  (let [transformations (identify-transformers row)] ;; TODO: identify once for whole table (not per row)
    (reduce (fn [row [col f]] (update row col f)) row transformations)))

(defn validate-dimensions [row]
  "Ensures that dimension columns have no missing values"
  (doseq [dimension (select-keys row is-dimension?)]
    (if (gecs/blank? (val dimension))
      (throw (ex-info (str "Missing value for dimension: " (key dimension))
                      { :row row })))))

(defn validate-columns [row]
  "Ensures that columns are valid"
  (validate-dimensions row)
  row)

(defn observations [reader]
  (let [data (read-csv reader title->name)]
    (sequence (map (comp transform-columns
                         validate-columns)) data)))

(defn component->column [{:keys [name title property_template value_template datatype]}]
  (merge {"name" name
          "titles" name ;; could revert to title here (would need to do so in all output csv too)
          "datatype" datatype
          "propertyUrl" property_template}
         (when (not (nil? value_template)) {"valueUrl" value_template})))

(defn dataset-link [dataset-slug]
  (let [ds-uri (str domain-data dataset-slug)]
    {"name" "DataSet",
     "virtual" true,
     "propertyUrl" "qb:dataSet",
     "valueUrl" ds-uri}))

(def observation-type
  {"name" "Observation",
   "virtual" true,
   "propertyUrl" "rdf:type",
   "valueUrl" "qb:Observation"})

(defn observation-template [dataset-slug components]
  (let [uri-parts (->> components
                       (remove #(is-value? (keyword %)))
                       (map #(str "/{+" % "}")))]
    (str domain-data
         dataset-slug
         (st/join uri-parts))))

(defn target-order [v]
  "Returns a function for use with sort-by which returns an index of an
  element according to a target vector, or an index falling after the target
  (i.e. putting unrecognised elements at the end)."
  (fn [element]
    (let [i (.indexOf v element)]
      (if (= -1 i)
        (inc (count v))
        i))))

(defn observations-metadata [reader csv-url dataset-slug]
  (let [data (read-csv reader title->name)
        column-order (->> data first keys (map unkeyword) target-order)
        components (sequence (comp (x/multiplex [dimensions attributes values])
                                   (map name->component)
                                   (x/sort-by #(column-order (get % :name)))) data)
        columns (into [] (comp (map component->column)
                               (append (dataset-link dataset-slug))
                               (append observation-type)) components)
        columns (sort-by #(column-order (get % "name")) columns)]
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
        "valueUrl" "skos:ConceptScheme"}],
      "aboutUrl" codelist-uri}}))

(defn suppress-value [row]
  (if (is-value? (keyword (get row "name")))
    (assoc row "suppressOutput" true)
    row))

(defn used-codes-codes-metadata [reader csv-url dataset-slug]
  (let [data (read-csv reader title->name)
        codelist-uri (str domain-data dataset-slug "/codes-used/{_name}")
        components (sequence (comp (x/multiplex [dimensions attributes values])
                                   (map name->component)) data)
        column-order (->> data first keys (map unkeyword) target-order)
        columns (into [] (comp (map component->column)
                               (map #(assoc % "propertyUrl" "skos:member"))
                               (map suppress-value)) components)
        columns (sort-by #(column-order (get % "name")) columns)]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "url" (str csv-url)
     "tableSchema"
     {"columns" (vec columns)
      "aboutUrl" codelist-uri}}))


(defn components [reader]
  (let [data (read-csv reader {"Label" :label
                               "Description" :description
                               "Component Type" :component_type
                               "Codelist" :codelist})]
    (sequence (map (fn [row]
                     (-> row
                         (assoc :notation (gecu/slugize (:label row)))
                         (assoc :component_type_slug ({"Dimension" "dimension"
                                                       "Measure" "measure"
                                                       "Attribute" "attribute"}
                                                      (row :component_type)))
                         (assoc :property_slug (gecu/propertize (:label row)))
                         (assoc :class_slug (gecu/classize (:label row)))
                         (update :component_type {"Dimension" "qb:DimensionProperty"
                                                  "Measure" "qb:MeasureProperty"
                                                  "Attribute" "qb:AttributeProperty"})
                         (assoc :parent_property (if (= "Measure" (:component_type row))
                                                   "http://purl.org/linked-data/sdmx/2009/measure#obsValue")))))
              data)))

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
        "valueUrl" ontology-uri }
       {"propertyUrl" "rdf:type",
        "virtual" true,
        "valueUrl" "rdf:Property"}],
      "aboutUrl" (str domain-def "{component_type_slug}/{notation}")}})) ;; property-slug?

(defn codes [reader]
  (let [data (read-csv reader {"Label" :label
                               "Notation" :notation
                               "Parent Notation", :parent_notation})]
    data))

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
       {"propertyUrl" "skos:inScheme",
        "valueUrl" codelist-uri,
        "virtual" true}
       {"propertyUrl" "skos:topConceptOf",
        "valueUrl" codelist-uri,
        "how-to-make-this-only-apply-when-parent-is-null?" "perhaps reasoning?",
        "virtual" true}
       {"propertyUrl" "skos:hasTopConcept",
        "aboutUrl" codelist-uri,
        "valueUrl" code-uri,
        "how-to-make-this-only-apply-when-parent-is-null?" "perhaps reasoning?",
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
    (write-csv writer (components reader))))

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
    ;;TODO: don't use concat
    (concat
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
