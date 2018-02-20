(ns table2qb.core-test
  (:require [clojure.test :refer :all]
            [table2qb.core :refer :all]
            [clojure.java.io :as io])
  (:import [java.io StringWriter]))

(defn example [filename]
  (str "./test/resources/trade-example/" filename))

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
            (with-open [target-reader (io/reader (example "components.csv"))]
              (is (= (set (read-csv target-reader))
                     (set components)))))
          (testing "serialised contents match"
            (with-open [target-reader (io/reader (example "components.csv"))]
              (let [string-writer (StringWriter.)]
                (write-csv string-writer (sort-by :component_slug components))
                (is (= (slurp target-reader)
                       (str string-writer)))))))))))
