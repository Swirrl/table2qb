(ns table2qb.core
  (:require [clojure.data.csv :as csv]
            [clojure.data.json :as json]
            [net.cgrand.xforms :as x]
            [clojure.java.io :as io]
            [grafter.extra.cell.uri :as gecu]
            [clojure.string :as st]
            [clojure.java.shell :refer [sh]]))

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
;; TODO: make configurable
(defn title->name [title]
  "Standardises a title to a unambiguious internal name"
  ({"GeographyCode" :geography
    "DateCode" :date
    "Measurement" :measure_type
    "Units" :unit
    "Value" :value
    "SITC Section" :sitc_section
    "Flow" :flow} title (keyword title)))

;; Component attributes for an name
;; TODO: defonce me
(def name->component
  (with-open [rdr (-> "components.csv" io/resource io/reader)]
    (let [component-attributes (read-csv rdr)]
      (zipmap (map (comp keyword :component_slug) component-attributes)
              component-attributes))))



;; Identifying Components

(defn headers-matching [pred]
  (comp (take 1) (map keys) cat (filter pred)))

(def is-dimension? #{:geography :date :sitc_section :flow :measure_type}) ;; TODO: specify in components.csv?
(def is-attribute? #{:unit})

(defn append [item]
  (fn [xf]
    (fn
      ([] (xf))
      ([result] (xf (xf result) item))
      ([result input] (xf result input)))))

(def dimensions
  (comp (headers-matching is-dimension?)
        ;;(append :measure_type)
        ))

(def attributes
  (headers-matching is-attribute?))

(def standardise-measure {"GBP Total" :gbp_total, "Net Mass", :net_mass})

(def measures
  (comp (map :measure_type)
        (distinct)
        (map standardise-measure) ;; replace with title->name?
))

(def identify-components
  (x/multiplex [dimensions attributes measures]))

(defn components
  "Takes an filename for csv of observations and returns a sequence of components"
  [reader]
  (let [data (read-csv reader title->name)]
    (sequence (comp identify-components
                    (map name->component)
                    (map #(select-keys % [:component_slug
                                          :component_attachment
                                          :component_property]))) data)))

(defn component-specification-template [dataset-slug]
  (str "http://statistics.data.gov.uk/data/" dataset-slug "/component/{component_slug}"))

(defn components-metadata [csv-url dataset-name dataset-slug]
  {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
   "url" csv-url,
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
      "valueUrl"
      (str "http://statistics.data.gov.uk/data/" dataset-slug "/codes-used/{component_slug}")}],
    "aboutUrl" (component-specification-template dataset-slug)}})

(defn dataset-metadata [csv-url dataset-name dataset-slug]
  (let [ds-uri (str "http://statistics.data.gov.uk/data/" dataset-slug)
        dsd-uri (str ds-uri "/structure")
        ds-label dataset-name]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "@id" ds-uri,
     "url" csv-url,
     "dc:title" ds-label,
     "tableSchema"
     {"columns"
      [{"name" "component_slug", "titles" "component_slug", "suppressOutput" true}
       {"name" "component_attachment", "titles" "component_attachment", "suppressOutput" true}
       {"name" "component_property", "titles" "component_property", "suppressOutput" true}
       {"name" "type","virtual" true,"propertyUrl" "rdf:type","valueUrl" "qb:DataSet"}
       {"name" "structure","virtual" true,"propertyUrl" "qb:structure","valueUrl" dsd-uri}],
      "aboutUrl" ds-uri}}))

