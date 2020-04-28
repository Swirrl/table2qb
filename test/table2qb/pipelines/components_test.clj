(ns table2qb.pipelines.components-test
  (:require [clojure.test :refer :all]
            [table2qb.csv :refer [reader]]
            [table2qb.pipelines.components :refer :all]
            [table2qb.pipelines.test-common :refer [first-by maps-match? example-csv example-csvw test-domain]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]))

(deftest components-test
  (testing "csv table"
    (with-open [input-reader (reader (example-csv "regional-trade" "components.csv"))]
      (let [components (doall (component-records input-reader))]
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
                :codelist "http://gss-data.org.uk/def/concept-scheme/flow-directions"
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
    (with-open [target-reader (reader (example-csvw "regional-trade" "components.json"))]
      (maps-match? (json/read target-reader)
                   (components-schema "components.csv" (get-uris test-domain)))))

  (testing "input validation"
    (testing "required columns"
      (is
        (= #{"Label" "Component Type"}
           (try
             (component-records (io/reader (char-array "column-a\nvalue-1")))
             (catch Exception e (:missing-columns (ex-data e)))))))))
