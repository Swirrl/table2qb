(ns table2qb.util-test
  (:require [table2qb.util :refer :all]
            [clojure.test :refer :all]
            [clojure.java.io :as io]))

(deftest csvw-url-test
  (testing "specifies a path to the csv")
    (testing "as an absolute path when directory is specified absolutely"
      (is (= "file:/absolute/file.csv"
             (str (csvw-url "/absolute/" "file.csv")))))
    (testing "relative to the json when the output directory is specified relatively"
      (is (= "file.csv"
             (str (csvw-url "./relative/" "file.csv"))))))
