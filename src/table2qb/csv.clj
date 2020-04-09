(ns table2qb.csv
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [table2qb.util :as util]
            [clojure.data :refer [diff]]
            [clojure.string :as string]
            [clojure.set :as set])
  (:import [org.apache.commons.io.input BOMInputStream]))

(defn csv-maps [header-keys data-rows]
  (map (fn [row] (zipmap header-keys row)) data-rows))

(defn read-csv-maps
  [reader]
  "Reads csv into seq of hashes, with mapping from headers to keys"
  (let [csv-data (csv/read-csv reader)
        header-row (first csv-data)
        header-keys (map keyword header-row)]
    (csv-maps header-keys (rest csv-data))))

(defn write-csv [writer data]
  (csv/write-csv writer
                 (cons (map name (keys (first data)))
                       (map vals data))))

(defn write-csv-rows [writer column-keys data-rows]
  (let [header (map name column-keys)
        extract-cells (apply juxt column-keys)
        data-records (map extract-cells data-rows)]
    (csv/write-csv writer (cons header data-records))))

(defn reader [source]
  "Returns a reader to the file contents with the BOM (if present) removed"
  (-> source io/input-stream BOMInputStream. io/reader))

(defn read-all-csv-maps
  "Eagerly reads a collection of CSV record maps from the given CSV data source."
  [csv-source]
  (with-open [r (reader csv-source)]
    (doall (read-csv-maps r))))

(s/def ::title string?)
(s/def ::key keyword?)
(s/def ::validate (s/coll-of fn?))
(s/def ::transform fn?)
(s/def ::default (s/or :literal string? :derive fn?))
(s/def ::required? boolean?)
(s/def ::Column (s/keys :req-un [::title ::key] :opt-un [::validate ::transform ::default ::required?]))

(defn- is-required?
  "Whether the column is required in a compatible CSV input file."
  [column]
  (boolean (:required column)))

(defn- has-default?
  "Whether the column defines a default value"
  [column]
  (contains? column :default))

(defn build-column-model
  "Builds a structure representing the specified column definitions."
  [columns]
  {:required      (set (map :title (filter is-required? columns)))
   :titles        (set (map :title columns))
   :title->column (util/map-by :title columns)})

(defn get-cell-default
  "Gets the default value for a column not present in a CSV input file."
  [{:keys [default] :as column} partial-row]
  {:pre [(has-default? column)]}
  (cond
    (string? default) default
    (fn? default) (default partial-row)
    :else nil))

(defn validate-header
  "Checks a CSV header row is valid according to a column specification. The header is valid if:
   * All specified column names are unique
   * All required columns are present
   * All column names in the header are defined within the column specification (i.e. no unknown columns)."
  [header-row {:keys [required titles] :as column-model}]
  (let [declared-columns (set header-row)
        duplicate-columns (->> header-row
                               (frequencies)
                               (keep (fn [kvp] (when (> (val kvp) 1)
                                                 (key kvp)))))
        missing-required (set/difference required declared-columns)
        unknown (set/difference declared-columns titles)]

    (when (seq duplicate-columns)
      (throw (ex-info (str "Duplicate column headers: " (string/join ", " duplicate-columns))
                      {:type :duplicate-csv-columns
                       :duplicate-columns duplicate-columns})))

    (when (seq missing-required)
      (throw (ex-info (str "Missing required columns: " (string/join ", " missing-required))
                      {:type :missing-csv-columns
                       :missing-columns missing-required})))

    (when (seq unknown)
      (throw (ex-info (str "Unexpected columns: " (string/join ", " unknown))
                      {:type :unknown-csv-columns
                       :unknown-columns unknown})))

    declared-columns))

(defn- merge-column-defaults [declared-row default-columns]
  (let [default-kvps (map (fn [col] [(:key col) (get-cell-default col declared-row)]) default-columns)]
    (into declared-row default-kvps)))

(defn get-cell-value
  "Resolves a cell's value from its string input value. First any validate functions associated with the
   column are executed (discarding any return values). Finally the column transform function is run if
   specified to yield the effective value. If no transform is present the string input value is returned."
  [row {:keys [transform validate] :as column} string-value]
  (doseq [vf validate]
    (vf row column string-value))

  (if (nil? transform)
    string-value
    (transform row column string-value)))

(defn- parse-declared-row [row-number ordered-columns cells]
  (into {} (map (fn [col raw-cell-value]
                  [(:key col) (get-cell-value row-number col raw-cell-value)])
                ordered-columns
                cells)))

(defn parse-row
  "Parses the row at the given index given a sequence of cell values and the corresponding
   columns from the column specification. Also takes a collection of default-columns which are
   optional columns in the column specification which are not present in the input CSV. Returns
   a map of column keys to the resolved cell values."
  [row-index cells ordered-columns default-columns]
  (let [declared-row (parse-declared-row (inc row-index) ordered-columns cells)]
    (merge-column-defaults declared-row default-columns)))

(defn read-csv-records
  "Reads a lazy sequence of row records from a reader according to a collection of column
   specifications. Each column specification should be a map with the following keys:
     :title - Required heading name identifying the column in the input
     :key - Required key to associate the resolved cell value to in each row map
     :required? - Optional boolean specifying whether the column is required in the input CSV
     :validate - Optional collection of validator functions for cell values within the column
     :transform - Optional transform function for cell values within the column
     :default - Optional literal value or derivation function for optional columns which are not present
                in the input data. Derivation functions take a single parameter for the partial row
                map constructed from the required column values."
  [reader columns]
  (let [{:keys [titles title->column] :as column-model} (build-column-model columns)
        csv-rows (csv/read-csv reader)]
    (if-let [header-row (first csv-rows)]
      (let [declared-columns (validate-header header-row column-model)
            ordered-columns (map title->column header-row)
            missing-optional (set/difference titles declared-columns)
            default-columns (filter has-default? (map title->column missing-optional))]
        (map-indexed (fn [row-index cells]
                       (parse-row row-index cells ordered-columns default-columns))
                     (next csv-rows))))))

;;validators
(defn- cell-validation-message [row-number {:keys [title] :as column} msg]
  (format "Invalid cell in column %s row %d: %s" title row-number msg))

(defn throw-cell-validation-error [row-number column msg data]
  (throw (ex-info (cell-validation-message row-number column msg) data)))

(defn- cell-enum-validation-message [row-number column options]
  (let [msg (str "Expected one of: " (string/join ", " options))]
    (cell-validation-message row-number column msg)))

(defn- throw-cell-enum-validation-error [row-number column options]
  (throw (ex-info (cell-enum-validation-message row-number column options) {:options options})))

(defn validate-not-blank
  "Validates the string s is not blank."
  [row column s]
  (if (string/blank? s)
    (throw-cell-validation-error row column "Value cannot be blank" {})
    s))

(defn optional
  "Wraps the given validator with one which returns nil or the specified
   default if the input cell value is blank. If a value is present, validates
   it with validator."
  ([validator] (optional validator nil))
  ([validator default]
   (fn [row column s]
     (if (string/blank? s)
       default
       (validator row column s)))))

(defn validate-one-of
  "Returns a validator which checks the cell value is a member of the specified set of permitted values."
  [options]
  {:pre [(set? options)]}
  (fn [row column s]
    (if (contains? options s)
      s
      (throw-cell-enum-validation-error row column options))))

(defn validate-mapping
  "Returns a validator which checks the cell value is a key in the specified map and returns the corresponding
   value."
  [mapping]
  (fn [row column s]
    (let [v (get mapping s ::missing)]
      (if (= ::missing v)
        (throw-cell-enum-validation-error row column (keys mapping))
        v))))