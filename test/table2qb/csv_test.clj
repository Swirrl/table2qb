(ns table2qb.csv-test
  (:require  [clojure.test :refer :all]
             [table2qb.csv :refer :all]))

(deftest bom-disposal-test
  (testing "The reader should remove a leading BOM if present"
    (let [data (read-all-csv-maps "test/resources/bom-example.csv")]
      (contains? (first data) :Label))))

(deftest get-cell-default-test
  (testing "Literal default"
    (let [default "default"
          column {:title "column" :key :column :default default}]
      (is (= default (get-cell-default column {})))))

  (testing "Derived default"
    (let [column {:title "column" :key :column :default (fn [row] (* 2 (:x row)))}]
      (is (= 4 (get-cell-default column {:x 2}))))))

(deftest validate-header-test
  (let [columns [{:title "required" :key :required :required true}
                 {:title "optional1" :key :opt1}
                 {:title "optional2" :key :opt2}]
        column-model (build-column-model columns)]

    (testing "Valid"
      (is (= #{"required" "optional2"} (validate-header ["optional2" "required"] column-model))))

    (testing "Duplicate headers"
      (is (thrown-with-msg? Exception #"Duplicate column headers" (validate-header ["required" "required" "optional1"] column-model))))

    (testing "Missing required columns"
      (is (thrown-with-msg? Exception #"Missing required columns" (validate-header ["optional2" "optional1"] column-model))))

    (testing "Unknown columns"
      (is (thrown-with-msg? Exception #"Unexpected columns" (validate-header ["required" "unknown" "optional1"] column-model))))))

(deftest get-cell-value-test
  (testing "No validations or transform"
    (let [column {:title "column" :key :column}
          value "value"]
      (is (= value (get-cell-value 1 column value)))))

  (testing "Valid with validation and no transform"
    (let [column {:title "column" :key :column :validate [(validate-one-of #{"foo" "bar" "baz"})]}]
      (is (= "bar" (get-cell-value 1 column "bar")))))

  (testing "Valid with transform"
    (let [column {:title "column" :key :column :transform (fn [row column value] (Integer/parseInt value))}]
      (is (= 4 (get-cell-value 1 column "4")))))

  (testing "Invalid"
    (let [column {:title "column" :key :column :validate [validate-not-blank]}]
      (is (thrown? Exception (get-cell-value 1 column "    "))))))

(deftest parse-row-test
  (let [required-col {:title "required" :key :required :required true}
        opt1-col {:title "opt1" :key :opt1 :default "default1"}
        opt2-col {:title "opt2" :key :opt2 :default (fn [{:keys [required] :as col}] (str required required))}
        opt3-col {:title "opt3" :key :opt3 :validate [validate-not-blank]}
        parsed (parse-row 0 ["req" "val3"] [required-col opt3-col] [opt2-col opt1-col])]
    (is (= {:required "req"
            :opt1 "default1"
            :opt2 "reqreq"
            :opt3 "val3"}
           parsed))))

(deftest validate-not-blank-test
  (testing "Valid"
    (let [s "Not blank"
          column {:title "column" :key :column}]
      (is (= s (validate-not-blank 1 column s)))))

  (testing "Invalid"
    (let [column {:title "column" :key :column}]
      (is (thrown-with-msg? Exception #"Value cannot be blank" (validate-not-blank 1 column "   "))))))

(deftest validate-one-of-test
  (let [options #{"foo" "bar" "baz"}
        column {:title "column" :key :column}
        vf (validate-one-of options)]
    (testing "Valid"
      (is (= "baz" (vf 1 column "baz"))))

    (testing "Invalid"
      (is (thrown-with-msg? Exception #"Expected one of" (vf 1 column "quux"))))))
               
(deftest validate-mapping-test
  (let [mapping {"foo" :foo "bar" :bar}
        column {:title "column" :key :column}
        vf (validate-mapping mapping)]
    (testing "Valid"
      (is (= :bar (vf 1 column "bar"))))

    (testing "Invalid"
      (is (thrown-with-msg? Exception #"Expected one of" (vf 1 column "quux"))))))