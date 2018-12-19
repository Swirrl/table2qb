(ns table2qb.configuration.cube
  "Namespace for deriving a cube-specific configuration for an observations data source and a columns configuration.
   Once built, the cube configuration can be queried for the contained dimension/attribute/measures etc. and used
   to load transformed observation records from a conforming data source."
  (:require [table2qb.configuration.columns :as column-config]
            [table2qb.csv :as tcsv]
            [clojure.data.csv :as csv]
            [clojure.string :as string]
            [clojure.set :as set]
            [grafter.extra.cell.string :as gecs]))

;;TODO: move into configuration.columns namespace?
(defn- resolve-component-names
  "Returns a sequence of component names of the corresponding observation column titles."
  [titles column-config]
  (let [title-name-pairs (map (fn [t] [t (column-config/title->key column-config t)]) titles)
        invalid-titles (map first (filter (fn [[_t n]] (nil? n)) title-name-pairs))]
    (if (seq invalid-titles)
      (throw (RuntimeException. (str "Unknown column titles: " (string/join ", " invalid-titles))))
      (mapv second title-name-pairs))))

(defn- throw-multiple-columns [component-names column-config column-type error-key]
  (let [titles (mapv #(column-config/component-name->title column-config %) component-names)
        msg (format "Found multiple %s columns: %s. Exactly one %s column should be defined."
                    column-type
                    (string/join titles)
                    column-type)]
    (throw (ex-info msg {error-key titles}))))

(defn- get-measure-type-component-name
  "Finds the named column which corresponds to the qb:measureType given a set of observation column names
   and a columns configuration. Exactly one qb:measureType column should exist - if no column or multiple
   columns exist, an exception will be thrown."
  [title-component-names column-config]
  (let [measure-types (set/intersection title-component-names (column-config/measure-types column-config))]
    (case (count measure-types)
      0 (throw (ex-info "No measure type column" {:measure-type-columns-found nil}))
      1 (first measure-types)
      (throw-multiple-columns measure-types column-config "qb:measureType" :measure-type-columns))))

(defn- get-value-component-name
  "Finds the named column containing observation values given a set of observation columns names and
  a columns configuration. Exactly one value column should exist - if no column or multiple columns
  exist, an exception will be thrown."
  [title-component-names column-config]
  (let [values (set/intersection title-component-names (column-config/values column-config))]
    (case (count values)
      0 (throw (ex-info "No value column" {}))
      1 (first values)
      (throw-multiple-columns values column-config "value" :value-columns))))

(defn- throw-invalid-measure
  "Throws an exception indicating a column referenced within a cube's qb:measureType column is not a qb:measure."
  [measure-type-component-name measure-title row-index column-config]
  (let [msg (format "Value '%s' in qb:measureType column '%s' row %d does not reference a qb:measure column"
                    measure-title
                    (column-config/component-name->title column-config measure-type-component-name)
                    row-index)]
    (throw (ex-info msg {:row-index row-index
                         :value measure-title}))))

(defn- resolve-observation-measure [measure-type-component-name observation-row row-index column-config]
  (let [measure-title (get observation-row measure-type-component-name)]
    (if-let [measure-name (get (:title->name column-config) measure-title)]
      (if (contains? (column-config/measures column-config) measure-name)
        measure-name
        (throw-invalid-measure measure-type-component-name measure-title row-index column-config))
      (throw-invalid-measure measure-type-component-name measure-title row-index column-config))))

(defn- resolve-measures
  "Returns a set of all referenced measure column names within the qb:measureType column of a cube."
  [measure-type-component-name data-records column-config]
  (into #{} (map-indexed (fn [row-index row]
                           (resolve-observation-measure measure-type-component-name row row-index column-config))
                         data-records)))

