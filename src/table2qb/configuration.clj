(ns table2qb.configuration
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [table2qb.util :refer [map-values exception?]]
            [table2qb.csv :refer [read-csv]]))

(defn blank->nil [value]
  (if (= "" value) nil value))

(defn- configuration-rows
  "Loads the configuration from a readable source"
  [source]
  (with-open [r (io/reader source)]
    (doall (read-csv r))))

(defn configuration-row->column
  "Creates a column definition from a row of the configuration file. Returns an Exception if the row is invalid."
  [row-index {:keys [name] :as row}]
  (cond
    (string/blank? name) (RuntimeException. (format "Row %d: csvw:name cannot be blank" row-index))
    (string/includes? name "-") (RuntimeException. (format "Row %d: csvw:name %s cannot contain hyphens (use underscores instead): " row-index name))
    :else (map-values row blank->nil)))

(defn identify-columns [conventions-map attachment]
  "Returns a predicate (set) of column names where the :component_attachment property is as specified"
  (reduce-kv (fn [s name {:keys [component_attachment]}]
               (if (= component_attachment attachment) (conj s name) s))
             #{}
             conventions-map))

(def dimensions :dimensions)
(def attributes :attributes)
(def values :values)
(def measures :measures)
(def measure-types :measure-types)

(defn title->name [config title]
  (let [title->name-lookup (:title->name config)]
    (if-let [name (title->name-lookup title)]
      name
      (throw (ex-info (str "Unrecognised column: " title)
                      {:known-columns (keys title->name-lookup)})))))

(def name->component :name->component)

(defn load-configuration
  "Creates lookup of columns (from a csv) for a name (in the component_slug field)"
  ([] (load-configuration (io/resource "columns.csv")))
  ([source]
   (let [config-rows (configuration-rows source)
         columns (map-indexed configuration-row->column config-rows)
         errors (filter exception? columns)
         valid-columns (remove exception? columns)]
     (if (seq errors)
       (let [msg (string/join "\n" (map #(.getMessage %) errors))]
         (throw (RuntimeException. msg)))
       (into {} (map (fn [col] [(keyword (:name col)) col]) valid-columns))))))

(defn real-load-configuration
  ([] (real-load-configuration (io/resource "columns.csv")))
  ([source]
   (let [name->component (load-configuration source)]
     {:name->component name->component
      :title->name     (zipmap (map :title (vals name->component))
                               (map (comp keyword :name) (vals name->component)))
      :dimensions      (identify-columns name->component "qb:dimension")
      :attributes      (identify-columns name->component "qb:attribute")
      :values          (identify-columns name->component nil) ;; if it's not attached as a component then it must be a value
      :measures        (identify-columns name->component "qb:measure") ;; as yet unused, will be needed for multi-measure cubes (note this includes single-measure ones not using the measure-dimension approach)
      :measure-types   (->> (vals name->component)
                            (filter #(= (:property_template %) "http://purl.org/linked-data/cube#measureType"))
                            (map (comp keyword :name))
                            set)})))