(ns table2qb.core
  (:require [clojure.data.csv :as csv]
            [clojure.data.json :as json]
            [net.cgrand.xforms :as x]
            [clojure.java.io :as io]))

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

(defn title->name [title]
  "Standardises a title to a unambiguious internal name"
  ({"GeographyCode" :geography
     "DateCode" :date
     "Measurement" :measure
     "Units" :unit
     "Value" :value
     "SITC Section" :sitc_section
     "Flow" :flow} title (keyword title)))

;; Component attributes for an name
(def name->component
  (with-open [rdr (-> "components.csv" io/resource io/reader)]
    (let [component-attributes (read-csv rdr)]
      (zipmap (map (comp keyword :component_slug) component-attributes)
              component-attributes))))




;; Identifying Components

(defn headers-matching [pred]
  (comp (take 1) (map keys) cat (filter pred)))

(def is-dimension? #{:geography :date :sitc_section :flow})
(def is-attribute? #{:unit})

(defn append-measures-dimension [xf]
  (fn
    ([] (xf))
    ([result] (xf (xf result) :measure_type))
    ([result input] (xf result input))))

(def dimensions
  (comp (headers-matching is-dimension?)
        append-measures-dimension
        (map name->component)))

(def attributes
  (comp (headers-matching is-attribute?)
        (map name->component)))

(def standardise-measure {"Value" :value, "Net Mass", :net_mass})

(def measures
  (comp (map :measure)
        (distinct)
        (map standardise-measure) ;; replace with title->name?
        (map name->component)))

(defn components
  "Takes an filename for csv of observations and returns a sequence of components"
  [reader]
  (let [data (read-csv reader title->name)]
    (sequence (x/multiplex [dimensions attributes measures]) data)))

(defn json-metadata [csv-url dataset-name dataset-slug]
  {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
   "url" csv-url,
   "dc:title" dataset-name,
   "tableSchema"
   {"columns"
    [{"name" "component_slug",
      "titles" "Component Slug",
      "datatype" "string",
      "suppressOutput" true}
     {"name" "component_attachment",
      "titles" "Component Attachment",
      "datatype" "string",
      "suppressOutput" true}
     {"name" "component_property",
      "titles" "Component Property",
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
    "aboutUrl" (str "http://statistics.data.gov.uk/data/" dataset-slug "/component/{component_slug}")}})

(defn pipeline [input-csv output-dir dataset-name dataset-slug]
  (let [fn-csv-cs (str output-dir "component-specifications.csv")
        fn-json-cs (str fn-csv-cs "-metadata.json")]
    (with-open [reader (io/reader input-csv)
                writer (io/writer fn-csv-cs)]
      (write-csv writer (components reader)))
    (with-open [writer (io/writer fn-json-cs)]
      (write-json writer (json-metadata fn-csv-cs dataset-name dataset-slug)))))

;; (pipeline (example "input.csv") "./tmp/" "Regional Trade" "regional-trade")
