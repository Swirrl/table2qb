(ns table2qb.configuration.columns
  (:require [clojure.string :as string]
            [table2qb.util :refer [exception? blank->nil]]
            [table2qb.csv :as csv]
            [table2qb.configuration.column :as column]
            [clojure.data.csv :as ccsv]
            [clojure.set :as set]
            [table2qb.util :as util])
  (:import [clojure.lang ExceptionInfo]))

(def dimensions :dimensions)
(def attributes :attributes)
(def values :values)
(def measures :measures)
(def measure-types :measure-types)

(defn title->column [{:keys [title->name name->component] :as config} title]
  (->> title (title->name) (name->component)))

(defn title->key [{:keys [title->name] :as config} title]
  (get title->name title))

(defn known-titles [{:keys [title->name] :as config}]
  (keys title->name))

(def name->component :name->component)

(defn component-name->title [{:keys [name->component] :as config} component-name]
  (if-let [comp (get name->component component-name)]
    (:title comp)
    (throw (ex-info (str "Unknown component name " component-name) {:component-name component-name}))))

(defn- row-exception [ex ex-data row-index]
  (let [msg (format "Invalid column definition on row %d: %s" row-index (.getMessage ex))
        data (assoc (ex-data ex) :row row-index)]
    (ex-info msg data ex)))

(defn- try-parse-row [row-index row]
  (try
    (column/parse-column row)
    (catch ExceptionInfo ex
      (row-exception ex (ex-data ex) row-index))
    (catch Exception ex
      (row-exception ex {} row-index))))

(defn- parse-column-records [records]
  (let [columns (map-indexed try-parse-row records)
        errors (filter exception? columns)
        valid-columns (remove exception? columns)]
    (if (seq errors)
      (let [msg (string/join "\n" (map #(.getMessage %) errors))]
        (throw (RuntimeException. msg)))
      valid-columns)))

(defn- load-columns
  "Creates lookup of columns (from a csv) for a name (in the component_slug field)"
  [source]
  (with-open [r (csv/reader source)]
    (let [csv-rows (ccsv/read-csv r)]
      (if-let [header-row (first csv-rows)]
        (let [header-keys (mapv keyword header-row)
              missing-headers (set/difference column/required-input-keys (set header-keys))]
          (if (seq missing-headers)
            (let [msg (format "Invalid columns configuration: required columns %s missing"
                              (string/join ", " (map name missing-headers)))]
              (throw (ex-info msg {:type :invalid-column-configuration})))
            (let [csv-records (csv/csv-records header-keys (rest csv-rows))]
              (vec (parse-column-records csv-records)))))
        (throw (ex-info "Columns configuration empty" {:type :empty-columns-configuration
                                                       :source source}))))))

(defn- is-qb-measure-type-column? [column]
  ;;TODO: should only return true for dimension columns?
  (= "http://purl.org/linked-data/cube#measureType" (column/property-template column)))

(defn load-column-configuration
  [source]
  (let [columns (load-columns source)
        {:keys [dimension attribute measure value]} (group-by column/column-type columns)
        ->key-set (fn [columns] (set (map column/column-key columns)))]
    {:name->component (util/map-by column/column-key columns)
     :title->name     (into {} (map (juxt column/column-title column/column-key) columns))
     :dimensions      (->key-set dimension)
     :attributes      (->key-set attribute)
     :values          (->key-set value)
     :measures        (->key-set measure) ;; as yet unused, will be needed for multi-measure cubes (note this includes single-measure ones not using the measure-dimension approach)
     :measure-types   (->key-set (filter is-qb-measure-type-column? columns))}))
