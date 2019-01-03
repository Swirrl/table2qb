(ns table2qb.configuration.uris
  (:require [csv2rdf.util :as util]))

(defn domain-data [domain]
  (str domain "data/"))

(defn expand-uri-template [template substitutions]
  (reduce (fn [t [var-key value]]
            (.replace t (str "$(" (name var-key) ")") value))
          template
          substitutions))

(defn expand-uris [uris substitutions]
  (util/map-values (fn [t] (expand-uri-template t substitutions)) uris))

(defn strip-trailing-path-separator [uri-str]
  (if (.endsWith uri-str "/")
    (.substring uri-str 0 (dec (.length uri-str)))
    uri-str))
