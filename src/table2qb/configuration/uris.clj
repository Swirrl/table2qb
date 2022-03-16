(ns table2qb.configuration.uris
  (:require [table2qb.util :as util]))

(defn domain-data [domain]
  (str domain "data/"))

(defn expand-uri-template [template substitutions]
  (reduce (fn [t [var-key value]]
            (.replace t (str "$(" (name var-key) ")") value))
          template
          substitutions))

(defn expand-uris [uris substitutions]
  (letfn [(expand-template [t] (expand-uri-template t substitutions))]
    (util/map-values (fn [v]
                       (if (coll? v)
                         (into (empty v) (map expand-template v))
                         (expand-template v)))
                     uris)))

(defn strip-trailing-path-separator [uri-str]
  (if (.endsWith uri-str "/")
    (.substring uri-str 0 (dec (.length uri-str)))
    uri-str))

(defn merge-uris
  "Merges a base definition for URIs with a user definition. The user definition may only
   override some of the definitions in the base and any unknown definitions are ignored."
  [base-uris user-uris]
  (select-keys (merge base-uris user-uris) (keys base-uris)))

(defn resolve-uri-defs [base-source user-source]
  (let [base-uris (util/read-edn base-source)
        user-uris (when user-source (util/read-edn user-source))]
    (merge-uris base-uris user-uris)))

(defn format-uris
  "Formats the values of a URI map as strings suitable for display in the UI"
  [uris]
  (util/map-values pr-str uris))