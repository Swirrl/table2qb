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
              (is (= (read-json target-reader)
                     (component-metadata
                      "regional-trade.slugged.normalised.csv"
                      "Regional Trade Component Specifications"
                      "regional-trade"))))))))))

(deftest structure-test
  (testing "compare with structure.json"
    (with-open [target-reader (io/reader (example "structure.json"))]
      (maps-match? (read-json target-reader)
                      (structure-metadata
                       "regional-trade.slugged.normalised.csv"
                       "Regional Trade"
                       "regional-trade")))))
