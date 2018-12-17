(ns table2qb.configuration-test
  (:require [clojure.test :refer :all]
            [table2qb.configuration :refer :all]
            [table2qb.util :refer [exception?]]))

(deftest configuration-row->column-test
  (testing "var names"
    (testing "must be provided"
      (let [r (configuration-row->column 1 {:name ""})]
        (is (instance? Exception r))
        (is (= :blank-name (:type (ex-data r))))))

    (testing "should not contain hyphens"
      (let [r (configuration-row->column 1 {:name "my-column"})]
        (is (instance? Exception r))
        (is (= :invalid-name (:type (ex-data r))))))

    (testing "may have underscores"
      (is (= {:name "my_column"} (configuration-row->column 1 {:name "my_column"})))))

  (testing "transformations"
    (testing "Empty transformation"
      (let [r (configuration-row->column 1 {:name "col" :value_transformation "    "})]
        (is (map? r))
        (is (nil? (:value_transformation r)))))

    (testing "Valid transformation"
      (let [{tf :value_transformation :as r} (configuration-row->column 1 {:name "col" :value_transformation "slugize"})]
        (is (map? r))
        (is (fn? tf))))

    (testing "Invalid transformation"
      (let [r (configuration-row->column 1 {:name "col" :value_transformation "invalid"})]
        (is (exception? r))
        (is (= :invalid-transform (:type (ex-data r))))))))

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