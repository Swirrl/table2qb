(ns table2qb.pipelines.cube-test
  (:require [clojure.test :refer :all]
            [table2qb.pipelines.cube :refer :all]
            [table2qb.pipelines.test-common :refer [default-config first-by example-csvw example-csv test-domain-data
                                                    maps-match? title->name]]
            [clojure.java.io :as io]
            [table2qb.csv :refer [read-csv write-csv]]
            [clojure.data.json :as json])
  (:import [java.io StringWriter]))

(defmacro is-metadata-compatible [input-csv schema]
  `(testing "column sequence matches"
     (with-open [rdr# (io/reader ~input-csv)]
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

(deftest component-specifications-test
  (testing "returns a dataset of component-specifications"
    (with-open [input-reader (io/reader (example-csv "regional-trade" "input.csv"))]
      (let [component-specifications (doall (component-specifications input-reader default-config))]
        (testing "one row per component"
          (is (= 8 (count component-specifications))))
        (testing "geography component"
          (let [{:keys [:component_attachment :component_property]}
                (first-by :component_slug "geography" component-specifications)]
            (is (= component_attachment "qb:dimension"))
            (is (= component_property "http://purl.org/linked-data/sdmx/2009/dimension#refArea"))))
        (testing "compare with component-specifications.csv"
          (testing "parsed contents match"
            (with-open [target-reader (io/reader (example-csvw "regional-trade" "component-specifications.csv"))]
              (is (= (set (read-csv target-reader))
                     (set component-specifications)))))
          (testing "serialised contents match"
            (with-open [target-reader (io/reader (example-csvw "regional-trade" "component-specifications.csv"))]
              (let [string-writer (StringWriter.)]
                (write-csv string-writer (sort-by :component_slug component-specifications))
                (is (= (slurp target-reader)
                       (str string-writer)))))))
        (testing "compare with component-specifications.json"
          (testing "parsed contents match"
            (with-open [target-reader (io/reader (example-csvw "regional-trade" "component-specifications.json"))]
              (maps-match? (json/read target-reader)
                           (component-specification-metadata
                             "regional-trade.slugged.normalised.csv"
                             test-domain-data
                             "Regional Trade Component Specifications"
                             "regional-trade"))))))))
  (testing "name is optional"
    (let [metadata (component-specification-metadata "components.csv" test-domain-data "" "ds-slug")]
      (is (= nil (get metadata "dc:title"))))))
    
(deftest dataset-test
  (testing "compare with dataset.json"
    (with-open [target-reader (io/reader (example-csvw "regional-trade" "dataset.json"))]
      (maps-match? (json/read target-reader)
                   (dataset-metadata
                     "regional-trade.slugged.normalised.csv"
                     test-domain-data
                     "Regional Trade"
                     "regional-trade"))))
 (testing "name is optional"
   (let [metadata (dataset-metadata "components.csv" test-domain-data "" "ds-slug")]
     (is (= nil (get metadata "rdfs:label"))))))

(deftest data-structure-definition-test
  (testing "compare with data-structure-definition.json"
    (with-open [target-reader (io/reader (example-csvw "regional-trade" "data-structure-definition.json"))]
      (maps-match? (json/read target-reader)
                   (data-structure-definition-metadata
                    "regional-trade.slugged.normalised.csv"
                    test-domain-data
                    "Regional Trade"
                    "regional-trade"))))
  (testing "name is optional"
    (let [metadata (data-structure-definition-metadata "components.csv" test-domain-data "" "ds-slug")]
      (is (= nil (get metadata "rdfs:label"))))))

(deftest observations-test
  (testing "sequence of observations"
    (testing "regional trade example"
      (with-open [input-reader (io/reader (example-csv "regional-trade" "input.csv"))]
        (let [observations (doall (observations input-reader default-config))]
          (testing "one observation per row"
            (is (= 44 (count observations))))
          (let [observation (first observations)]
            (testing "one column per component"
              (is (= 7 (count observation))))
            (testing "slugged columns"
              (are [expected actual] (= expected actual)
                "gbp-total" (:measure_type observation)
                "gbp-million" (:unit observation)
                "0-food-and-live-animals" (:sitc_section observation)
                "export" (:flow observation)))))))
    (testing "overseas trade example"
      (with-open [input-reader (io/reader (example-csv "overseas-trade" "ots-cn-sample.csv"))]
        (let [observations (doall (observations input-reader default-config))]
          (testing "one observation per row"
            (is (= 20 (count observations))))
          (let [observation (first observations)]
            (testing "one column per component"
              (is (= 7 (count observation))))
            (testing "slugged columns"
              (are [expected actual] (= expected actual)
                "gbp-total" (:measure_type observation)
                "gbp-million" (:unit observation)
                "cn#cn8_28399000" (:combined_nomenclature observation)
                "export" (:flow observation))))))))
  (testing "observation metadata"
    (testing "regional trade example"
      (with-open [input-reader (io/reader (example-csv "regional-trade" "input.csv"))
                  target-reader (io/reader (example-csvw "regional-trade" "observations.json"))]
        (let [obs-meta (observations-metadata input-reader
                                              "observations.csv"
                                              test-domain-data
                                              "regional-trade"
                                              default-config)]
          (maps-match? (json/read target-reader)
                       obs-meta))))
    (testing "overseas trade example"
      (with-open [input-reader (io/reader (example-csv "overseas-trade" "ots-cn-sample.csv"))]
        (let [obs-meta (observations-metadata input-reader "ignore-me.csv" test-domain-data "overseas-trade" default-config)]
          (is-metadata-compatible (example-csv "overseas-trade" "ots-cn-sample.csv")
                                  obs-meta))))))

(deftest used-codes-test
  (testing "codelists metadata"
    (with-open [target-reader (io/reader (example-csvw "regional-trade" "used-codes-codelists.json"))]
      (maps-match? (json/read target-reader)
                   (used-codes-codelists-metadata "regional-trade.slugged.normalised.csv"
                                                  test-domain-data
                                                  "regional-trade"))))
  (testing "codes metadata"
    (with-open [input-reader (io/reader (example-csv "regional-trade" "input.csv"))
                target-reader (io/reader (example-csvw "regional-trade" "used-codes-codes.json"))]
      (maps-match? (json/read target-reader)
                   (used-codes-codes-metadata input-reader
                                              "regional-trade.slugged.csv"
                                              test-domain-data
                                              "regional-trade"
                                              default-config)))))

(deftest validations-test
  (testing "all column must be recognised"
    (with-open [input-reader (io/reader (example-csv "validation" "unknown-columns.csv"))]
      (is (thrown-with-msg?
            Throwable #"Unrecognised column: Unknown"
            (observations input-reader default-config)))))

  (testing "a measure should be present"
    (testing "under the measures-dimension approach"
      (testing "with a single measure-type column"
        (with-open [input-reader (io/reader (example-csv "validation" "measure-type-single.csv"))]
          (is (seq? (observations input-reader default-config))))
        (testing "and a measure column"))
      ;; TODO - should fail (i.e. either type or measure provided)

      (testing "with multiple measure-type columns")
      ;; TODO - should fail - can only have one measure-type dimension
      ;; not sure this is worth testing until it's a problem!

      (testing "with no measure-type columns"
        (with-open [input-reader (io/reader (example-csv "validation" "measure-type-missing.csv"))]
          (is (thrown-with-msg?
                Throwable #"No measure type column"
                (component-specifications input-reader default-config))))))
    (testing "under the multi-measures approach"))
  ;; TODO - this isn't implemented yet
  ;; Should require that no measure-type component be provided if there is a measure column


  (testing "values must be provided for all dimensions"
    (with-open [input-reader (io/reader (example-csv "validation" "dimension-values-missing.csv"))]
      (is (thrown? Throwable
                   (doall (observations input-reader default-config)))))))

;; TODO: Need to label components and their used-code codelists if dataset-name is not blank
