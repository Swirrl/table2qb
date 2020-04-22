(ns table2qb.pipelines
  (:require [integrant.core :as ig]))

(defmethod ig/init-key ::pipeline [key config]
  (let [ns (find-ns (symbol (namespace key)))
        pipeline-name (symbol (name key))
        ;;TODO: require namespace?
        var (ns-resolve ns pipeline-name)]
    (assoc config :name
           pipeline-name :table2qb/pipeline-fn
           var :description (:doc (meta var)))))

(defmethod ig/init-key ::pipelines [_ pipelines]
  pipelines)

(defmethod ig/init-key ::pipeline-runner [_ pipelines]
  pipelines)


