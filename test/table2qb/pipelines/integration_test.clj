(ns table2qb.pipelines.integration-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [table2qb.pipelines.test-common :refer [test-domain test-domain-data default-config add-csvw]]
            [table2qb.pipelines.components :as components]
            [table2qb.pipelines.codelist :as codelist]
            [table2qb.pipelines.cube :as cube]
            [grafter-2.rdf4j.repository :refer [query] :as repo]
            [grafter.extra.repository :refer [with-repository]]
            [grafter.extra.validation.pmd :as pmd]
            [grafter.extra.validation.pmd.dataset :as pmdd]))

(deftest ^:integration integration-test
  (testing "Validates table2qb outputs against pmd, dataset, and cube tests"
    (testing "Overseas Trade"
      (with-repository [repo (repo/fixture-repo
                              ;; Third party vocabularies
                              (io/resource "vocabularies/sdmx-dimension.ttl")
                              (io/resource "vocabularies/qb.ttl")
                              (io/resource "overseas-trade/vocabularies/2012.rdf")
                              (io/resource "overseas-trade/vocabularies/CN_2015_20180206_105537.ttl"))]
        (with-open [conn (repo/->connection repo)]
          (add-csvw conn codelist/codelist-pipeline {:codelist-csv (io/resource "regional-trade/csv/flow-directions.csv")
                                                     :codelist-name             "Flow Directions"
                                                     :codelist-slug             "flow-directions"
                                                     :base-uri                  test-domain})
          (add-csvw conn codelist/codelist-pipeline {:codelist-csv (io/resource "regional-trade/csv/sitc-sections.csv")
                                                     :codelist-name             "SITC Sections"
                                                     :codelist-slug             "sitc-sections"
                                                     :base-uri                  test-domain})
          (add-csvw conn codelist/codelist-pipeline {:codelist-csv (io/resource "regional-trade/csv/units.csv")
                                                     :codelist-name             "Measurement Units"
                                                     :codelist-slug             "measurement-units"
                                                     :base-uri                  test-domain})
          (add-csvw conn components/components-pipeline {:input-csv (io/resource "regional-trade/csv/components.csv")
                                                         :base-uri                 test-domain})

          ;;this dataset
          (add-csvw conn codelist/codelist-pipeline {:codelist-csv (io/resource "overseas-trade/csv/countries.csv")
                                                     :codelist-name             "Countries"
                                                     :codelist-slug             "countries"
                                                     :base-uri                  test-domain})
          (add-csvw conn components/components-pipeline {:input-csv (io/resource "overseas-trade/csv/components.csv")
                                                         :base-uri                 test-domain})
          (add-csvw conn cube/cube-pipeline {:input-csv (io/resource "overseas-trade/csv/ots-cn-sample.csv")
                                             :dataset-name       "Overseas Trade Sample"
                                             :dataset-slug       "overseas-trade-sample"
                                             :column-config      default-config
                                             :base-uri           test-domain}))

        (testing "PMD Validation"
          (is (empty? (pmd/errors repo))))
        (testing "PMD Dataset Validation"
          (is (empty? (remove #{"is missing a reference area dimension"
                                "is not a pmd:Dataset"
                                "is missing a pmd:graph"}
                              (pmdd/errors repo (str test-domain-data "overseas-trade-sample"))))))
        (testing "Sort Priority"
          (with-open [conn (repo/->connection repo)]
            (let [results (query conn (slurp (io/resource "validation/sparql/sort-priority.sparql")))
                  schemes (->> results (map (comp str :scheme)) distinct)]
              (testing "may be provided"
                (is (some #{"http://gss-data.org.uk/def/concept-scheme/sitc-sections"} schemes)))
              (testing "is optional"
                (is (not-any? #{"http://gss-data.org.uk/def/concept-scheme/flow-directions"} schemes))))))
        (testing "Description"
          (with-open [conn (repo/->connection repo)]
            (let [results (query conn (slurp (io/resource "validation/sparql/description.sparql")))
                  schemes (->> results (map (comp str :scheme)) distinct)]
              (testing "may be provided"
                (is (some #{"http://gss-data.org.uk/def/concept-scheme/sitc-sections"} schemes)))
              (testing "is optional"
                (is (not-any? #{"http://gss-data.org.uk/def/concept-scheme/flow-directions"} schemes))))))))))


;; TODO: Vocabulary for pmd:usedCode
