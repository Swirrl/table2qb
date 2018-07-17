(ns table2qb.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [table2qb.cli.tasks :refer [exec-task find-task usage-lines display-lines]])
  (:import [clojure.lang ExceptionInfo]))

(defn get-config []
  (ig/read-string (slurp (io/resource "table2qb-config.edn"))))

(defmethod ig/init-key ::tasks [_ tasks]
  tasks)

(defn- display-error-lines [lines]
  (binding [*out* *err*]
    (display-lines lines)))

(defn inner-main [args]
  (let [config (get-config)
        _loaded-namespaces (ig/load-namespaces config)
        system (ig/init config)
        tasks (::tasks system)
        task-name (or (first args) "help")]
    (if-let [task (find-task tasks task-name)]
      (try
        (exec-task task tasks (rest args))
        (catch ExceptionInfo ex
          (display-error-lines (cons (.getMessage ex) (:error-lines (ex-data ex))))
          1)
        (catch Exception ex
          (.println *err* (.getMessage ex))
          (.printStackTrace ex *err*)
          1))
      (do
        (display-error-lines
          (cons (str "Unknown task " task-name)
                (usage-lines tasks)))
        1))))

(defn -main [& args]
  (if-let [exit-code (inner-main args)]
    (System/exit exit-code)))
