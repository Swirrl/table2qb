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

;;(serialise-demo "./tmp")
