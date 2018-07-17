(ns table2qb.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [grafter.rdf.io :as gio]
            [grafter.rdf :as rdf]
            [table2qb.configuration :as config]
            [clojure.set :as set]))

(defn get-config []
  (ig/read-string (slurp (io/resource "table2qb-config.edn"))))

(defmethod ig/init-key ::tasks [_ tasks]
  tasks)

(defn- display-tasks [tasks]
  (println "Available tasks are:")
  (doseq [task tasks]
    (println (name (:name task)))))

(defn usage [tasks]
  (println "Usage: table2qb task-name [args]")
  (display-tasks tasks)
  (println "Use table2qb help task-name for more information about a task"))

(defn find-task [tasks task-name]
  (first (filter (fn [task] (= task-name (name (:name task)))) tasks)))

(defmulti exec-task (fn [task all-tasks args] (:name task)))

(defmulti describe-task (fn [task] (:name task)))

(defmethod describe-task :help [_task]
  (println "Displays usage information for a task")
  (println "Usage: table2qb help [task-name]"))

(defmethod describe-task :list [_task]
  (println "Lists the available pipelines")
  (println "Usage: table2qb list"))

(defmethod describe-task :describe [_task]
  (println "Describes a named pipeline")
  (println "Usage: table2qb describe pipeline-name"))

(defmethod describe-task :exec [_task]
  (println "Executes a named pipeline")
  (println "Usage table2qb exec pipeline-name args"))

(defmethod exec-task :help [_help-task all-tasks args]
  (if-let [task-name (first args)]
    (if-let [task (find-task all-tasks task-name)]
      (describe-task task)
      (binding [*out* *err*]
        (println "Unknown task name " task-name)
        (display-tasks all-tasks)
        1))
    (usage all-tasks)))

(defn display-pipelines [pipelines]
  (println "Available pipelines:")
  (println)
  (doseq [pipeline pipelines]
    (println (:name pipeline))))

(defmethod exec-task :list [{:keys [pipelines] :as list-task} all-tasks args]
  (display-pipelines pipelines)
  (println)
  (println "Use table2qb describe pipeline-name for information on how to run the named pipeline"))

(defn find-pipeline [pipelines pipeline-name]
  (let [pipeline-sym (symbol pipeline-name)]
    (first (filter (fn [pipeline] (= pipeline-sym (:name pipeline))) pipelines))))

;;TODO: use grafter types
(defmulti parse-arg (fn [type arg-string] type))

(defmethod parse-arg :string [_type arg-string]
  arg-string)

(defmethod parse-arg :file [_type arg-string]
  (io/file arg-string))

(defmethod parse-arg :config [_type arg-string]
  (config/load-column-configuration (io/file arg-string)))

(defn pipeline-parameter->cli-desc [{:keys [description type] param-name :name :as param}]
  [:id (keyword param-name)
   :long-opt (str "--" (name param-name))
   :required (string/upper-case param-name)
   :parse-fn (fn [s] (parse-arg type s))
   :desc description])

(def shared-pipeline-parameters
  [{:name        'output-file
    :description "File to write RDF output to"
    :type        :file
    :example "output.ttl"}])

(defn- get-pipeline-parameters [pipeline]
  (concat (:parameters pipeline) shared-pipeline-parameters))

(defn- example-pipeline-argument [{param-name :name example :example}]
  (format "--%s %s" param-name (or example (string/upper-case param-name))))

(defn- get-example-exec-command-line [pipeline]
  (let [params (get-pipeline-parameters pipeline)
        args (map (fn [param] (example-pipeline-argument param)) params)]
    (format "table2qb exec %s %s" (name (:name pipeline)) (string/join " " args))))

(defn- unknown-pipeline [pipelines pipeline-name]
  (binding [*out* *err*]
    (println "Unknown pipeline " pipeline-name)
    (display-pipelines pipelines)))

(defmethod exec-task :describe [{:keys [pipelines] :as describe-task} _all-tasks args]
  (if-let [pipeline-name (first args)]
    (if-let [{:keys [name description] :as pipeline} (find-pipeline pipelines pipeline-name)]
      (let [params (get-pipeline-parameters pipeline)
            opts (mapv pipeline-parameter->cli-desc params)
            {:keys [summary]} (cli/parse-opts [] opts)]
        (println name)
        (println description)
        (println)
        (println "Parameters:")
        (println summary)
        (println)
        (println "To execute pipeline:")
        (println (get-example-exec-command-line pipeline)))
      (do
        (unknown-pipeline pipelines pipeline-name)
        1))
    (binding [*out* *err*]
      (println "Pipeline name required")
      (println "Usage: table2qb describe pipeline-name")
      1)))

(defn- exec-pipeline [{:keys [parameters var] :as pipeline} {:keys [output-file] :as options}]
  (let [args (mapv (fn [param] (get options (keyword (:name param)))) parameters)]
    (with-open [os (io/output-stream output-file)]
      (let [s (gio/rdf-serializer os :format :ttl)]
        (rdf/add s (apply var args))))
    nil))

(defn- parse-and-validate-pipeline-arguments [param-specs args]
  (let [{:keys [options errors]} (cli/parse-opts args param-specs)
        id->opt-name (into {} (map (fn [spec]
                                     (let [{:keys [id long-opt] :as spec-map} (apply hash-map spec)]
                                       [id long-opt]))
                                   param-specs))
        expected-keys (set (keys id->opt-name))
        missing-keys (set/difference expected-keys (set (keys options)))
        missing-args-errors (map (fn [k] (format "Missing required argument %s" (get id->opt-name k))) missing-keys)]
    {:options options
     :errors (concat errors missing-args-errors)}))

(defmethod exec-task :exec [{:keys [pipelines] :as exec-task} all-tasks args]
  (if-let [pipeline-name (first args)]
    (if-let [pipeline (find-pipeline pipelines pipeline-name)]
      (let [params (get-pipeline-parameters pipeline)
            params-spec (mapv pipeline-parameter->cli-desc params)
            {:keys [options errors]} (parse-and-validate-pipeline-arguments params-spec (rest args))]
        (if (seq errors)
          (binding [*out* *err*]
            (println "Invalid pipeline arguments:")
            (doseq [err errors]
              (println err))
            1)
          (exec-pipeline pipeline options)))
      (do
        (unknown-pipeline pipelines pipeline-name)
        1))
    (binding [*out* *err*]
      (println "Pipeline name required")
      (println "Usage: table2qb describe pipeline-name")
      1)))

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
