(ns table2qb.configuration.cube-test
  (:require [clojure.test :refer :all]
            [table2qb.configuration.cube :refer :all]
            [table2qb.configuration.column :refer [unitize]]
            [grafter.extra.cell.uri :refer [slugize]]
            [table2qb.pipelines.test-common :refer [maps-match? example-csv default-config]]
            [table2qb.csv :as tcsv]))

(deftest find-header-transformers-test
  (let [cube-config {:name->component {:date {:title "Date"
                                              :name "date"
                                              :value_transformation nil}
                                       :flow {:title "Flow"
                                              :name "flow"
                                              :value_transformation unitize}
                                       :unit {:title "Unit"
                                              :name "unit"
                                              :value_transformation identity}
                                       :cdid {:title "CDID"
                                              :name "cdid"
                                              :value_transformation nil}}
                     :names [:date :flow :unit :cdid]}
        transformers (find-header-transformers cube-config)]
    (is (= #{:flow :unit} (set (keys transformers))))
    (is (every? fn? (vals transformers)))))

(deftest transform-colums-test
  (testing "converts columns with transforms specified"
    (let [transforms {:unit unitize :sitc_section slugize}]
      (is (maps-match? (transform-columns {:unit "£ million" :sitc_section "0 Food and Live Animals"} transforms)
                       {:unit "gbp-million" :sitc_section "0-food-and-live-animals"}))))
  (testing "leaves columns with no transform as is"
    (is (maps-match? (transform-columns {:label "not a slug" :curie "foo:bar"} {})
                     {:label "not a slug" :curie "foo:bar"}))))

(deftest get-ordered-dimension-names-test
  (let [cube-config {:name->component {:dim1 {:name "dim1"}
                                       :dim2 {:name "dim2"}
                                       :measure1 {:name "measure1"}
                                       :measure2 {:name "measure2"}
                                       :value {:name "value"}
                                       :attr1 {:name "attr1"}}
                     :dimensions #{:dim1 :dim2}
                     :measures #{:measure1 :measure2}
                     :attributes #{:attr1}
                     :names [:dim1 :measure1 :measure2 :value :dim2 :attr1]}]
    (is (= ["dim1" "dim2"] (get-ordered-dimension-names cube-config)))))

(deftest get-cube-configuration-test
  (testing "qb:measureType cube"
    (testing "valid cube configuration"
      (let [{:keys [names dimensions measures attributes type
                    measure-type-component value-component
                    name->component] :as config} (get-cube-configuration (example-csv "validation" "measure-type-cube.csv") default-config)
            expected-keys (into #{value-component measure-type-component} (concat dimensions measures attributes))]
        (is (= :measure-dimension type))
        (is (= [:geography :date :flow :measure_type :value :unit] names))
        (is (= #{:geography :date :flow :measure_type} dimensions))
        (is (= #{:count :gbp_total} measures))
        (is (= :value value-component))
        (is (= :measure_type measure-type-component))
        (is (= expected-keys (set (keys name->component))))))

    (testing "measure type column references invalid column"
      (is (thrown?
            Throwable
            (get-cube-configuration (example-csv "validation" "measure-type-invalid-column-reference.csv") default-config))))

    (testing "measure type column references non-measure column"
      (is (thrown?
            Throwable
            (get-cube-configuration (example-csv "validation" "measure-type-invalid-measure-reference.csv") default-config))))

    (testing "no value column"
      (is (thrown-with-msg?
            Throwable
            #"No value column"
            (get-cube-configuration (example-csv "validation" "value-column-missing.csv") default-config))))

    (testing "multiple value columns"
      (is (thrown-with-msg? Throwable
                            #"multiple value columns"
                            (get-cube-configuration (example-csv "validation" "multiple-value-columns.csv") default-config))))

    (testing "contains measure columns"
      (is (thrown? Throwable (get-cube-configuration (example-csv "validation" "measure-type-and-measures.csv") default-config)))))

  (testing "Multi-measure cube"
    (testing "valid cube"
      (let [{:keys [names dimensions measures
                    attributes type] :as cube-config} (get-cube-configuration (example-csv "validation" "multi-measure-cube.csv") default-config)]
        (is (= :multi-measure type))
        (is (= [:date :geography :flow :count :gbp_total] names))
        (is (= #{:date :geography :flow} dimensions))
        (is (= #{:count :gbp_total} measures))
        (is (= #{} attributes))))

    (testing "with no dimensions"
      (is (thrown-with-msg?
            Throwable
            #"No dimension columns found"
            (get-cube-configuration (example-csv "validation" "multi-measure-no-dimensions.csv") default-config))))

    (testing "with value column"
      (is (thrown-with-msg?
            Throwable #"Columns Value represent observation values"
            (get-cube-configuration (example-csv "validation" "multi-measure-with-value-column.csv") default-config)))))

  (testing "invalid column headers"
    (is (thrown-with-msg?
          Throwable #"Unknown column titles"
          (get-cube-configuration (example-csv "validation" "unknown-columns.csv") default-config))))

  (testing "no measure-type or measure columns"
    (is (thrown-with-msg?
          Throwable #"at least one measure column"
          (get-cube-configuration (example-csv "validation" "measure-type-missing.csv") default-config))))

  (testing "multiple qb:measureType columns"
    (is (thrown-with-msg?
          Throwable
          #"multiple qb:measureType columns"
          (get-cube-configuration (example-csv "validation" "multiple-measure-type-columns.csv") default-config)))))

(deftest observation-records-test
  (testing "Missing dimension values"
    (let [obs-source (example-csv "validation" "dimension-values-missing.csv")
          cube-config (get-cube-configuration obs-source default-config)]
      (is (thrown-with-msg? Throwable
                            #"Missing value for dimension"
                            (with-open [r (tcsv/reader obs-source)]
                              (doall (observation-records r cube-config))))))))
