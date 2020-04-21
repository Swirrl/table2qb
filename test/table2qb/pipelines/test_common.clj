(ns table2qb.pipelines.test-common
  (:require [clojure.java.io :as io]
            [clojure.test :refer [is]]
            [clojure.data :refer [diff]]
            [table2qb.configuration.columns :as column-config]
            [table2qb.configuration.uris :as uri-config]
            [grafter-2.rdf4j.repository :as repo]))

(defn- load-test-configuration []
  (column-config/load-column-configuration (io/file "test/resources/columns.csv")))

(def default-config (load-test-configuration))

(defn title->name [title]
  (if-let [k (column-config/title->key default-config title)]
    k
    (throw (ex-info (str "Unrecognised column: " title)
                    {:known-columns (column-config/known-titles default-config)}))))

(def test-domain "http://gss-data.org.uk/")
(def test-domain-def (uri-config/domain-def test-domain))
(def test-domain-data (uri-config/domain-data test-domain))

(defn first-by [attr val coll]
  "Finds first item in collection with attribute having value"
  (first (filter #(= val (attr %)) coll)))

(defn example [type name filename]
  (io/resource (str "./" name "/" type "/" filename)))

(def example-csv (partial example "csv"))
(def example-csvw (partial example "csvw"))

(defn maps-match? [a b]
  (let [[a-only b-only _] (diff a b)]
    (is (nil? a-only) "Found only in first argument: ")
    (is (nil? b-only) "Found only in second argument: ")))

(defn eager-select
  "Executes a SELECT query against the given repository and eagerly evaluates the resulting sequence."
  [repo select-query]
  (with-open [conn (repo/->connection repo)]
    (doall (repo/query conn select-query))))
