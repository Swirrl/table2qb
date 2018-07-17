(ns table2qb.pipelines.integration-test
  (:require [clojure.test :refer :all]
            [table2qb.pipelines.test-common :refer [test-domain test-domain-data default-config]]
            [table2qb.pipelines.components :refer [components-pipeline]]
            [table2qb.pipelines.codelist :refer [codelist-pipeline]]
            [table2qb.pipelines.cube :refer [cube-pipeline]]
            [grafter.rdf :as rdf]
            [grafter.rdf.repository :refer [query] :as repo]
            [grafter.extra.repository :refer [with-repository]]
            [clojure.java.io :as io]
            [grafter.extra.validation.pmd :as pmd]
            [grafter.extra.validation.pmd.dataset :as pmdd]))

(defn load-from-file [conn file]
  (rdf/add conn (rdf/statements (io/file file))))

(deftest integration-test
  (testing "Validates table2qb outputs against pmd, dataset, and cube tests"
    (testing "Overseas Trade"
      (with-repository [repo (repo/sail-repo)]
                       (with-open [conn (repo/->connection repo)]
                         ;; Third party vocabularies
                         (doseq [file ["./examples/vocabularies/sdmx-dimension.ttl"
                                       "./examples/vocabularies/qb.ttl"
                                       "./examples/overseas-trade/vocabularies/2012.rdf"
                                       "./examples/overseas-trade/vocabularies/CN_2015_20180206_105537.ttl"]]
                           (load-from-file conn file))

                         (let [stmts (concat
                                       ;; Existing reference data
                                       (codelist-pipeline "./examples/regional-trade/csv/flow-directions.csv" "Flow Directions" "flow-directions" test-domain)
                                       (codelist-pipeline "./examples/regional-trade/csv/sitc-sections.csv" "Flow Directions" "sitc-sections" test-domain)
                                       (codelist-pipeline "./examples/regional-trade/csv/units.csv" "Measurement Units" "measurement-units" test-domain)
                                       (components-pipeline "./examples/regional-trade/csv/components.csv" test-domain)

                                       ;; This dataset
                                       (codelist-pipeline "./examples/overseas-trade/csv/countries.csv" "Countries" "countries" test-domain)
                                       (components-pipeline "./examples/overseas-trade/csv/components.csv" test-domain)
                                       (cube-pipeline "./examples/overseas-trade/csv/ots-cn-sample.csv" "Overseas Trade Sample" "overseas-trade-sample" default-config test-domain))]
                           (rdf/add conn stmts)))
                       (testing "PMD Validation"
                         (is (empty? (pmd/errors repo))))
                       (testing "PMD Dataset Validation"
                         (is (empty? (remove #{"is missing a reference area dimension"
                                               "is not a pmd:Dataset"
                                               "is missing a pmd:graph"}
                                             (pmdd/errors repo (str test-domain-data "overseas-trade-sample"))))))
                       (testing "Sort Priority"
                         (with-open [conn (repo/->connection repo)]
                           (let [results (query conn (slurp "./examples/validation/sparql/sort-priority.sparql"))
                                 schemes (->> results (map (comp str :scheme)) distinct)]
                             (testing "may be provided"
                               (is (some #{"http://gss-data.org.uk/def/concept-scheme/sitc-sections"} schemes)))
                             (testing "is optional"
                               (is (not-any? #{"http://gss-data.org.uk/def/concept-scheme/flow-directions"} schemes))))))
                       (testing "Description"
                         (with-open [conn (repo/->connection repo)]
                           (let [results (query conn (slurp "./examples/validation/sparql/description.sparql"))
                                 schemes (->> results (map (comp str :scheme)) distinct)]
                             (testing "may be provided"
                               (is (some #{"http://gss-data.org.uk/def/concept-scheme/sitc-sections"} schemes)))
                             (testing "is optional"
                               (is (not-any? #{"http://gss-data.org.uk/def/concept-scheme/flow-directions"} schemes))))))))))


;; TODO: Vocabulary for pmd:usedCode
