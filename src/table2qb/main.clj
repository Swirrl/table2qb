(ns table2qb.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [table2qb.cli.tasks :refer [exec-task find-task usage]]))

(defn get-config []
  (ig/read-string (slurp (io/resource "table2qb-config.edn"))))

(defmethod ig/init-key ::tasks [_ tasks]
  tasks)

(defn inner-main [args]
  (let [config (get-config)
        _loaded-namespaces (ig/load-namespaces config)
        system (ig/init config)
        tasks (::tasks system)
        task-name (or (first args) "help")]
    (if-let [task (find-task tasks task-name)]
      (try
        (exec-task task tasks (rest args))
        (catch Exception ex
          (println (.getMessage ex))
          (.printStackTrace ex)
          1))
      (binding [*out* *err*]
        (println "Unknown task " task-name)
        (usage tasks)
        1))))

(defn -main [& args]
  (if-let [exit-code (inner-main args)]
    (System/exit exit-code)))