(defn data-structure-definition-metadata [csv-url dataset-name dataset-slug]
  (let [dsd-uri (str "http://statistics.data.gov.uk/data/" dataset-slug "/structure")
        dsd-label (str dataset-name " (Data Structure Definition)")]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "@id" dsd-uri,
     "url" csv-url,
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

(defn slugise-columns [row]
  (-> row
      (update :measure_type gecu/slugize)
      (update :unit (comp gecu/slugize replace-symbols))
      (update :sitc_section gecu/slugize) ;; TODO: generalise me
      (update :flow gecu/slugize)))

(defn observations [reader]
  (let [data (read-csv reader title->name)]
    (sequence (map slugise-columns) data)))

(defn component->column [{:keys [component_slug
                                 component_attachment
                                 component_property
                                 value_uri_template
                                 datatype]}]
  (merge {"name" component_slug
          "titles" component_slug ;; TODO: replace with title, standard or from original?
          "datatype" datatype
          "propertyUrl" component_property}
         (when (not (= "" value_uri_template)) {"valueUrl" value_uri_template})))

(defn dataset-link [dataset-slug]
  {"name" "DataSet",
   "virtual" true,
   "propertyUrl" "qb:dataSet",
   "valueUrl" "http://statistics.data.gov.uk/data/regional-trade"})

(def observation-type
  {"name" "Observation",
   "virtual" true,
   "propertyUrl" "rdf:type",
   "valueUrl" "qb:Observation"})

(def values
  (headers-matching #{:value}))

(defn observation-template [dataset-slug components]
  (let [uri-parts (->> components
                       (sort-by #(get {"geography" -2 "date" -1 "measure_type" 1 "unit" 2} % 0))
                       (remove #{"value"})
                       (map #(str "/{" % "}")))]
    (str "http://statistics.data.gov.uk/data/"
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
        components (sequence (comp (x/multiplex [dimensions attributes values])
                                   (map name->component)) data)
        column-order (->> data first keys (map unkeyword) target-order)
        columns (into [] (comp (map component->column)
                               (append (dataset-link dataset-slug))
                               (append observation-type)) components)
        columns (sort-by #(column-order (get % "name")) columns)]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "url" csv-url,
     "tableSchema"
     {"columns" columns,
      "aboutUrl" (observation-template dataset-slug (map :component_slug components))}}))


;; serialize

(defn pipeline [input-csv output-dir dataset-name dataset-slug]
  (let [writer (fn [filename] (io/writer (str output-dir "/" filename)))
        component-specifications-csv "component-specifications.csv"
        component-specifications-json "component-specifications.json"
        dataset-json "dataset.json"
        data-structure-definition-json "data-structure-definition.json"
        observations-csv "observations.csv"
        observations-json "observations.json"]
    (with-open [reader (io/reader input-csv)
                writer (writer component-specifications-csv)]
      (write-csv writer (components reader)))
    (with-open [writer (writer component-specifications-json)]
      (write-json writer (components-metadata component-specifications-csv dataset-name dataset-slug)))
    (with-open [writer (writer dataset-json)]
      (write-json writer (dataset-metadata component-specifications-csv dataset-name dataset-slug)))
    (with-open [writer (writer data-structure-definition-json)]
      (write-json writer (data-structure-definition-metadata component-specifications-csv dataset-name dataset-slug)))
    (with-open [reader (io/reader input-csv)
                writer (writer observations-csv)]
      (write-csv writer (observations reader)))
    (with-open [reader (io/reader input-csv)
                writer (writer observations-json)]
      (write-json writer (observations-metadata reader observations-csv dataset-slug)))))


;; CSV2RDF

(defn rdf-serialize [output-dir resource]
  ["rdf" "serialize"
   "--input-format" "tabular" (str output-dir "/" resource ".json")
   "--output-format" "ttl" ">" (str output-dir "/" resource ".ttl")])

(defn csv2rdf [output-dir resource]
  (sh "sh" "-c" (st/join " " (rdf-serialize output-dir resource))))

(defn csv2rdf-all [output-dir]
  (for [resource ["component-specifications" "dataset" "data-structure-definition" "observations"]]
    (csv2rdf output-dir resource)))


;;(pipeline "./test/resources/trade-example/input.csv" "./tmp" "Regional Trade" "regional-trade")
;;(csv2rdf-all "./tmp")
;;(csv2rdf "./tmp" "dataset")



