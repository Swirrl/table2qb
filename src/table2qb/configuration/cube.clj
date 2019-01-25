(ns table2qb.configuration.cube
  "Namespace for deriving a cube-specific configuration for an observations data source and a columns configuration.
   Once built, the cube configuration can be queried for the contained dimension/attribute/measures etc. and used
   to load transformed observation records from a conforming data source."
  (:require [table2qb.configuration.columns :as column-config]
            [table2qb.csv :as tcsv]
            [clojure.data.csv :as csv]
            [clojure.string :as string]
            [clojure.set :as set]
            [grafter.extra.cell.string :as gecs]
            [table2qb.configuration.column :as column]
            [table2qb.util :as util]))

;;TODO: move into configuration.columns namespace?
(defn- resolve-columns
  "Returns a sequence of component names of the corresponding observation column titles."
  [titles column-config]
  (let [title-column (map (fn [t] [t (column-config/title->column column-config t)]) titles)
        invalid-titles (map first (filter (fn [[_t n]] (nil? n)) title-column))]
    (if (seq invalid-titles)
      (throw (RuntimeException. (str "Unknown column titles: " (string/join ", " invalid-titles))))
      (mapv second title-column))))

(defn- get-measure-type-column-name
  "Finds the named column which corresponds to the qb:measureType given a set of observation column names
   and a columns configuration. At most one qb:measureType column should exist - if multiple columns
   exist, an exception will be thrown."
  [columns]
  (let [measure-type-columns (vec (filter column/is-qb-measure-type-column? columns))]
    (case (count measure-type-columns)
      0 nil
      1 (column/column-key (first measure-type-columns))
      (let [mt-column-titles (map column/column-title measure-type-columns)
            msg (format "Found multiple qb:measureType columns: %s. At most one qb:measureType column should be defined."
                        (string/join ", " ))]
        (throw (ex-info msg {:measure-type-columns mt-column-titles}))))))

(defn- get-value-component-name
  "Finds the named column containing observation values given a set of observation columns names and
  a columns configuration. Exactly one value column should exist - if no column or multiple columns
  exist, an exception will be thrown."
  [columns]
  (let [value-columns (vec (filter column/value-column? columns))]
    (case (count value-columns)
      0 (throw (ex-info "No value column defined" {}))
      1 (column/column-key (first value-columns))
      (let [value-column-titles (map column/column-title value-columns)
            msg (format "Found multiple value columns: %s. Exactly one value column should be defined."
                        value-column-titles)]
        (throw (ex-info msg {:value-columns value-column-titles}))))))

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

(defn- create-title->name [columns]
  (into {} (map (juxt column/column-title column/column-key) columns)))

(defn- get-dimensions [column-names column-config]
  (let [dimensions (set/intersection column-names (column-config/dimensions column-config))]
    (if (seq dimensions)
      dimensions
      (throw (ex-info "No dimension columns found. At least one dimension must be specified." {})))))

(defn- parse-multi-measure-cube-configuration [columns column-config]
  (let [header-column-names (map column/column-key columns)
        name-set (set header-column-names)
        measures (set/intersection name-set (column-config/measures column-config))
        values (set/intersection name-set (column-config/values column-config))
        attributes (set/intersection name-set (column-config/attributes column-config))
        name->column (util/map-by column/column-key columns)]
    (when (empty? measures)
      (throw (ex-info "Multi-measure cube must contain at least one measure column" {})))

    (when (seq values)
      (let [value-titles (map #(column-config/component-name->title column-config %) values)
            msg (format "Columns %s represent observation values. Multi-measure cubes should define measure values in the corresponding measure columns."
                        (string/join ", " value-titles))]
        (throw (ex-info msg {:value-columns value-titles}))))

    {:titles (mapv column/column-title columns)
     :names header-column-names
     :type :multi-measure
     :title->name (create-title->name columns)
     :name->component name->column
     :dimensions (get-dimensions name-set column-config)
     :attributes attributes
     :measures measures}))

(defn- parse-measure-type-cube-configuration [columns mt-column-name data-rows column-config]
  (let [column-keys (map column/column-key columns)
        name-set (set column-keys)
        measures (resolve-measures mt-column-name (tcsv/csv-records column-keys data-rows) column-config)
        component-names (concat column-keys measures)
        name->component (select-keys (column-config/name->component column-config) component-names)]
    (validate-no-measure-columns name-set column-config)
    {:titles                 (mapv column/column-title columns)
     :names                  column-keys
     :type                   :measure-dimension
     :title->name            (create-title->name columns)
     :name->component        name->component
     :dimensions             (get-dimensions name-set column-config)
     :attributes             (set/intersection name-set (column-config/attributes column-config))
     :measures               measures
     :measure-type-component mt-column-name
     :value-component        (get-value-component-name columns)}))

(defn- parse-cube-configuration-rows [titles data-rows column-config]
  (let [header-columns (resolve-columns titles column-config)
        mt-column-name (get-measure-type-column-name header-columns)]
    (if (nil? mt-column-name)
      (parse-multi-measure-cube-configuration header-columns column-config)
      (parse-measure-type-cube-configuration header-columns mt-column-name data-rows column-config))))

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

(defmulti values
          "Returns a set of the cube columns which represent observation values."
          (fn [cube-config] (:type cube-config)))

(defmethod values :measure-dimension [cube-config]
  #{(:value-component cube-config)})

(defmethod values :multi-measure [_cube-config]
  #{})

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
  [cube-config]
  (->> (ordered-columns cube-config)
       (filter (fn [col] (some? (column/value-transformation col))))
       (map (juxt column/column-key :value_transformation))
       (into {})))

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
  "Returns an ordered list of dimension column names"
  [{:keys [names dimensions name->component] :as cube-config}]
  (let [ordered-dim-names (keep dimensions names)]
    (map column/column-name (map name->component ordered-dim-names))))