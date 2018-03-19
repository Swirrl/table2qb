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

;; Creates lookup of columns (from a csv) for an name (in the component_slug field)
(def name->component ;; TODO: defonce me
  (with-open [rdr (-> "columns.csv" io/resource io/reader)]
    (let [columns (read-csv rdr)]
      (zipmap (map (comp keyword :name) columns)
              columns))))

(def title->name-lookup
  (zipmap (map :title (vals name->component))
          (map (comp keyword :name) (vals name->component))))

(defn title->name [title]
  (title->name-lookup title (keyword title)))

(def is-dimension? #{:geography :date :sitc_section :flow :measure_type}) ;; TODO: specify in components.csv?
(def is-attribute? #{:unit})


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
  (str "http://gss-data.org.uk/data/" dataset-slug "/component/{component_slug}"))

(defn component-specification-metadata [csv-url dataset-name dataset-slug]
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
      "valueUrl" (str "http://gss-data.org.uk/data/" dataset-slug "/codes-used/{component_slug}")}],
    "aboutUrl" (component-specification-template dataset-slug)}})

(defn dataset-metadata [csv-url dataset-name dataset-slug]
  (let [ds-uri (str "http://gss-data.org.uk/data/" dataset-slug)
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
  (let [dsd-uri (str "http://gss-data.org.uk/data/" dataset-slug "/structure")
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

(defn component->column [{:keys [name title property_template value_template datatype]}]
  (merge {"name" name
          "titles" name ;; could revert to title here (would need to do so in all output csv too)
          "datatype" datatype
          "propertyUrl" property_template}
         (when (not (= "" value_template)) {"valueUrl" value_template})))

(defn dataset-link [dataset-slug]
  (let [ds-uri (str "http://gss-data.org.uk/data/" dataset-slug)]
    {"name" "DataSet",
     "virtual" true,
     "propertyUrl" "qb:dataSet",
     "valueUrl" ds-uri}))

(def observation-type
  {"name" "Observation",
   "virtual" true,
   "propertyUrl" "rdf:type",
   "valueUrl" "qb:Observation"})

(def values
  (headers-matching #{:value}))

(defn observation-template [dataset-slug components]
  (let [uri-parts (->> components
                       (sort-by #(get {"geography" -2 "date" -1 "measure_type" 1 "unit" 2} % 0)) ;; TODO - extract conventions
                       (remove #{"value"})
                       (map #(str "/{" % "}")))]
    (str "http://gss-data.org.uk/data/"
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
      "aboutUrl" (observation-template dataset-slug (map :name components))}}))

(defn used-codes-codelists-metadata [csv-url dataset-slug]
  (let [codelist-uri (str "http://gss-data.org.uk/data/" dataset-slug "/codes-used/{component_slug}")]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "url" csv-url,
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
  (if (= "value" (get row "name"))
    (assoc row "suppressOutput" true)
    row))

(defn used-codes-codes-metadata [reader csv-url dataset-slug]
  (let [data (read-csv reader title->name)
        codelist-uri (str "http://gss-data.org.uk/data/" dataset-slug "/codes-used/{_name}")
        components (sequence (comp (x/multiplex [dimensions attributes values])
                                   (map name->component)) data)
        column-order (->> data first keys (map unkeyword) target-order)
        columns (into [] (comp (map component->column)
                               (map #(assoc % "propertyUrl" "skos:member"))
                               (map suppress-value)) components)
        columns (sort-by #(column-order (get % "name")) columns)]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "url" csv-url,
     "tableSchema"
     {"columns" columns,
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
  (let [ontology-uri "http://gss-data.org.uk/def/ontology/components"]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "@id" ontology-uri,
     "url" csv-url,
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
        "propertyUrl" "dct:description"}
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
        "valueUrl" "http://gss-data.org.uk/def/{class_slug}"}
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
      "aboutUrl" "http://gss-data.org.uk/def/{component_type_slug}/{notation}"}})) ;; property-slug?

(defn codes [reader]
  (let [data (read-csv reader {"Label" :label
                               "Notation" :notation
                               "Parent Notation", :parent_notation})]
    data))

(defn codelist-metadata [csv-url codelist-name codelist-slug]
  (let [codelist-uri (str "http://gss-data.org.uk/def/concept-scheme/" codelist-slug)
        code-uri (str "http://gss-data.org.uk/def/concept/" codelist-slug "/{notation}")
        parent-uri (str "http://gss-data.org.uk/def/concept/" codelist-slug "/{parent_notation}")]
    {"@context" ["http://www.w3.org/ns/csvw" {"@language" "en"}],
     "@id" codelist-uri,
     "url" csv-url,
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


;; serialize

(defn codelist-pipeline [input-csv output-dir codelist-name codelist-slug]
  (with-open [reader (io/reader input-csv)
              writer (io/writer (str output-dir "/" codelist-slug ".csv"))]
    (write-csv writer (codes reader)))
  (with-open [reader (io/reader input-csv)
              writer (io/writer (str output-dir "/" codelist-slug ".json"))]
    (write-json writer (codelist-metadata (str codelist-slug ".csv") codelist-name codelist-slug))))

(defn components-pipeline [input-csv output-dir]
  (with-open [reader (io/reader input-csv)
              writer (io/writer (str output-dir "/components.csv"))]
    (write-csv writer (components reader)))
  (with-open [writer (io/writer (str output-dir "/components.json"))]
    (write-json writer (components-metadata "components.csv"))))

(defn data-pipeline [input-csv output-dir dataset-name dataset-slug]
  (let [writer (fn [filename] (io/writer (str output-dir "/" filename)))
        component-specifications-csv "component-specifications.csv"
        component-specifications-json "component-specifications.json"
        dataset-json "dataset.json"
        data-structure-definition-json "data-structure-definition.json"
        observations-csv "observations.csv"
        observations-json "observations.json"
        used-codes-codelists-json "used-codes-codelists.json"
        used-codes-codes-json "used-codes-codes.json"]
    (with-open [reader (io/reader input-csv)
                writer (writer component-specifications-csv)]
      (write-csv writer (component-specifications reader)))
    (with-open [writer (writer component-specifications-json)]
      (write-json writer (component-specification-metadata component-specifications-csv dataset-name dataset-slug)))
    (with-open [writer (writer dataset-json)]
      (write-json writer (dataset-metadata component-specifications-csv dataset-name dataset-slug)))
    (with-open [writer (writer data-structure-definition-json)]
      (write-json writer (data-structure-definition-metadata component-specifications-csv dataset-name dataset-slug)))
    (with-open [reader (io/reader input-csv)
                writer (writer observations-csv)]
      (write-csv writer (observations reader)))
    (with-open [reader (io/reader input-csv)
                writer (writer observations-json)]
      (write-json writer (observations-metadata reader observations-csv dataset-slug)))
    (with-open [writer (writer used-codes-codelists-json)]
      (write-json writer (used-codes-codelists-metadata component-specifications-csv dataset-slug)))
    (with-open [reader (io/reader input-csv)
                writer (writer used-codes-codes-json)]
      (write-json writer (used-codes-codes-metadata reader observations-csv dataset-slug)))))


;; CSV2RDF

(defn rdf-serialize [output-dir resource]
  ["rdf" "serialize"
   "--input-format" "tabular" (str output-dir "/" resource ".json")
   "--output-format" "ttl" ">" (str output-dir "/" resource ".ttl")])

(defn csv2rdf [output-dir resource]
  (println (str "converting: " resource))
  (println (sh "sh" "-c" (st/join " " (rdf-serialize output-dir resource)))))

(defn csv2rdf-qb [output-dir]
  (for [resource ["component-specifications"
                  "dataset"
                  "data-structure-definition"
                  "observations"
                  "used-codes-codelists"
                  "used-codes-codes"]]
    (csv2rdf output-dir resource)))

(defn serialise-demo []
  (components-pipeline "./examples/regional-trade/csv/components.csv" "./tmp")
  (csv2rdf "./tmp" "components")

  (codelist-pipeline "./examples/regional-trade/csv/flow-directions.csv" "./tmp" "Flow Directions" "flow-directions")
  (csv2rdf "./tmp" "flow-directions")
  (codelist-pipeline "./examples/regional-trade/csv/sitc-sections.csv" "./tmp" "SITC Sections" "sitc-sections")
  (csv2rdf "./tmp" "sitc-sections")
  (codelist-pipeline "./examples/regional-trade/csv/units.csv" "./tmp" "Measurement Units" "measurement-units")
  (csv2rdf "./tmp" "measurement-units")

  (data-pipeline "./examples/regional-trade/csv/input.csv" "./tmp" "Regional Trade" "regional-trade")
  (csv2rdf-qb "./tmp"))

;;(serialise-demo)


