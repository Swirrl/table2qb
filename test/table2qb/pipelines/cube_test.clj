(ns table2qb.pipelines.cube-test
  (:require [clojure.data.csv :as ccsv]
            [clojure.test :refer :all]
            [grafter-2.rdf4j.repository :as repo]
            [table2qb.configuration.columns :as column-config]
            [table2qb.configuration.cube :as cube-config]
            [table2qb.csv :as csv :refer [reader]]
            [table2qb.pipelines.cube :refer :all]
            [table2qb.pipelines.test-common
             :refer
             [add-csvw
              default-config
              eager-select
              example
              example-csv
              example-csvw
              first-by
              maps-match?
              test-domain
              test-domain-data
              title->name]]
            [table2qb.util :as util])
  (:import java.io.StringWriter))

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
            (csv/write-csv string-writer (sort-by :component_slug component-specifications))
            (is (= (slurp (example-csvw "regional-trade" "component-specifications.csv"))
                   (str string-writer))))))
      (testing "compare with component-specifications.json"
        (testing "parsed contents match"
          (maps-match? (util/read-json (example-csvw "regional-trade" "component-specifications.json"))
                       (component-specification-schema
                        "regional-trade.slugged.normalised.csv"
                        "Regional Trade Component Specifications"
                        (get-uris test-domain "regional-trade")))))))
  (testing "name is optional"
    (let [metadata (component-specification-schema
                    "components.csv"
                    ""
                    (get-uris test-domain "ds-slug"))]
      (is (= nil (get metadata "dc:title"))))))
    
(deftest dataset-test
  (testing "compare with dataset.json"
    (maps-match? (util/read-json (example-csvw "regional-trade" "dataset.json"))
                 (dataset-schema
                   "regional-trade.slugged.normalised.csv"
                   "Regional Trade"
                   (get-uris test-domain "regional-trade"))))
 (testing "name is optional"
   (let [metadata (dataset-schema "components.csv" "" (get-uris test-domain "ds-slug"))]
     (is (= nil (get metadata "rdfs:label"))))))

(deftest data-structure-definition-test
  (testing "compare with data-structure-definition.json"
    (maps-match? (util/read-json (example-csvw "regional-trade" "data-structure-definition.json"))
                 (data-structure-definition-schema
                   "regional-trade.slugged.normalised.csv"
                   "Regional Trade"
                   (get-uris test-domain "regional-trade"))))
  (testing "name is optional"
    (let [metadata (data-structure-definition-schema "components.csv" "" (get-uris test-domain "ds-slug"))]
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
                                           cube-config
                                           (get-uris test-domain "regional-trade"))]
        (maps-match? (util/read-json (example-csvw "regional-trade" "observations.json"))
                     obs-meta)))
    (testing "overseas trade example"
      (let [obs-source (example-csv "overseas-trade" "ots-cn-sample.csv")
            cube-config (cube-config/get-cube-configuration obs-source default-config)
            obs-meta (observations-schema "ignore-me.csv" test-domain-data "overseas-trade" cube-config (get-uris test-domain "overseas-trade"))]
       (is (= (table-metadata-non-virtual-column-names obs-meta)
              (csv-column-names (example-csv "overseas-trade" "ots-cn-sample.csv"))))))))

(deftest used-codes-test
  (testing "codelists metadata"
    (maps-match? (util/read-json (example-csvw "regional-trade" "used-codes-codelists.json"))
                 (used-codes-codelists-schema "regional-trade.slugged.normalised.csv"
                                                (get-uris test-domain "regional-trade"))))
  (testing "codes metadata"
    (let [obs-source (example-csv "regional-trade" "input.csv")
          cube-config (cube-config/get-cube-configuration obs-source default-config)
          expected-json (util/read-json (example-csvw "regional-trade" "used-codes-codes.json"))]
      (maps-match? expected-json
                   (used-codes-codes-schema "regional-trade.slugged.csv"
                                            cube-config
                                            (get-uris test-domain "regional-trade"))))))

(deftest cube-pipeline-test
  (testing "regional-trade example"
    (let [dataset-name "Regional Trade"
          dataset-slug "regional-trade"
          base-uri "http://example.com/"
          dataset-uri "http://example.com/data/regional-trade"
          dsd-uri (str dataset-uri "/structure")
          repo (repo/sail-repo)]
      (with-open [conn (repo/->connection repo)]
        (add-csvw conn cube-pipeline {:input-csv (example-csv "regional-trade" "input.csv")
                                      :dataset-name dataset-name
                                      :dataset-slug dataset-slug
                                      :column-config default-config
                                      :base-uri base-uri}))

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

  (testing "custom-uris example"
    (let [input-csv (example-csv "customising-uris" "observations.csv")
          dataset-name "kubus luchtemissies"
          dataset-slug "luchtemissies"
          base-uri "https://id.milieuinfo.be/"
          uri-templates (example "templates" "customising-uris" "cube.edn")
          repo (repo/sail-repo)]
      (with-open [conn (repo/->connection repo)]
        (add-csvw conn cube-pipeline {:input-csv (example-csv "customising-uris" "observations.csv")
                                      :dataset-name dataset-name
                                      :dataset-slug dataset-slug
                                      :column-config (column-config/load-column-configuration (example "." "customising-uris" "columns.csv"))
                                      :base-uri base-uri
                                      :uri-templates uri-templates}))

      (testing "uri patterns match"
        (let [q (str "SELECT DISTINCT * WHERE {"
                     "  ?uri a ?type ."
                     "}")
              resources (eager-select repo q)]
          (let [uris-with-type (fn [type] (->> resources
                                               (filter #(= type (-> % :type str)))
                                               (map (comp str :uri))))
                qb (partial str "http://purl.org/linked-data/cube#")]
            (are [type uri] (contains? (set (uris-with-type type)) uri)
              (qb "DataSet") "https://id.milieuinfo.be/imjv/kubus/luchtemissies#id"
              (qb "DataStructureDefinition") "https://id.milieuinfo.be/imjv/dsd/luchtemissies#id"))))))) ;; the true URI is not pluralised
              ;;(qb "Observation") "https://id.milieuinfo.be/imjv/observatie/00119266000190/2804/em/11/obs/13/2012#id"
              

;; TODO: Need to label components and their used-code codelists if dataset-name is not blank
