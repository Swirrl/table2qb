(ns table2qb.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [table2qb.cli.tasks :refer [exec-task find-task usage-lines display-lines]]
            [table2qb.pipelines]
            [table2qb.pipelines.cube]
            [table2qb.pipelines.components]
            [table2qb.pipelines.codelist])
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
        system (ig/init config)
        tasks (::tasks system)
        task-name (or (first args) "help")]
    (if-let [task (find-task tasks task-name)]
      (do (println "table2qb exec")
          (exec-task task tasks (rest args)))
      (throw (ex-info "Unknown task"
                      {:error ::unknown-task
                       ::task-name task-name
                       ::tasks tasks
                       ::args args})))))

(defn cli-main
  "Wrapper over inner-main that handles errors for the CLI and returns
  a CLI status code.

  This mainly exists to please the unit tests for the cli options (and
  avoid having to call System/exit)"
  [args]
  (try
    (inner-main args)
    0
    (catch ExceptionInfo ex
      (let [exd (ex-data ex)]
        (case (:error exd)
          ::unknown-task
          (let [task-name (::task-name exd)
                tasks (::tasks exd)]
            (display-error-lines
             (cons (str "Unknown task " task-name)
                   (usage-lines tasks)))
            1)
          (do (display-error-lines (cons (ex-message ex) (:error-lines exd)))
              1))))
    (catch Exception ex
      (.println *err* (.getMessage ex))
      (.printStackTrace ex *err*)
      1)))

(defn -main [& args]
  (let [exit-code (cli-main args)]
    (System/exit exit-code)))
