(ns table2qb.cli.tasks
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [grafter-2.rdf4j.io :as gio]
            [grafter-2.rdf.protocols :as pr]
            [table2qb.configuration.columns :as column-config]
            [clojure.set :as set]
            [csv2rdf.csvw :as csvw])
  (:import [java.net URI]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [org.apache.commons.io FileUtils]))

(defn display-lines [lines]
  (doseq [line lines]
    (println line)))

(defn- display-tasks-lines [tasks]
  (concat ["Available tasks are:"
           ""]
          (map (fn [task] (name (:name task))) tasks)))

(defn usage-lines [tasks]
  (concat ["Usage: table2qb task-name [args]"]
          (display-tasks-lines tasks)
          [""
           "Use table2qb help task-name for more information about a task"]))

(defn usage [tasks]
  (display-lines (usage-lines tasks)))

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

(defmethod describe-task :csvw [_task]
  (println "Executes a named pipeline and outputs CSVW")
  (println "Usage table2qb csvw pipeline-name args"))

(defmethod describe-task :exec [_task]
  (println "Executes a named pipeline")
  (println "Usage table2qb exec pipeline-name args"))

(defmethod exec-task :help [_help-task all-tasks args]
  (if-let [task-name (first args)]
    (if-let [task (find-task all-tasks task-name)]
      (describe-task task)
      (throw (ex-info (str "Unknown task name " task-name)
                      {:error-lines (display-tasks-lines all-tasks)})))
    (usage all-tasks)))

(defn- display-pipelines-lines [pipelines]
  (concat ["Available pipelines"
           ""]
          (map (fn [p] (name (:name p))) pipelines)))

(defn display-pipelines [pipelines]
  (display-lines (display-pipelines-lines pipelines)))

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

(defmethod parse-arg :directory [_type arg-string]
  (io/file arg-string))

(defmethod parse-arg :uri [_type arg-string]
  (URI. arg-string))

(defmethod parse-arg :config [_type arg-string]
  (column-config/load-column-configuration (io/file arg-string)))

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
    :example "output.ttl"}
   {:name        'graph
    :description "Target graph for result triples"
    :type        :uri
    :example     "http://example.com/graph/dataset"
    :optional?   true}])

(def shared-csvw-parameters
  [{:name 'output-directory
    :description "Directory to write CSVW to"
    :type :directory
    :example "csvw"}])

(defn- is-optional? [param]
  (= true (:optional? param)))

(defn- is-required? [param]
  (not (is-optional? param)))

(defn- get-pipeline-parameters [pipeline]
  (concat (:parameters pipeline) shared-pipeline-parameters))

(defn- get-pipeline-csvw-parameters [pipeline]
  (concat (:parameters pipeline) shared-csvw-parameters))

(defn- example-pipeline-argument [{param-name :name example :example :as param}]
  (let [example (or example (string/upper-case param-name))]
    (if (is-optional? param)
      (format "[--%s %s]" param-name example)
      (format "--%s %s" param-name example))))

(defn- get-example-pipeline-task-command-line [task-name pipeline-name params]
  (let [required-params (remove is-optional? params)
        optional-params (filter is-optional? params)
        args (map (fn [param] (example-pipeline-argument param)) (concat required-params optional-params))]
    (format "table2qb %s %s %s" task-name (name pipeline-name) (string/join " " args))))

(defn- get-example-exec-command-line [pipeline]
  (let [params (get-pipeline-parameters pipeline)]
    (get-example-pipeline-task-command-line "exec" (:name pipeline) params)))

(defn- get-example-csvw-command-line [pipeline]
  (let [params (get-pipeline-csvw-parameters pipeline)]
    (get-example-pipeline-task-command-line "csvw" (:name pipeline) params)))

(defn- unknown-pipeline [pipelines pipeline-name]
  (throw (ex-info (str "Unknown pipeline " pipeline-name)
                  {:error-lines (display-pipelines-lines pipelines)})))

(defn- pad-to-length
  "Pads a string with spaces to the specified length"
  [^String s length]
  (if (>= (.length s) length)
    s
    (let [sb (StringBuilder. s)]
      (dotimes [_ (- length (.length s))]
        (.append sb \space))
      (.toString sb))))

