(ns table2qb.source
  "The csv2rdf RowSource protocol represents a source of a read-once sequence of logical CSV records. The source
   must be re-opened if multiple iterations are required. The pipelines defined in table2qb.core sometimes need to
   modify or derive additional data from the raw input files - the sources defined in this namespace allow the CSV
   files input into the pipelines to be transformed before being presented to the CSVW process."
  (:require [csv2rdf.metadata.dialect :as dialect]
            [csv2rdf.source :as source]
            [clojure.string :as string]
            [csv2rdf.tabular.csv.reader :as reader]))

(defn make-row
  "Creates a CSV record in the format expected by csv2rdf. cells should be a vector of strings in the same order
  as the declared column headings. Row numbers are indexed from 1."
  [row-number cells]
  {:source-row-number row-number
   :content           (string/join ", " cells)
   :comment           nil
   :type              :data
   :cells             cells})

(defn column-keys->header-row
  "Creates the header record for a given collection of column keywords."
  [column-keys]
  (make-row 1 (mapv name column-keys)))

(defn make-data-row-transformer
  "Creates a mapping function for CSV records. input-column-keys contains the key names for the corresponding
   cell values in the row - this should match the number of columns in the input file. output-column-keys
   contains the names of the columns in the output - these should match the columns defined in the corresponding
   metadata file. transform-fn is a function from source row map to result row map - the keys in the source map
   match those defined in input-column-keys. The result map should contain all of the keys specified in
   output-column-keys."
  [input-column-keys output-column-keys transform-fn]
  (fn [{:keys [cells] :as row}]
    (let [source-map (zipmap input-column-keys cells)
          result-map (transform-fn source-map)
          result-cells (mapv result-map output-column-keys)]
      (assoc row :cells result-cells))))

(defn open-transformed-rows
  "Opens the given tabular source and transforms each data row according to the given transform function.
   The source file should contain a single header row. column-header-mapping should be a map from source
   column names in the input data to keys to map the corresponding cell value to in the map provided
   to transform-fn. output-column-keys should contain the keys for the non-virtual columns defined in
   the corresponding metadata file."
  ([tabular-file dialect column-header-mapping output-column-keys]
   (open-transformed-rows tabular-file dialect column-header-mapping output-column-keys identity))
  ([tabular-file dialect column-header-mapping output-column-keys transform-fn]
   (let [{:keys [options rows]} (reader/read-tabular-source tabular-file dialect)
         rows (reader/row-contents->rows rows options)
         headers (:cells (first rows))                          ;;TODO: handle empty input file
         input-column-keys (mapv column-header-mapping headers)
         header-row (column-keys->header-row output-column-keys)
         row-transform-fn (make-data-row-transformer input-column-keys output-column-keys transform-fn)]
     {:options options
      :rows    (cons header-row (map row-transform-fn (drop 1 rows)))})))

;;represents a source of CSV records for an in-memory sequence of data maps. uri is the logical URI of
;;the tabular source. input-column-keys contains the keys for the non-virtual columns defined in the
;;corresponding metadata file. Each of keys in input-column-keys should exist on each map within rows.
(defrecord MemoryRowSource [uri input-column-keys rows]
  source/URIable
  (->uri [_this] uri)

  reader/RowSource
  (open-rows [_this dialect]
    (let [options (dialect/dialect->options dialect)
          header (column-keys->header-row input-column-keys)
          data-rows (map-indexed (fn [row-index row]
                                   (let [cells (mapv (fn [k] (get row k "")) input-column-keys)
                                         ;;row numbers start at 1, plus header row
                                         row-number (+ 2 row-index)]
                                     (make-row row-number cells)))
                                 rows)]
      {:options options
       :rows (cons header data-rows)})))

;;represents a source of CSV records which are loaded and then transformed from the given tabular data
;;source. The column heading are mapped to key using input-column-mapping - this mapping is used to
;;construct a map from key to the corresponding cell value for every row in the input. The row is
;;transformed using transform-fn and then projected into a sequence of cell values according to
;;output-column-keys. The keys in output-column-keys should correspond to the names of the columns
;;in the associated metadat file.
(defrecord TransformingRowSource [tabular-file input-column-mapping output-column-keys transform-fn]
  source/URIable
  (->uri [_this] (.toURI tabular-file))

  reader/RowSource
  (open-rows [_this dialect]
    (open-transformed-rows tabular-file dialect input-column-mapping output-column-keys transform-fn)))

(defn header-replacing-source
  "Returns a RowSource for a tabular file which renames the source column headers according to
  input-header-mapping. output-column-keys defines the order of the mapped columns within the
  output sequence - these should match the order defined in the corresponding metadata file."
  [tabular-file input-header-mapping output-column-keys]
  (->TransformingRowSource tabular-file input-header-mapping output-column-keys identity))

(defn get-rows [source]
  (:rows (reader/open-rows source dialect/default-dialect)))