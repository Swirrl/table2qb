(ns table2qb.util)

(defn exception? [x] (instance? Exception x))

(defn map-values [m f]
  (into {} (map (fn [[k v]] [k (f v)]) m)))

(defn target-order [s]
  "Returns a function for use with sort-by which returns an index of an
  element according to a target sequence, or an index falling after the target
  (i.e. putting unrecognised elements at the end)."
  (let [item->index (zipmap s (range))]
    (fn [element]
      (get item->index element (count s)))))