(defn- pad-rows
  "Takes a sequence of row sequences and pads each string within each column to be the
   length of the longest contained string."
  [rows]
  (when (seq rows)
    (let [column-lengths (apply map (fn [& col]
                                      (apply max (map #(.length %) col))) rows)]
      (map (fn [row]
             (map pad-to-length row column-lengths))
           rows))))

(defn- row->string [row]
  (string/join " " row))

(defn- parameter-summary-row [{param-name :name description :description :as param}]
  [(format "--%s %s" param-name (string/upper-case (name param-name)))
   (if (is-optional? param) "optional" "required")
   description])

(defmethod exec-task :describe [{:keys [pipelines] :as describe-task} _all-tasks args]
  (if-let [pipeline-name (first args)]
    (if-let [{:keys [name description] :as pipeline} (find-pipeline pipelines pipeline-name)]
      (let [params (get-pipeline-parameters pipeline)
            summary-rows (map parameter-summary-row params)
            padded-rows (pad-rows summary-rows)]
        (println name)
        (println description)
        (println)
        (println "Parameters:")
        (doseq [row padded-rows]
          (println "  " (row->string row)))
        (println)
        (println "To generate pipeline CSVW:")
        (println (get-example-csvw-command-line pipeline))
        (println)
        (println "To execute pipeline:")
        (println (get-example-exec-command-line pipeline)))
      (unknown-pipeline pipelines pipeline-name))
    (throw (ex-info "Pipeline name required"
                    {:error-lines ["Usage: table2qb describe pipeline-name"]}))))

(defn- write-csvw-rdf [metadata-file {:keys [output-file graph] :as options}]
  (with-open [os (io/output-stream output-file)]
    (let [s (gio/rdf-writer os :format (if graph :trig :ttl))
          csvw-opts {:mode :annotated}]
      (when graph
        (pr/add s graph (csvw/csv->rdf nil metadata-file csvw-opts)))
      (when-not graph
        (pr/add s (csvw/csv->rdf nil metadata-file csvw-opts)))))
  nil)

(defn- parse-pipeline-arguments [params args]
  (let [param-specs (mapv pipeline-parameter->cli-desc params)
        {:keys [options errors]} (cli/parse-opts args param-specs)
        required-params (filter is-required? params)
        required-id->opt-name (into {} (map (fn [param]
                                              (let [param-spec (pipeline-parameter->cli-desc param)]
                                                ((juxt :id :long-opt) (apply hash-map param-spec))))
                                            required-params))
        required-keys (set (keys required-id->opt-name))
        missing-keys (set/difference required-keys (set (keys options)))
        missing-args-errors (map (fn [k] (format "Missing required argument %s" (get required-id->opt-name k))) missing-keys)
        errors (concat errors missing-args-errors)]
    (if (seq errors)
      (throw (ex-info "Invalid pipeline arguments:"
                      {:error-lines (vec errors)}))
      options)))

(defn- args-pipeline [pipelines args]
  (if-let [pipeline-name (first args)]
    (if-let [pipeline (find-pipeline pipelines pipeline-name)]
      pipeline
      (unknown-pipeline pipelines pipeline-name))
    (throw (ex-info "Pipeline name required"
                    {:error-lines ["Usage: table2qb describe pipeline-name"]}))))

(defmethod exec-task :csvw [{:keys [pipelines] :as csvw-task} _all-tasks args]
  (let [{:keys [table2qb/pipeline-fn] :as pipeline} (args-pipeline pipelines args)
        params (get-pipeline-csvw-parameters pipeline)
        {:keys [output-directory] :as arguments} (parse-pipeline-arguments params (rest args))]
    (.mkdirs output-directory)
    (let [{:keys [metadata-file]} (pipeline-fn output-directory arguments)]
      (println "To generate RDF with csv2rdf run the following command:")
      (printf "java -jar csv2rdf.jar -u %s -m annotated -o output.ttl%n" (.getAbsolutePath metadata-file))
      (flush))))

(defmethod exec-task :exec [{:keys [pipelines] :as exec-task} all-tasks args]
  (let [{:keys [table2qb/pipeline-fn] :as pipeline} (args-pipeline pipelines args)
        params (get-pipeline-parameters pipeline)
        arguments (parse-pipeline-arguments params (rest args))
        csvw-dir (.toFile (Files/createTempDirectory "table2qb" (make-array FileAttribute 0)))]
    (try
      (let [{:keys [metadata-file]} (pipeline-fn csvw-dir arguments)]
        (write-csvw-rdf metadata-file arguments))
      (finally
        (FileUtils/deleteDirectory csvw-dir)))))
