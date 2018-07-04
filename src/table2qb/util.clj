(ns table2qb.util
  (:import [java.util List]))

(defn exception? [x] (instance? Exception x))

(defn map-values [m f]
  (into {} (map (fn [[k v]] [k (f v)]) m)))

(defn target-order [^List v]
  "Returns a function for use with sort-by which returns an index of an
  element according to a target vector, or an index falling after the target
  (i.e. putting unrecognised elements at the end)."
  (fn [element]
    (let [i (.indexOf v element)]
      (if (= -1 i)
        (inc (count v))
        i))))
