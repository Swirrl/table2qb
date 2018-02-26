(ns table2qb.core-test
  (:require [clojure.test :refer :all]
            [table2qb.core :refer :all]
            [clojure.java.io :as io]
            [clojure.data :refer [diff]])
  (:import [java.io StringWriter]))

(defn example [filename]
  (str "./test/resources/trade-example/" filename))

(defn maps-match? [a b]
  (let [[a-only b-only _] (diff a b)]
    (is (nil? a-only) "Found only in first argument: ")
    (is (nil? b-only) "Found only in second argument: ")))

(deftest components-test
  (testing "returns a dataset of components"
    (with-open [input-reader (io/reader (example "input.csv"))]
      (let [components (doall (components input-reader))]
        (testing "one row per component"
          (is (= 8 (-> components count))))
        (testing "geography component"
          (let [{:keys [:component_attachment :component_property]}
                (first (filter #(= (:component_slug %) "geography") components))]
            (is (= component_attachment "qb:dimension"))
            (is (= component_property "http://purl.org/linked-data/sdmx/2009/dimension#refArea"))))
        (testing "compare with components.csv"
          (testing "parsed contents match"
            (with-open [target-reader (io/reader (example "component-specifications.csv"))]
              (is (= (set (read-csv target-reader))
                     (set components)))))
          (testing "serialised contents match"
            (with-open [target-reader (io/reader (example "component-specifications.csv"))]
              (let [string-writer (StringWriter.)]
                (write-csv string-writer (sort-by :component_slug components))
                (is (= (slurp target-reader)
                       (str string-writer)))))))
        (testing "compare with components.json"
          (testing "parsed contents match"
            (with-open [target-reader (io/reader (example "component-specifications.json"))]
              (maps-match? (read-json target-reader)
                           (components-metadata
                            "regional-trade.slugged.normalised.csv"
                            "Regional Trade Component Specifications"
                            "regional-trade")))))))))

(deftest structure-test
  (testing "compare with structure.json"
    (with-open [target-reader (io/reader (example "structure.json"))]
      (maps-match? (read-json target-reader)
                      (structure-metadata
                       "regional-trade.slugged.normalised.csv"
                       "Regional Trade"
                       "regional-trade")))))

(defn order-columns [m]
  (update-in m ["tableSchema" "columns"] (partial sort-by #(get % "name"))))

(deftest observations-test
  (testing "sequence of observations"
    (with-open [input-reader (io/reader (example "input.csv"))]
      (let [observations (doall (observations input-reader))]
        (testing "one observation per row"
          (is (= 44 (count observations))))
        (let [observation (first observations)]
          (testing "one column per component"
            (is (= 7 (count observation))))
          (testing "slugged columns"
            (are [expected actual] (= expected actual)
              "gbp-total" (:measure_type observation)
              "gbp-million" (:unit observation)
              "0-food-and-live-animals" (:sitc_section observation)
              "export" (:flow observation)))))))
  (testing "observation metadata"
    (with-open [input-reader (io/reader (example "input.csv"))
                target-reader (io/reader (example "observations-metadata.json"))]
      (maps-match? (order-columns (read-json target-reader))
                   (order-columns (observations-metadata input-reader
                                                         "regional-trade.slugged.csv"
                                                         "regional-trade"))))))

;; TODO: initial creation of reference data: components and codelists
;; TODO: codes-used list
;; TODO: dataset
;; TODO: slugizers vs code-specifier vs curie
