(ns table2qb.configuration.column-test
  (:require [clojure.test :refer :all]
            [table2qb.configuration.column :refer :all]))

(deftest parse-column-test
  (testing "Valid"
    (let [input-row {:title "SITC Section"
                     :name "sitc_section"
                     :component_attachment "qb:dimension"
                     :property_template "http://gss-data.org.uk/def/dimension/sitc-section"
                     :datatype "string"
                     :value_transformation "slugize"}
          column (parse-column input-row)]
      (is (= "SITC Section" (:title column)))
      (is (= "sitc_section" (:name column)))
      (is (= :dimension (:type column)))
      (is (= "string" (:datatype column)))
      (is (fn? (:value_transformation column)))))

  (testing "Empty title"
    (is (thrown-with-msg?
          Throwable
          #"Title cannot be blank"
          (parse-column {:title "   "
                         :name "geography"
                         :component_attachment "qb:dimension"}))))

  (testing "Empty name"
    (is (thrown-with-msg?
          Throwable
          #"csvw:name cannot be blank"
          (parse-column {:title "Geography"
                         :name ""
                         :component_attachment "qb:dimension"}))))

  (testing "Invalid name"
    (is (thrown-with-msg?
          Throwable
          #"cannot contain hyphens"
          (parse-column {:title "SITC Code"
                         :name "sitc-code"
                         :component_attachment "qb:dimension"}))))

  (testing "Invalid component attachment"
    (is (thrown-with-msg?
          Throwable
          #"Invalid component attachment"
          (parse-column {:title "Geography"
                         :name "geography"
                         :component_attachment "invalid"}))))

  (testing "Invalid value transform"
    (is (thrown-with-msg?
          Throwable
          #"Invalid value_transformation function"
          (parse-column {:title "SITC Section"
                         :name "sitc_section"
                         :component_attachment "qb:dimension"
                         :value_transformation "invalid"})))))