(defn- validate-no-measure-columns
  "qb:measureType cubes cannot contain columns which represent qb:measures. This function checks none of the
   column names within header-names are measure columns as declared in the columns configuration."
  [header-names column-config]
  (let [cube-measure-columns (set/intersection header-names (column-config/measures column-config))]
    (when (seq cube-measure-columns)
      (let [measure-titles (map #(column-config/component-name->title column-config %) cube-measure-columns)
            msg (format "Columns %s reference qb:measure components. Measure columns should not be declared for qb:measureType cubes"
                        (string/join ", " measure-titles))]
        (throw (ex-info msg {}))))))

(defn- parse-cube-configuration-rows [titles data-rows column-config]
  (let [header-component-names (resolve-component-names titles column-config)
        name-set (set header-component-names)
        mt-component-name (get-measure-type-component-name name-set column-config)
        measures (resolve-measures mt-component-name (tcsv/csv-records header-component-names data-rows) column-config)
        component-names (concat header-component-names measures)
        name->component (select-keys (column-config/name->component column-config) component-names)]
    (validate-no-measure-columns name-set column-config)
    {:titles                 titles
     :names                  header-component-names
     :type                   :measure-dimension
     :title->name            (into {} (map (juxt :title (comp keyword :name)) (vals name->component))) ;;TODO: add namespace for components?
     :name->component        name->component
     :dimensions             (set/intersection name-set (column-config/dimensions column-config))
     :attributes             (set/intersection name-set (column-config/attributes column-config))
     :measures               measures
     :measure-type-component mt-component-name
     :value-component        (get-value-component-name name-set column-config)}))

(defn get-cube-configuration
  "Returns a cube configuration for a source of observations and a columns configuration. Every column within
   observation-source must have a definition in column-config. Different cube types may impose additional
   restrictions on the columns allowed within the observations data."
  [observations-source column-config]
  (with-open [r (tcsv/reader observations-source)]
    (let [lines (csv/read-csv r)]
      (if (seq lines)
        (parse-cube-configuration-rows (first lines) (rest lines) column-config)
        (throw (RuntimeException. "No header row found in observations source"))))))

(defn values
  "Returns a set of the cube columns which represent observation values."
  [cube-config]
  #{(:value-component cube-config)})

(defn dimension-attribute-measure-columns
  "Returns a sequence of columns for the dimensions, attributes and measures defined within
   the associated cube."
  [{:keys [name->component dimensions attributes measures] :as cube-config}]
  (map name->component (concat dimensions attributes measures)))

(defn ordered-columns
  "Returns columns for the associated cube in the order they appear in the obseravtions header row."
  [{:keys [name->component names] :as cube-config}]
  (map name->component names))

(defn find-header-transformers
  "Returns a map of {column name -> transform fn} for each column in a cube configuration
   which declares a transformation to be applied to cells in the corresponding column."
  [{:keys [name->component] :as cube-config}]
  (let [components (map name->component (:names cube-config))]
    (->> components
         (filter (fn [comp] (some? (:value_transformation comp))))
         (map (juxt (comp keyword :name) :value_transformation))
         (into {}))))

(defn- validate-dimensions
  "Ensures that dimension columns have no missing values"
  [row dimensions]
  (doseq [[dim value] (select-keys row dimensions)]
    (if (gecs/blank? value)
      (throw (ex-info (str "Missing value for dimension: " dim)
                      {:row row})))))

(defn- validate-columns [record cube-config]
  (validate-dimensions record (:dimensions cube-config))
  record)

(defn transform-columns
  "Applies the specified column transforms to a row map"
  [row transformations]
  (reduce (fn [row [col f]] (update row col f)) row transformations))

(defn observation-records
  "Returns a sequence of transformed observation row maps for the given reader and cube configuration."
  [reader cube-config]
  (let [csv-records (csv/read-csv reader)
        data-records (rest csv-records)
        csv-records (tcsv/csv-records (:names cube-config) data-records)
        column-transforms (find-header-transformers cube-config)]
    (map (fn [record]
           (-> record
               (transform-columns column-transforms)
               (validate-columns cube-config)))
         csv-records)))

(defn write-observation-records
  "Writes a sequence of observation row records to a destination writer."
  [writer records cube-config]
  (tcsv/write-csv-rows writer (:names cube-config) records))

(defn get-ordered-dimension-names
  "Returns an ordered list of dimension names given an ordered list of components and a predicate indicating
   when the specified name corresponds to a dimension."
  [{:keys [names dimensions name->component] :as cube-config}]
  (let [ordered-dim-names (keep dimensions names)]
    (map :name (map name->component ordered-dim-names))))