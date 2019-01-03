(ns table2qb.util
  (:require [csv2rdf.source :as source]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.edn :as edn])
  (:import [java.io File PushbackReader Reader]
           [java.net URI]))

(defn exception? [x] (instance? Exception x))

(defn map-values
  "Transforms each value within a map with the given transform function. Returns a new map."
  [m f]
  (into {} (map (fn [[k v]] [k (f v)]) m)))

(defn filter-vals
  "Filters a map according to the given predicate on values."
  [val-p m]
  (into {} (filter (comp val-p val) m)))

(defn map-by [f s]
  (into {} (map (fn [v] [(f v) v]) s)))

(defn csv-file->metadata-uri [csv-file-str]
  (let [meta-file (str csv-file-str "meta.json")]
    (URI. meta-file)))

(defn create-metadata-source [csv-file-str metadata-json]
  (let [meta-uri (csv-file->metadata-uri csv-file-str)]
    (source/->MapMetadataSource meta-uri metadata-json)))

(defn tempfile [filename extension]
  (File/createTempFile filename extension))

(defn read-json
  "Reads a JSON document from an io/IOFactory source"
  [json-source]
  (with-open [r (io/reader json-source)]
    (json/read r)))

(defn- ^PushbackReader ensure-pushback-reader [^Reader reader]
  (if (instance? PushbackReader reader)
    reader
    (PushbackReader. reader)))

(defn ^PushbackReader ->pushback-reader
  "Opens the given IOFactory source as a PushbackReader."
  [io-source]
  (-> io-source io/reader ensure-pushback-reader))

(defn read-edn
  "Reads EDN from an IOFactory source."
  [edn-source]
  (with-open [r (->pushback-reader edn-source)]
    (edn/read r)))

(defn blank->nil
  "Returns the input value if it is not blank, otherwise nil."
  [value]
  (if-not (string/blank? value)
    value))
