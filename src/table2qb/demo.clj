(ns table2qb.demo
  (:require [grafter.rdf :as rdf]
            [grafter.rdf.io :as gio]
            [clojure.java.io :as io]
            [table2qb.core :refer [components-pipeline codelist-pipeline cube-pipeline]]
            [table2qb.configuration :as config]
            [table2qb.main :refer [inner-main]]))

(defn serialise-demo [out-dir]
  (let [config (config/real-load-configuration)]
    (with-open [output-stream (io/output-stream (str out-dir "/components.ttl"))]
      (let [writer (gio/rdf-serializer output-stream :format :ttl)]
        (rdf/add writer
                 (components-pipeline "./examples/employment/csv/components.csv" config))))

    (with-open [output-stream (io/output-stream (str out-dir "/gender.ttl"))]
      (let [writer (gio/rdf-serializer output-stream :format :ttl)]
        (rdf/add writer
                 (codelist-pipeline "./examples/employment/csv/gender.csv"
                                    "Gender" "gender" config))))

    (with-open [output-stream (io/output-stream (str out-dir "/measurement-units.ttl"))]
      (let [writer (gio/rdf-serializer output-stream :format :ttl)]
        (rdf/add writer
                 (codelist-pipeline "./examples/employment/csv/units.csv"
                                    "Measurement Units" "measurement-units"
                                    config))))

    (with-open [output-stream (io/output-stream (str out-dir "/cube.ttl"))]
      (let [writer (gio/rdf-serializer output-stream :format :ttl)]
        (rdf/add writer
                 (cube-pipeline "./examples/employment/csv/input.csv"
                                "Employment" "employment"
                                config))))))

(defn cli-serialise-demo [out-dir]
  (let [column-config "resources/columns.csv"]
    (inner-main ["exec" "components-pipeline" "--input-csv" "./examples/regional-trade/csv/components.csv" "--column-config" column-config "--output-file" (str out-dir "/components.ttl")])
    (inner-main ["exec" "codelist-pipeline" "--codelist-csv" "./examples/regional-trade/csv/flow-directions.csv" "--codelist-name" "Flow Directions" "--codelist-slug" "flow-directions" "--column-config" column-config "--output-file" (str out-dir "/flow-directions.csv")])
    (inner-main ["exec" "codelist-pipeline" "--codelist-csv" "./examples/regional-trade/csv/sitc-sections.csv" "--codelist-name" "SITC Sections" "--codelist-slug" "sitc-sections" "--column-config" column-config "--output-file" (str out-dir "/sitc-sections.csv")])
    (inner-main ["exec" "codelist-pipeline" "--codelist-csv" "./examples/regional-trade/csv/units.csv" "--codelist-name" "Measurement Units" "--codelist-slug" "measurement-units" "--column-config" column-config "--output-file" (str out-dir "/measurement-units.csv")])
    (inner-main ["exec" "cube-pipeline" "--input-csv" "./examples/regional-trade/csv/input.csv" "--dataset-name" "Regional Trade" "--dataset-slug" "regional-trade" "--column-config" column-config "--output-file" (str out-dir "/cube.ttl")])))

;;(serialise-demo "./tmp")
