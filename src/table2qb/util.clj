(ns table2qb.util
  (:require [csv2rdf.source :as source]
            [clojure.java.io :as io])
  (:import [java.io File]))

(defn exception? [x] (instance? Exception x))

(defn map-values [m f]
  (into {} (map (fn [[k v]] [k (f v)]) m)))

(defn target-order [s]
  "Returns a function for use with sort-by which returns an index of an
  element according to a target sequence, or an index falling after the target
  (i.e. putting unrecognised elements at the end)."
  (let [item->index (zipmap s (range))]
    (fn [element]
      (get item->index element (count s)))))

(defn csv-file->metadata-uri [csv-file]
  (.resolve (.toURI csv-file) "meta.json"))

(defn create-metadata-source [csv-file-str metadata-json]
  (let [meta-uri (csv-file->metadata-uri (io/file csv-file-str))]
    (source/->MapMetadataSource meta-uri metadata-json)))

(defn tempfile [filename extension]
  (File/createTempFile filename extension))

