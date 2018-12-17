(ns table2qb.configuration.columns
  (:require [clojure.string :as string]
            [table2qb.util :refer [map-values exception? blank->nil]]
            [table2qb.csv :as csv]
            [grafter.extra.cell.uri :as gecu]))

(defn- replace-symbols [s]
  (string/replace s #"£" "GBP"))

(defn- unitize [s]
  (gecu/slugize (replace-symbols s)))

;; TODO resolve on the basis of other component attributes? https://github.com/Swirrl/table2qb/issues/18
(def column-transformers
  {"slugize" gecu/slugize
   "unitize" unitize})

(defn- resolve-component-row-transform
  "Resolves the value_transformation cell of a configuration row to the corresponding transform function. Returns
   an exception if the function cannot be resolved."
  [row row-index]
  (if-let [vt (:value_transformation row)]
    (let [transform-fn (get column-transformers vt ::invalid)]
      (if (= ::invalid transform-fn)
        (let [msg (format "Row %d: Invalid value_transformation function %s. Valid transformations: %s"
                          row-index
                          vt
                          (string/join ", " (keys column-transformers)))]
          (ex-info msg {:type :invalid-transform
                        :row row-index
                        :transform vt}))
        (assoc row :value_transformation transform-fn)))
    row))

(defn configuration-row->column
  "Creates a column definition from a row of the configuration file. Returns an Exception if the row is invalid."
  [row-index {:keys [name] :as row}]
  (cond
    (string/blank? name) (ex-info (format "Row %d: csvw:name cannot be blank" row-index) {:type :blank-name
                                                                                          :row row-index})
    (string/includes? name "-") (ex-info (format "Row %d: csvw:name %s cannot contain hyphens (use underscores instead): " row-index name)
                                         {:type :invalid-name
                                          :row row-index
                                          :name name})
    :else (-> row
              (map-values blank->nil)
              (resolve-component-row-transform row-index))))

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

(defn load-column-components
  "Creates lookup of columns (from a csv) for a name (in the component_slug field)"
  [source]
  (let [config-rows (csv/read-all-csv-records source)
        columns (map-indexed configuration-row->column config-rows)
        errors (filter exception? columns)
        valid-columns (remove exception? columns)]
    (if (seq errors)
      (let [msg (string/join "\n" (map #(.getMessage %) errors))]
        (throw (RuntimeException. msg)))
      (into {} (map (fn [col] [(keyword (:name col)) col]) valid-columns)))))

(defn load-column-configuration
  [source]
  (let [name->component (load-column-components source)]
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
                           set)}))
