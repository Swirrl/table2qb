(ns table2qb.configuration.columns
  (:require [table2qb.util :refer [map-values exception? blank->nil]]
            [table2qb.csv :as csv]
            [table2qb.configuration.column :as column]
            [table2qb.util :as util]))

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

(defn- load-columns [source]
  (with-open [r (csv/reader source)]
    (mapv column/remove-optional-columns (csv/read-csv-records r column/csv-columns))))

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
     :measure-types   (->key-set (filter column/is-qb-measure-type-column? columns))}))
