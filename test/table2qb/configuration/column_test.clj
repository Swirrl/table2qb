(ns table2qb.configuration.column-test
  (:require [clojure.test :refer :all]
            [table2qb.configuration.column :refer :all]))

(deftest validate-name-test
  (testing "Valid"
    (let [n "valid_name"]
      (is (= n (validate-name 1 {:title "name"} n)))))

  (testing "Invalid"
    (let [n "invalid-name"]
      (is (thrown? Exception (validate-name 1 {:title "name"} n))))))

(deftest validate-column-type-test
  (testing "Valid"
    (are [input expected] (= expected (validate-column-type 1 {:title "component_attachment"} input))
      "" :value
      "     " :value
      "qb:dimension" :dimension
      "qb:attribute" :attribute
      "qb:measure" :measure))

  (testing "Invalid"
    (is (thrown? Exception (validate-column-type 1 {:title "component_attachment"} "invalid")))
    (is (thrown-with-msg? Exception #"Value must be blank or one of qb:dimension, qb:measure or qb:attribute"
                          (validate-column-type 1 {:title "component_attachment"} "qb:dimension  ")))))

(deftest validate-csvw-datatype-test
  (testing "Valid"
    (is (= "datetime" (validate-csvw-datatype 2 {:title "datatype"} "datetime"))))

  (testing "Invalid"
    (is (thrown? Exception (validate-csvw-datatype 2 {:title "datatype"} "stonks")))))

(deftest uri-template-test
  (testing "Valid"
    (let [template "http://example.com/{/path}{#frag}"]
      (is (= template (uri-template 1 {:title "property_template"} template)))))

  (testing "Invalid"
    (is (thrown? Exception (uri-template 1 {:title "property_template"} "http://example.com/{unclosed_var")))))
