(ns table2qb.core-test
  (:require [clojure.test :refer :all]
            [table2qb.core :refer :all]
            [clojure.java.io :as io]))

(defn example [filename]
  (str "./test/resources/trade-example/" filename))

(deftest components-test
  (testing "returns a dataset of components"
    (with-open [input-reader (io/reader (example "input.csv"))]
      (let [components (doall (components input-reader))]
        (testing "one row per component"
          (is (= 8 (-> components count))))))))
