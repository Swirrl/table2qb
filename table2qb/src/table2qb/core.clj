(ns table2qb.core
  (:require [clojure.data.csv :as csv]
            [net.cgrand.xforms :as x]))

(def standardise-header
  {"GeographyCode" :geography
   "DateCode" :date
   "Measurement" :measure
   "Units" :unit
   "Value" :value
   "SITC Section" :sitc-section
   "Flow" :flow})

(defn read-csv [reader]
  (let [csv-data (csv/read-csv reader)]
    (map zipmap
         (->> (first csv-data)
              (map standardise-header)
              repeat)
         (rest csv-data))))

(defn headers-matching [pred]
  (comp (take 1) (map keys) cat (filter pred)))

(def is-dimension? #{:geography :date :sitc-section :flow})
(def is-attribute? #{:unit})

(defn append-measures-dimension [xf]
  (fn
    ([] (xf))
    ([result] (xf (xf result) :measure-type))
    ([result input] (xf result input))))

(def dimensions
  (comp (headers-matching is-dimension?)
        append-measures-dimension))

(def attributes (headers-matching is-attribute?))

(def standardise-measure {"Value" :value, "Net Mass", :net-mass})

(def measures
  (comp (map :measure) (distinct) (map standardise-measure)))

(defn components
  "Takes an filename for csv of observations and returns a components dataset"
  [reader]
  (let [data (read-csv reader)]
    (sequence (x/multiplex [dimensions attributes measures]) data)))
