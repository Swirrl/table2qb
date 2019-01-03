(ns table2qb.pipelines.codelist-test
  (:require [clojure.test :refer :all]
            [table2qb.csv :refer [reader]]
            [table2qb.pipelines.codelist :refer :all]
            [table2qb.pipelines.test-common :refer [example-csvw example-csv maps-match? test-domain]]
            [csv2rdf.util :as util]))

(deftest codelists-test
  (testing "minimum case"
    (testing "csv table"
      (with-open [input-reader (reader (example-csv "regional-trade" "flow-directions.csv"))]
        (let [codes (doall (codes input-reader))]
          (testing "one row per code"
            (is (= 2 (count codes)))))))
    (testing "json metadata"
      (maps-match? (util/read-json (example-csvw "regional-trade" "flow-directions.json"))
                   (codelist-metadata
                     "flow-directions-codelist.csv"
                     "Flow Directions Codelist"
                     (get-uris test-domain "flow-directions")))))
  (testing "with optional fields"
    (testing "csv table"
      (with-open [input-reader (reader (example-csv "regional-trade" "sitc-sections.csv"))]
        (let [codes (doall (codes input-reader))]
          (testing "one column per attribute"
            (is (= [:label :notation :parent_notation :sort_priority :description :top_concept_of :has_top_concept :pref_label]
                   (-> codes first keys))))
          (testing "column for sort-priority"
            (is (= "0" (-> codes first :sort_priority))))
          (testing "column for description"
            (is (= "lorem ipsum" (-> codes first :description)))))
        (testing "json metadata"
          (maps-match? (util/read-json (example-csvw "regional-trade" "sitc-sections.json"))
                       (codelist-metadata
                         "sitc-sections-codelist.csv"
                         "SITC Sections Codelist"
                         (get-uris test-domain "sitc-sections"))))))))
