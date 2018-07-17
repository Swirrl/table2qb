(ns table2qb.pipelines.test-common
  (:require [clojure.java.io :as io]
            [clojure.test :refer [is]]
            [clojure.data :refer [diff]]
            [table2qb.configuration :as config]))

(defn- load-test-configuration []
  (config/load-column-configuration (io/resource "columns.csv")))

(def default-config (load-test-configuration))

(defn title->name [title]
  (config/title->name default-config title))

(def test-domain "http://gss-data.org.uk/")
(def test-domain-def (config/domain-def test-domain))
(def test-domain-data (config/domain-data test-domain))

(defn first-by [attr val coll]
  "Finds first item in collection with attribute having value"
  (first (filter #(= val (attr %)) coll)))

(defn example [type name filename]
  (str "./examples/" name "/" type "/" filename))

(def example-csv (partial example "csv"))
(def example-csvw (partial example "csvw"))

(defn maps-match? [a b]
  (let [[a-only b-only _] (diff a b)]
    (is (nil? a-only) "Found only in first argument: ")
    (is (nil? b-only) "Found only in second argument: ")))


