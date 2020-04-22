(ns table2qb.pipelines.cube-test
  (:require [clojure.test :refer :all]
            [table2qb.csv :refer [reader] :as csv]
            [clojure.data.csv :as ccsv]
            [table2qb.pipelines.cube :refer :all]
            [table2qb.pipelines.test-common :refer [default-config first-by example-csvw example-csv test-domain-data
                                                    maps-match? title->name eager-select add-csvw]]
            [table2qb.util :as util]
            [table2qb.configuration.cube :as cube-config]
            [grafter-2.rdf4j.repository :as repo]
            [grafter-2.rdf.protocols :as pr])
  (:import [java.io StringWriter]
           [java.net URI]))

(defn- table-metadata-non-virtual-column-names
  "Returns an ordered sequence of CSV table metadata column names."
  [metadata]
  (->> (get-in metadata ["tableSchema" "columns"])
       (remove #(get % "virtual" false))
       (map #(get % "name"))))

(defn- csv-column-names
  "Returns an ordered sequence of columns names for an input CSV data source."
  [csv-source]
  (let [header-row (with-open [r (reader csv-source)]
                     (first (ccsv/read-csv r)))]
    (map (fn [title] (name (title->name title))) header-row)))

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

(deftest observation-template-test
  (let [domain-data-prefix "http://example.com/data/"
        dataset-slug "test"
        dimension-names ["year" "flow" "code"]]
    (is (= "http://example.com/data/test/{+year}/{+flow}/{+code}" (observation-template domain-data-prefix dataset-slug dimension-names)))))

(defn- example-csvw-schema-part [example-directory part-name]
  "Extracts a table schema from the json metadata that covers the whole cube"
  (let [metadata-file (example-csvw example-directory (str example-directory "-metadata.json"))
        schema (util/read-json metadata-file)
        part-number (case part-name
                      "dataset" 0
                      "data-structure-defintion" 1
                      "component-specifications" 2
                      "used-codes-codelists" 3
                      "used-codes-codes" 4
                      "observations" 5)]
    (-> schema (get "tables") (nth part-number))))

(deftest component-specification-records-test
  (testing "returns a dataset of component-specifications"
    (let [cube-config (cube-config/get-cube-configuration (example-csv "regional-trade" "input.csv") default-config)
          component-specifications (component-specification-records cube-config)]
      (testing "one row per component"
        (is (= 8 (count component-specifications))))
      (testing "geography component"
        (let [{:keys [component_attachment component_property]}
              (first-by :component_slug "geography" component-specifications)]
          (is (= component_attachment "qb:dimension"))
          (is (= component_property "http://purl.org/linked-data/sdmx/2009/dimension#refArea"))))
      (testing "compare with component-specifications.csv"
        (testing "parsed contents match"
          (let [expected-records (csv/read-all-csv-maps (example-csvw "regional-trade" "component-specifications.csv"))]
            (is (= (set expected-records)
                   (set component-specifications)))))
        (testing "serialised contents match"
          (let [string-writer (StringWriter.)]
            (csv/write-csv string-writer component-specifications)
            (is (= (slurp (example-csvw "regional-trade" "component-specifications.csv"))
                   (str string-writer))))))
      (testing "compare with component-specifications.json"
        (testing "parsed contents match"
          (maps-match? (example-csvw-schema-part "regional-trade" "component-specifications")
                       (component-specification-schema
                        "component-specifications.csv"
                        test-domain-data
                        "Regional Trade"
                        "regional-trade"))))))
  (testing "name is optional"
    (let [metadata (component-specification-schema "components.csv" test-domain-data "" "ds-slug")]
      (is (= nil (get metadata "dc:title"))))))

(deftest dataset-test
  (testing "compare with dataset.json"
    (maps-match? (example-csvw-schema-part "regional-trade" "dataset")
                 (dataset-schema
                  "component-specifications.csv"
                  test-domain-data
                  "Regional Trade"
                  "regional-trade")))
  (testing "name is optional"
    (let [metadata (dataset-schema "components.csv" test-domain-data "" "ds-slug")]
      (is (= nil (get metadata "rdfs:label"))))))

(deftest data-structure-definition-test
  (testing "compare with data-structure-definition.json"
    (maps-match? (example-csvw-schema-part "regional-trade" "data-structure-defintion")
                 (data-structure-definition-schema
                  "component-specifications.csv"
                  test-domain-data
                  "Regional Trade"
                  "regional-trade")))
  (testing "name is optional"
    (let [metadata (data-structure-definition-schema "components.csv" test-domain-data "" "ds-slug")]
      (is (= nil (get metadata "rdfs:label"))))))

(defn get-observations
  [observations-source column-config]
  (let [cube-config (cube-config/get-cube-configuration observations-source column-config)]
    (with-open [r (csv/reader observations-source)]
      (doall (cube-config/observation-records r cube-config)))))

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
      (let [obs-source (example-csv "regional-trade" "input.csv")
            cube-config (cube-config/get-cube-configuration obs-source default-config)
            obs-meta (observations-schema "observations.csv"
                                          test-domain-data
                                          "regional-trade"
                                          cube-config)]
        (maps-match? (example-csvw-schema-part "regional-trade" "observations")
                     obs-meta)))
    (testing "overseas trade example"
      (let [obs-source (example-csv "overseas-trade" "ots-cn-sample.csv")
            cube-config (cube-config/get-cube-configuration obs-source default-config)
            obs-meta (observations-schema "ignore-me.csv" test-domain-data "overseas-trade" cube-config)]
        (is (= (table-metadata-non-virtual-column-names obs-meta) (csv-column-names (example-csv "overseas-trade" "ots-cn-sample.csv"))))))))

(deftest used-codes-test
  (testing "codelists metadata"
    (maps-match? (example-csvw-schema-part "regional-trade" "used-codes-codelists")
                 (used-codes-codelists-schema "component-specifications.csv"
                                              test-domain-data
                                              "regional-trade")))
  (testing "codes metadata"
    (let [obs-source (example-csv "regional-trade" "input.csv")
          cube-config (cube-config/get-cube-configuration obs-source default-config)
          expected-json (example-csvw-schema-part "regional-trade" "used-codes-codes")]
      (maps-match? expected-json
                   (used-codes-codes-schema "observations.csv"
                                            test-domain-data
                                            "regional-trade"
                                            cube-config)))))

(deftest cube-pipeline-test
  (let [dataset-name "Regional Trade"
        dataset-slug "regional-trade"
        base-uri (URI. "http://example.com/")
        dataset-uri "http://example.com/data/regional-trade"
        dsd-uri (str dataset-uri "/structure")
        repo (repo/sail-repo)]
    (with-open [conn (repo/->connection repo)]
      (add-csvw conn cube-pipeline {:input-csv (example-csv "regional-trade" "input.csv")
                           :dataset-name       dataset-name
                           :dataset-slug       dataset-slug
                           :column-config      default-config
                           :base-uri           base-uri}))

    (testing "Dataset title and label"
      (let [q (str "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                   "PREFIX dc: <http://purl.org/dc/terms/>"
                   "SELECT ?title ?label WHERE {"
                   "  <" dataset-uri "> dc:title ?title ;"
                   "                    rdfs:label ?label ."
                   "}")
            {:keys [title label] :as binding} (first (eager-select repo q))]
        (is (= dataset-name (str title)))
        (is (= dataset-name (str label)))))

    (testing "DSD title and label"
      (let [dsd-label (derive-dsd-label dataset-name)
            q (str "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                   "PREFIX dc: <http://purl.org/dc/terms/>"
                   "SELECT ?title ?label WHERE {"
                   "  <" dsd-uri "> dc:title ?title ;"
                   "                    rdfs:label ?label ."
                   "}")
            {:keys [title label] :as binding} (first (eager-select repo q))]
        (is (= dsd-label (str title)))
        (is (= dsd-label (str label)))))

    (testing "DSD type"
      (let [q (str "PREFIX qb: <http://purl.org/linked-data/cube#>"
                   "ASK WHERE {"
                   "  <" dsd-uri "> a qb:DataStructureDefinition ."
                   "}")]
        (with-open [conn (repo/->connection repo)]
          (is (repo/query conn q)))))))

;; TODO: Need to label components and their used-code codelists if dataset-name is not blank
