(ns table2qb.csv-test
  (:require  [clojure.test :refer :all]
             [table2qb.csv :refer :all]))

(deftest bom-disposal-test
  (testing "The reader should remove a leading BOM if present"
    (with-open [rdr (reader "test/resources/bom-example.csv")]
      (let [data (read-csv rdr)
            first-column-heading (-> data first keys first)]
        (is (= :Label first-column-heading)))))) 
               
