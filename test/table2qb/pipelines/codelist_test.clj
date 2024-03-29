(ns table2qb.pipelines.codelist-test
  (:require [clojure.test :refer [deftest testing is]]
            [table2qb.csv :refer [reader]]
            [table2qb.pipelines.codelist :refer :all :as codelist]
            [table2qb.pipelines.test-common :refer [example-csvw
                                                    example-csv
                                                    example
                                                    maps-match?
                                                    test-domain
                                                    eager-select
                                                    add-csvw]]
            [table2qb.util :as util]
            [grafter-2.rdf4j.repository :as repo]
            [clojure.java.io :as io]))

(defn- read-codes [csv-source]
  (with-open [r (reader csv-source)]
    (doall (code-records r))))

(deftest codelists-test
  (testing "minimum case"
    (testing "csv table"
      (let [codes (read-codes (example-csv "regional-trade" "flow-directions.csv"))]
        (testing "one row per code"
          (is (= 2 (count codes))))))
    (testing "json metadata"
      (maps-match? (util/read-json (example-csvw "regional-trade" "flow-directions.json"))
                   (codelist-schema
                     "flow-directions-codelist.csv"
                     "Flow Directions Codelist"
                     (get-uris test-domain "flow-directions")))))
  (testing "with optional fields"
    (testing "csv table"
      (let [codes (read-codes (example-csv "regional-trade" "sitc-sections.csv"))]
        (testing "one column per attribute"
          (is (= (sort [:label :notation :parent_notation :parent_notation2 :sort_priority :description :top_concept_of :has_top_concept :pref_label])
                 (-> codes first keys sort))))
        (testing "column for sort-priority"
          (is (= "0" (-> codes first :sort_priority))))
        (testing "column for description"
          (is (= "lorem ipsum" (-> codes first :description))))))
    (testing "json metadata"
      (maps-match? (util/read-json (example-csvw "regional-trade" "sitc-sections.json"))
                   (codelist-schema
                    "sitc-sections-codelist.csv"
                    "SITC Sections Codelist"
                    (get-uris test-domain "sitc-sections")))))

  (testing "input validation"
    (testing "required columns"
      (is
        (= #{"Label"}
           (try
             (code-records (io/reader (char-array "column-a\nvalue-1")))
             (catch Exception e (:missing-columns (ex-data e)))))))))

(deftest codelist-pipeline-test
  (testing "flat example"
    (let [codelist-csv (example-csv "regional-trade" "flow-directions.csv")
          codelist-name "Flow directions"
          codelist-slug "flow-directions"
          base-uri "http://example.com/"
          codelist-uri "http://example.com/def/concept-scheme/flow-directions"
          repo (repo/sail-repo)]
      (with-open [conn (repo/->connection repo)]
        (add-csvw conn codelist/codelist-pipeline {:codelist-csv codelist-csv
                                                   :codelist-name codelist-name
                                                   :codelist-slug codelist-slug
                                                   :base-uri base-uri}))

      (testing "codelist title and label"
        (let [q (str "PREFIX dc: <http://purl.org/dc/terms/>"
                     "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                     "SELECT ?title ?label WHERE {"
                     "  <" codelist-uri "> dc:title ?title ;"
                     "                     rdfs:label ?label ."
                     "}")
              {:keys [title label] :as binding} (first (eager-select repo q))]
          (is (= codelist-name (str title)))
          (is (= codelist-name (str label)))))

      (testing "codelist type"
        (let [q (str "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>"
                     "ASK WHERE {"
                     "  <" codelist-uri "> a skos:ConceptScheme ."
                     "}")]

          (with-open [conn (repo/->connection repo)]
            (is (repo/query conn q)))))))

  (testing "hierachical example"
    (with-open [conn (repo/->connection (repo/sail-repo))]
      (add-csvw conn codelist/codelist-pipeline
                {:codelist-csv (example-csv "regional-trade" "sitc-sections.csv")
                 :codelist-name "SITC Sections"
                 :codelist-slug "sitc-sections"
                 :base-uri "http://example.com/"})
      (testing "has broader relations"
        (let [broaders (repo/query
                        conn
                        (str "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>"
                             "SELECT * WHERE {"
                             "  ?narrower skos:broader ?broader"
                             "}"))]
          (is (= 10 (count broaders)))))
      (testing "has narrower relations"
        (let [narrowers (repo/query
                         conn
                         (str "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>"
                              "SELECT * WHERE {"
                              "  ?broader skos:narrower ?narrower"
                              "}"))]
          (is (= 10 (count narrowers)))
          (is (= ["http://example.com/def/concept/sitc-sections/total"]
                 (->> narrowers (map :broader) distinct (map str))))))))

  (testing "custom-uris example"
    (let [codelist-csv (example-csv "customising-uris" "substanties.csv")
          codelist-name "substanties (IMJV)"
          codelist-slug "substantie"
          base-uri "https://id.milieuinfo.be/"
          uri-templates (example "templates" "customising-uris" "codelists.edn")
          repo (repo/sail-repo)]

      (with-open [conn (repo/->connection repo)]
        (add-csvw conn codelist/codelist-pipeline {:codelist-csv codelist-csv
                                                   :codelist-name codelist-name
                                                   :codelist-slug codelist-slug
                                                   :base-uri base-uri
                                                   :uri-templates uri-templates}))

      (testing "uri patterns match"
        (let [q (str "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>"
                     "SELECT * WHERE {"
                     "  ?code skos:inScheme ?codelist ;"
                     "        skos:notation 'CID280' ."
                     "}")
              {:keys [code codelist]} (first (eager-select repo q))]
          (is (= "https://id.milieuinfo.be/vocab/imjv/concept/substantie/CID280#id"
                 (str code)))
          (is (= "https://id.milieuinfo.be/vocab/imjv/conceptscheme/substanties#id"
                 (str codelist))))))))
