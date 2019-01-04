(ns table2qb.pipelines.integration-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [table2qb.pipelines.test-common :refer [test-domain test-domain-data default-config]]
            [table2qb.pipelines.components :refer [components-pipeline]]
            [table2qb.pipelines.codelist :refer [codelist-pipeline]]
            [table2qb.pipelines.cube :refer [cube-pipeline]]
            [grafter.rdf :as rdf]
            [grafter.rdf.repository :refer [query] :as repo]
            [grafter.extra.repository :refer [with-repository]]
            [grafter.extra.validation.pmd :as pmd]
            [grafter.extra.validation.pmd.dataset :as pmdd]))

(deftest ^:integration integration-test
  (testing "Validates table2qb outputs against pmd, dataset, and cube tests"
    (testing "Overseas Trade"
      (with-repository [repo (repo/fixture-repo
                               ;; Third party vocabularies
                               (io/resource "examples/vocabularies/sdmx-dimension.ttl")
                               (io/resource "examples/vocabularies/qb.ttl")
                               (io/resource "examples/overseas-trade/vocabularies/2012.rdf")
                               (io/resource "examples/overseas-trade/vocabularies/CN_2015_20180206_105537.ttl"))]
        (let [stmts (concat
                     ;; Existing reference data
                     (codelist-pipeline (io/resource "examples/regional-trade/csv/flow-directions.csv") "Flow Directions" "flow-directions" test-domain)
                     (codelist-pipeline (io/resource "examples/regional-trade/csv/sitc-sections.csv") "Flow Directions" "sitc-sections" test-domain)
                     (codelist-pipeline (io/resource "examples/regional-trade/csv/units.csv") "Measurement Units" "measurement-units" test-domain)
                     (components-pipeline (io/resource "examples/regional-trade/csv/components.csv") test-domain)

                     ;; This dataset
                     (codelist-pipeline (io/resource "examples/overseas-trade/csv/countries.csv") "Countries" "countries" test-domain)
                     (components-pipeline (io/resource "examples/overseas-trade/csv/components.csv") test-domain)
                     (cube-pipeline (io/resource "examples/overseas-trade/csv/ots-cn-sample.csv") "Overseas Trade Sample" "overseas-trade-sample" default-config test-domain))]
          (with-open [conn (repo/->connection repo)]
            (rdf/add conn stmts)

            (testing "PMD Validation"
              (is (empty? (pmd/errors repo))))
            (testing "PMD Dataset Validation"
              (is (empty? (remove #{"is missing a reference area dimension"
                                    "is not a pmd:Dataset"
                                    "is missing a pmd:graph"}
                                  (pmdd/errors repo (str test-domain-data "overseas-trade-sample"))))))
            (testing "Sort Priority"
              (with-open [conn (repo/->connection repo)]
                (let [results (query conn (slurp (io/resource "examples/validation/sparql/sort-priority.sparql")))
                      schemes (->> results (map (comp str :scheme)) distinct)]
                  (testing "may be provided"
                    (is (some #{"http://gss-data.org.uk/def/concept-scheme/sitc-sections"} schemes)))
                  (testing "is optional"
                    (is (not-any? #{"http://gss-data.org.uk/def/concept-scheme/flow-directions"} schemes))))))
            (testing "Description"
              (with-open [conn (repo/->connection repo)]
                (let [results (query conn (slurp (io/resource "examples/validation/sparql/description.sparql")))
                      schemes (->> results (map (comp str :scheme)) distinct)]
                  (testing "may be provided"
                    (is (some #{"http://gss-data.org.uk/def/concept-scheme/sitc-sections"} schemes)))
                  (testing "is optional"
                    (is (not-any? #{"http://gss-data.org.uk/def/concept-scheme/flow-directions"} schemes))))))))))))


;; TODO: Vocabulary for pmd:usedCode
