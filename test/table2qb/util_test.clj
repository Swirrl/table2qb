(ns table2qb.util-test
  (:require [clojure.test :refer :all]
            [table2qb.util :refer :all]))

(deftest target-order-test
  (let [v [:a :b :c :d]
        f (target-order v)]
    (testing "Exists in collection"
      (is (= 1 (f :b))))

    (testing "Does not exist in collection"
      (is (= (count v) (f :x))))))
