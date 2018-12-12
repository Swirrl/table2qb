(ns table2qb.pipelines.cube-test
  (:require [clojure.test :refer :all]
            [table2qb.csv :refer [reader read-csv] :as csv]
            [table2qb.pipelines.cube :refer :all]
            [table2qb.pipelines.test-common :refer [default-config first-by example-csvw example-csv test-domain-data
                                                    maps-match? title->name]]
            [table2qb.util :as util])
  (:import [java.io StringWriter]))

(defmacro is-metadata-compatible [input-csv schema]
  `(testing "column sequence matches"
     (with-open [rdr# (reader ~input-csv)]
       (let [csv-columns# (-> rdr# (read-csv title->name) first keys ((partial map name)))
             meta-columns# (->> (get-in ~schema ["tableSchema" "columns"])
                                (remove #(get % "virtual" false))
                                (map #(get % "name")))]
         (is (= csv-columns# meta-columns#))))))

(deftest identify-header-transformers-test
  (let [header ["Date" "Flow" "Unit" "CDID"]
        config default-config
        transformers (identify-header-transformers header config)]
    (is (= #{:flow :unit} (set (keys transformers))))
    (is (every? fn? (vals transformers)))))

(deftest suppress-value-column-test
  (testing "value column"
    (is (= {"name" "value"
            "title" "Value"
            "suppressOutput" true}
           (suppress-value-column {"name"  "value"
                                   "title" "Value"} #{:value}))))

  (testing "non-value column"
    (let [col {"name" "flow"
               "title" "Flow"}]
      (is (= col (suppress-value-column col #{:value}))))))

(deftest transform-colums-test
  (testing "converts columns with transforms specified"
    (let [transforms (identify-header-transformers ["Unit" "SITC Section"] default-config)]
      (is (maps-match? (transform-columns {:unit "£ million" :sitc_section "0 Food and Live Animals"} transforms)
                       {:unit "gbp-million" :sitc_section "0-food-and-live-animals"}))))
  (testing "leaves columns with no transform as is"
    (is (maps-match? (transform-columns {:label "not a slug" :curie "foo:bar"} {})
                     {:label "not a slug" :curie "foo:bar"}))))

(deftest observation-template-test
  (let [domain-data-prefix "http://example.com/data/"
        dataset-slug "test"
        dimension-names ["year" "flow" "code"]]
    (is (= "http://example.com/data/test/{+year}/{+flow}/{+code}" (observation-template domain-data-prefix dataset-slug dimension-names)))))

(deftest read-component-specifications-test
  (testing "returns a dataset of component-specifications"
    (let [component-specifications (read-component-specifications (example-csv "regional-trade" "input.csv") default-config)]
      (testing "one row per component"
        (is (= 8 (count component-specifications))))
      (testing "geography component"
        (let [{:keys [:component_attachment :component_property]}
              (first-by :component_slug "geography" component-specifications)]
          (is (= component_attachment "qb:dimension"))
          (is (= component_property "http://purl.org/linked-data/sdmx/2009/dimension#refArea"))))
      (testing "compare with component-specifications.csv"
        (testing "parsed contents match"
          (let [expected-records (csv/read-all-csv-records (example-csvw "regional-trade" "component-specifications.csv"))]
            (is (= (set expected-records)
                   (set component-specifications)))))
        (testing "serialised contents match"
          (let [string-writer (StringWriter.)]
            (csv/write-csv string-writer (sort-by :component_slug component-specifications))
            (is (= (slurp (example-csvw "regional-trade" "component-specifications.csv"))
                   (str string-writer))))))
      (testing "compare with component-specifications.json"
        (testing "parsed contents match"
          (maps-match? (util/read-json (example-csvw "regional-trade" "component-specifications.json"))
                       (component-specification-metadata
                         "regional-trade.slugged.normalised.csv"
                         test-domain-data
                         "Regional Trade Component Specifications"
                         "regional-trade"))))))
  (testing "name is optional"
    (let [metadata (component-specification-metadata "components.csv" test-domain-data "" "ds-slug")]
      (is (= nil (get metadata "dc:title"))))))
    
(deftest dataset-test
  (testing "compare with dataset.json"
    (maps-match? (util/read-json (example-csvw "regional-trade" "dataset.json"))
                 (dataset-metadata
                   "regional-trade.slugged.normalised.csv"
                   test-domain-data
                   "Regional Trade"
                   "regional-trade")))
 (testing "name is optional"
   (let [metadata (dataset-metadata "components.csv" test-domain-data "" "ds-slug")]
     (is (= nil (get metadata "rdfs:label"))))))

(deftest data-structure-definition-test
  (testing "compare with data-structure-definition.json"
    (maps-match? (util/read-json (example-csvw "regional-trade" "data-structure-definition.json"))
                 (data-structure-definition-metadata
                   "regional-trade.slugged.normalised.csv"
                   test-domain-data
                   "Regional Trade"
                   "regional-trade")))
  (testing "name is optional"
    (let [metadata (data-structure-definition-metadata "components.csv" test-domain-data "" "ds-slug")]
      (is (= nil (get metadata "rdfs:label"))))))

(deftest get-ordered-dimension-names-test
  (let [components [{:name "dim1"}
                    {:name "measure1"}
                    {:name "measure2"}
                    {:name "value"}
                    {:name "dim2"}
                    {:name "attr1"}]]
    (is (= ["dim1" "dim2"] (get-ordered-dimension-names components #{:dim1 :dim2 :dim3})))))

(defn get-observations
  [observations-source column-config]
  (let [lines (csv/read-all-csv-rows observations-source)]
    (doall (observation-rows (first lines) (rest lines) column-config))))

(deftest observations-test
  (testing "sequence of observations"
    (testing "regional trade example"
      (let [observations (get-observations (example-csv "regional-trade" "input.csv") default-config)
            observation (first observations)]
        (testing "one observation per row"
          (is (= 44 (count observations))))
        (testing "one column per component"
          (is (= 7 (count observation))))
        (testing "slugged columns"
          (are [expected actual] (= expected actual)
               "gbp-total" (:measure_type observation)
            "gbp-million" (:unit observation)
            "0-food-and-live-animals" (:sitc_section observation)
            "export" (:flow observation)))))
    (testing "overseas trade example"
      (let [observations (doall (get-observations (example-csv "overseas-trade" "ots-cn-sample.csv") default-config))
            observation (first observations)]
        (testing "one observation per row"
          (is (= 20 (count observations))))
        (testing "one column per component"
          (is (= 7 (count observation))))
        (testing "slugged columns"
          (are [expected actual] (= expected actual)
               "gbp-total" (:measure_type observation)
            "gbp-million" (:unit observation)
            "cn#cn8_28399000" (:combined_nomenclature observation)
            "export" (:flow observation))))))
  (testing "observation metadata"
    (testing "regional trade example"
      (let [input-header (csv/read-header-row (example-csv "regional-trade" "input.csv"))
            obs-meta (observations-metadata input-header
                                            "observations.csv"
                                            test-domain-data
                                            "regional-trade"
                                            default-config)]
        (maps-match? (util/read-json (example-csvw "regional-trade" "observations.json"))
                     obs-meta)))
    (testing "overseas trade example"
      (let [input-header-row (csv/read-header-row (example-csv "overseas-trade" "ots-cn-sample.csv"))
            obs-meta (observations-metadata input-header-row "ignore-me.csv" test-domain-data "overseas-trade" default-config)]
        (is-metadata-compatible (example-csv "overseas-trade" "ots-cn-sample.csv")
                                obs-meta)))))

(deftest used-codes-test
  (testing "codelists metadata"
    (maps-match? (util/read-json (example-csvw "regional-trade" "used-codes-codelists.json"))
                 (used-codes-codelists-metadata "regional-trade.slugged.normalised.csv"
                                                test-domain-data
                                                "regional-trade")))
  (testing "codes metadata"
    (let [observations-header-row (csv/read-header-row (example-csv "regional-trade" "input.csv"))
          expected-json (util/read-json (example-csvw "regional-trade" "used-codes-codes.json"))]
      (maps-match? expected-json
                   (used-codes-codes-metadata observations-header-row
                                              "regional-trade.slugged.csv"
                                              test-domain-data
                                              "regional-trade"
                                              default-config)))))

(deftest validations-test
  (testing "all column must be recognised"
    (is (thrown-with-msg?
          Throwable #"Unrecognised column: Unknown"
          (get-observations (example-csv "validation" "unknown-columns.csv") default-config))))

  (testing "a measure should be present"
    (testing "under the measures-dimension approach"
      (testing "with a single measure-type column"
        (is (seq? (get-observations (example-csv "validation" "measure-type-single.csv") default-config)))
        (testing "and a measure column"))
      ;; TODO - should fail (i.e. either type or measure provided)

      (testing "with multiple measure-type columns")
      ;; TODO - should fail - can only have one measure-type dimension
      ;; not sure this is worth testing until it's a problem!

      (testing "with no measure-type columns"
        (is (thrown-with-msg?
              Throwable #"No measure type column"
              (read-component-specifications (example-csv "validation" "measure-type-missing.csv") default-config)))))
    (testing "under the multi-measures approach"))
  ;; TODO - this isn't implemented yet
  ;; Should require that no measure-type component be provided if there is a measure column


  (testing "values must be provided for all dimensions"
    (is (thrown? Throwable
                 (doall (get-observations (example-csv "validation" "dimension-values-missing.csv") default-config))))))

;; TODO: Need to label components and their used-code codelists if dataset-name is not blank
