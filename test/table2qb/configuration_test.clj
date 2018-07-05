(ns table2qb.configuration-test
  (:require [clojure.test :refer :all]
            [table2qb.configuration :refer :all]
            [clojure.string :as string]))

(deftest configuration-row->column-test
  (testing "var names"
    (testing "must be provided"
      (let [r (configuration-row->column 1 {:name ""})]
        (is (instance? Exception r))
        (is (string/includes? (.getMessage r) "csvw:name cannot be blank"))))

    (testing "should not contain hyphens"
      (let [r (configuration-row->column 1 {:name "my-column"})]
        (is (instance? Exception r))
        (is (string/includes? (.getMessage r) "cannot contain hyphens"))))

    (testing "may have underscores"
      (is (= {:name "my_column"} (configuration-row->column 1 {:name "my_column"}))))))

(deftest identify-columns-test
  (let [conventions {:my-dim {:component_attachment "qb:dimension"}
                     :my-att {:component_attachment "qb:attribute"}}
        dimension? (identify-columns conventions "qb:dimension")
        attribute? (identify-columns conventions "qb:attribute")]
    (is (dimension? :my-dim))
    (is (not (dimension? :my-att)))
    (is (not (dimension? :unknown)))
    (is (attribute? :my-att))
    (is (not (attribute? :my-dim)))
    (is (not (attribute? :unknown)))))