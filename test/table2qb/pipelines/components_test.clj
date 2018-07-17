(ns table2qb.pipelines.components-test
  (:require [clojure.test :refer :all]
            [table2qb.pipelines.components :refer :all]
            [table2qb.pipelines.test-common :refer [first-by maps-match? example-csv example-csvw test-domain-def]]
            [clojure.java.io :as io]
            [clojure.data.json :as json]))

(deftest components-test
  (testing "csv table"
    (with-open [input-reader (io/reader (example-csv "regional-trade" "components.csv"))]
      (let [components (doall (components input-reader))]
        (testing "one row per component"
          (is (= 4 (count components))))
        (testing "one column per attribute"
          (testing "flow"
            (let [flow (first-by :label "Flow" components)]
              (are [attribute value] (= value (attribute flow))
                   :notation "flow"
                :description "Direction in which trade is measured"
                :component_type "qb:DimensionProperty"
                :component_type_slug "dimension"
                :codelist (str test-domain-def "concept-scheme/flow-directions")
                :property_slug "flow"
                :class_slug "Flow"
                :parent_property nil)))
          (testing "gbp total"
            (let [gbp-total (first-by :label "GBP Total" components)]
              (are [attribute value] (= value (attribute gbp-total))
                   :notation "gbp-total"
                :component_type "qb:MeasureProperty"
                :component_type_slug "measure"
                :property_slug "gbpTotal"
                :class_slug "GbpTotal"
                :parent_property "http://purl.org/linked-data/sdmx/2009/measure#obsValue")))))))
  (testing "json metadata"
    (with-open [target-reader (io/reader (example-csvw "regional-trade" "components.json"))]
      (maps-match? (json/read target-reader)
                   (components-metadata "components.csv" test-domain-def)))))
