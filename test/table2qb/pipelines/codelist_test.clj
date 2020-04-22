(ns table2qb.pipelines.codelist-test
  (:require [clojure.test :refer :all]
            [table2qb.csv :refer [reader]]
            [table2qb.pipelines.codelist :refer :all :as codelist]
            [table2qb.pipelines.test-common :refer [example-csvw example-csv maps-match? test-domain-def eager-select
                                                    add-csvw]]
            [table2qb.util :as util]
            [grafter-2.rdf4j.repository :as repo]
            [grafter-2.rdf.protocols :as pr]
            [clojure.java.io :as io])
  (:import [java.net URI]))

(defn- read-codes [csv-source]
  (with-open [r (reader csv-source)]
    (doall (codes r))))

(deftest codelists-test
  (testing "minimum case"
    (testing "csv table"
      (let [codes (read-codes (example-csv "regional-trade" "flow-directions.csv"))]
        (testing "one row per code"
          (is (= 2 (count codes))))))
    (testing "json metadata"
      (maps-match? (util/read-json (example-csvw "regional-trade" "flow-directions.json"))
                   (codelist-metadata
                     "flow-directions-codelist.csv"
                     test-domain-def
                     "Flow Directions Codelist"
                     "flow-directions"))))

  (testing "with optional fields"
    (testing "csv table"
      (let [codes (read-codes (example-csv "regional-trade" "sitc-sections.csv"))]
        (testing "one column per attribute"
          (is (= [:label :notation :parent_notation :sort_priority :description :top_concept_of :has_top_concept :pref_label]
                 (-> codes first keys))))
        (testing "column for sort-priority"
          (is (= "0" (-> codes first :sort_priority))))
        (testing "column for description"
          (is (= "lorem ipsum" (-> codes first :description))))))
    (testing "json metadata"
      (maps-match? (util/read-json (example-csvw "regional-trade" "sitc-sections.json"))
                   (codelist-metadata
                    "sitc-sections-codelist.csv"
                    test-domain-def
                    "SITC Sections Codelist"
                    "sitc-sections"))))

  (testing "input validation"
    (testing "required columns"
      (is
        (= #{"Label"}
           (try
             (codes (io/reader (char-array "column-a\nvalue-1")))
             (catch Exception e (:missing-columns (ex-data e)))))))))

(deftest codelist-pipeline-test
  (let [codelist-csv (example-csv "regional-trade" "flow-directions.csv")
        codelist-name "Flow directions"
        codelist-slug "flow-directions"
        base-uri (URI. "http://example.com/")
        codelist-uri "http://example.com/def/concept-scheme/flow-directions"
        repo (repo/sail-repo)]
    (with-open [conn (repo/->connection repo)]
      (add-csvw conn codelist/codelist-pipeline {:codelist-csv codelist-csv
                                    :codelist-name             codelist-name
                                    :codelist-slug             codelist-slug
                                    :base-uri                  base-uri}))

    (testing "codelist title and label"
      (let [q (str "PREFIX dc: <http://purl.org/dc/terms/>"
                   "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                   "SELECT ?title ?label WHERE {"
                   "  <" codelist-uri "> dc:title ?title ;"
                   "                     rdfs:label ?label ."
                   "}")
            {:keys [title label] :as binding} (first (eager-select repo q))]
        (is (= codelist-name (str title)))
        (is (= codelist-name (str label)))))

    (testing "codelist type"
      (let [q (str "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>"
                   "ASK WHERE {"
                   "  <" codelist-uri "> a skos:ConceptScheme ."
                   "}")]

        (with-open [conn (repo/->connection repo)]
          (is (repo/query conn q)))))))
