(ns table2qb.demo
  (:require [grafter.rdf :as rdf]
            [grafter.rdf.io :as gio]
            [clojure.java.io :as io]
            [table2qb.core :refer [components-pipeline codelist-pipeline cube-pipeline]]
            [table2qb.configuration :as config]))

(defn serialise-demo [out-dir]
  (let [config (config/real-load-configuration)]
    (with-open [output-stream (io/output-stream (str out-dir "/components.ttl"))]
      (let [writer (gio/rdf-serializer output-stream :format :ttl)]
        (rdf/add writer
                 (components-pipeline "./examples/regional-trade/csv/components.csv"))))

    (with-open [output-stream (io/output-stream (str out-dir "/flow-directions.ttl"))]
      (let [writer (gio/rdf-serializer output-stream :format :ttl)]
        (rdf/add writer
                 (codelist-pipeline "./examples/regional-trade/csv/flow-directions.csv"
                                    "Flow Directions" "flow-directions"))))

    (with-open [output-stream (io/output-stream (str out-dir "/sitc-sections.ttl"))]
      (let [writer (gio/rdf-serializer output-stream :format :ttl)]
        (rdf/add writer
                 (codelist-pipeline "./examples/regional-trade/csv/sitc-sections.csv"
                                    "SITC Sections" "sitc-sections"))))

    (with-open [output-stream (io/output-stream (str out-dir "/measurement-units.ttl"))]
      (let [writer (gio/rdf-serializer output-stream :format :ttl)]
        (rdf/add writer
                 (codelist-pipeline "./examples/regional-trade/csv/units.csv"
                                    "Measurement Units" "measurement-units"))))

    (with-open [output-stream (io/output-stream (str out-dir "/cube.ttl"))]
      (let [writer (gio/rdf-serializer output-stream :format :ttl)]
        (rdf/add writer
                 (cube-pipeline "./examples/regional-trade/csv/input.csv"
                                "Regional Trade" "regional-trade"
                                config))))))

;;(serialise-demo "./tmp")
