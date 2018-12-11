(ns table2qb.csv
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io])
  (:import [org.apache.commons.io.input BOMInputStream]))

(defn csv-records [header-keys data-rows]
  (map (fn [row] (zipmap header-keys row)) data-rows))

(defn csv-rows
  "Returns a lazy sequence of CSV row records given a header row, data row and row heading->key name mapping."
  [header-row data-rows header-mapping]
  (let [header-keys (map header-mapping header-row)]
    (csv-records header-keys data-rows)))

(defn read-csv
  ([reader]
   "Reads converting headers to keywords"
   (read-csv reader keyword))
  ([reader header-mapping]
   "Reads csv into seq of hashes, with mapping from headers to keys"
   (let [csv-data (csv/read-csv reader)]
     (csv-rows (first csv-data) (rest csv-data) header-mapping))))

(defn write-csv [writer data]
  (csv/write-csv writer
                 (cons (map name (keys (first data)))
                       (map vals data))))

(defn write-csv-rows [writer column-keys data-rows]
  (let [header (map name column-keys)
        extract-cells (apply juxt column-keys)
        data-records (map extract-cells data-rows)]
    (csv/write-csv writer (cons header data-records))))

(defn reader [filename]
  "Returns a reader to the file contents with the BOM (if present) removed"
  (-> filename io/input-stream BOMInputStream. io/reader))

(defn read-header-row
  "Reads the header row from the given CSV data source."
  [csv-source]
  (with-open [r (reader csv-source)]
    (first (csv/read-csv r))))
