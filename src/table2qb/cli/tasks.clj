(ns table2qb.cli.tasks
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [grafter.rdf.io :as gio]
            [grafter.rdf :as rdf]
            [table2qb.configuration.columns :as column-config]
            [table2qb.configuration.uris :as uri-config]
            [clojure.set :as set]
            [table2qb.util :as util])
  (:import [java.net URI]))

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

(defn- display-pipelines-lines [pipelines]
  (concat ["Available pipelines"
           ""]
          (map (fn [p] (name (:name p))) pipelines)))

(defn display-pipelines [pipelines]
  (display-lines (display-pipelines-lines pipelines)))

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
  (println "Usage: table2qb exec pipeline-name args"))

(defmethod describe-task :uris [{:keys [pipelines] :as uris-task}]
  (println "Usage: table2qb uris pipeline-name [uris-file]")
  (println)
  (println "Lists and describes the URI templates used by a named pipeline")
  (println "If an EDN file containing overriding URI definitions is provided, the resolved URIs that would be used by the pipeline will be displayed")
  (println)
  (display-pipelines pipelines))

(defmethod exec-task :help [_help-task all-tasks args]
  (if-let [task-name (first args)]
    (if-let [task (find-task all-tasks task-name)]
      (describe-task task)
      (throw (ex-info (str "Unknown task name " task-name)
                      {:error-lines (display-tasks-lines all-tasks)})))
    (usage all-tasks)))

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

(defn- is-optional? [param]
  (= true (:optional? param)))

(defn- is-required? [param]
  (not (is-optional? param)))

(defn- get-pipeline-parameters [pipeline]
  (concat (:parameters pipeline) shared-pipeline-parameters))

(defn- example-pipeline-argument [{param-name :name example :example :as param}]
  (let [example (or example (string/upper-case param-name))]
    (if (is-optional? param)
      (format "[--%s %s]" param-name example)
      (format "--%s %s" param-name example))))

(defn- get-example-exec-command-line [pipeline]
  (let [params (get-pipeline-parameters pipeline)
        required-params (remove is-optional? params)
        optional-params (filter is-optional? params)
        args (map (fn [param] (example-pipeline-argument param)) (concat required-params optional-params))]
    (format "table2qb exec %s %s" (name (:name pipeline)) (string/join " " args))))

(defn- get-example-uris-command-line [pipeline]
  (format "table2qb uris %s" (name (:name pipeline))))

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
    (if-let [{:keys [name description uris-resource] :as pipeline} (find-pipeline pipelines pipeline-name)]
      (let [params (get-pipeline-parameters pipeline)
            uris (util/read-edn (io/resource uris-resource))
            summary-rows (map parameter-summary-row params)
            padded-rows (pad-rows summary-rows)]
        (println name)
        (println description)
        (println)
        (println "Parameters:")
        (doseq [row padded-rows]
          (println "  " (row->string row)))
        (println)
        (println "URIs:")
        (let [uri-header ["" "Default"]
              uri-rows (pad-rows (cons uri-header (map (fn [[key uri]] [(str "  " key) uri]) uris)))]
          (doseq [row uri-rows]
            (println (row->string row))))
        (println)
        (println "To describe pipeline URIs:")
        (println (get-example-uris-command-line pipeline))
        (println)
        (println "To execute pipeline:")
        (println (get-example-exec-command-line pipeline)))
      (unknown-pipeline pipelines pipeline-name))
    (throw (ex-info "Pipeline name required"
                    {:error-lines ["Usage: table2qb describe pipeline-name"]}))))

(defn- exec-pipeline [{:keys [parameters var] :as pipeline} {:keys [output-file graph] :as options}]
  (let [args (mapv (fn [param] (get options (keyword (:name param)))) parameters)
        format (if graph :trig :ttl)]
    (with-open [os (io/output-stream output-file)]
      (let [s (gio/rdf-serializer os :format format)]
        (when graph
          (rdf/add s graph (apply var args)))
        (when-not graph
          (rdf/add s (apply var args)))))
    nil))

(defn- parse-and-validate-pipeline-arguments [params args]
  (let [param-specs (mapv pipeline-parameter->cli-desc params)
        {:keys [options errors]} (cli/parse-opts args param-specs)
        required-params (filter is-required? params)
        required-id->opt-name (into {} (map (fn [param]
                                              (let [param-spec (pipeline-parameter->cli-desc param)]
                                                ((juxt :id :long-opt) (apply hash-map param-spec))))
                                            required-params))
        required-keys (set (keys required-id->opt-name))
        missing-keys (set/difference required-keys (set (keys options)))
        missing-args-errors (map (fn [k] (format "Missing required argument %s" (get required-id->opt-name k))) missing-keys)]
    {:options options
     :errors (concat errors missing-args-errors)}))

(defmethod exec-task :exec [{:keys [pipelines] :as exec-task} all-tasks args]
  (if-let [pipeline-name (first args)]
    (if-let [pipeline (find-pipeline pipelines pipeline-name)]
      (let [params (get-pipeline-parameters pipeline)
            {:keys [options errors]} (parse-and-validate-pipeline-arguments params (rest args))]
        (if (seq errors)
          (throw (ex-info "Invalid pipeline arguments:"
                          {:error-lines (vec errors)}))
          (exec-pipeline pipeline options)))
      (unknown-pipeline pipelines pipeline-name))
    (throw (ex-info "Pipeline name required"
                    {:error-lines ["Usage: table2qb describe pipeline-name"]}))))

(defn- load-user-uris-file [file-str]
  ;;TODO: handle file errors, validate loaded EDN
  (util/read-edn (io/file file-str)))

(defmethod exec-task :uris [{:keys [pipelines] :as uri-task} _all-tasks [pipeline-name uris-file & _ignored]]
  (if (some? pipeline-name)
    (if-let [{:keys [uris-resource uri-vars] :as pipeline} (find-pipeline pipelines pipeline-name)]
      (if (some? uris-file)
        (let [resolved-uris (uri-config/resolve-uri-defs (io/resource uris-resource) (io/file uris-file))
              rows (cons ["Name" "Template"] (map (fn [[key uri]] [(str "  " key) uri]) resolved-uris))]
          (doseq [row (pad-rows rows)]
            (println (row->string row))))
        (let [uris (util/read-edn (io/resource uris-resource))
              var-rows (cons ["Name" "Description"] (util/map-keys name uri-vars))
              uri-rows (cons ["Name" "Default"] (map (fn [[key uri]] [(str "  " key) uri]) uris))]
          (println "URIs:")
          (doseq [row (pad-rows uri-rows)]
            (println (row->string row)))
          (println)
          (println "Template variables:")
          (doseq [row (pad-rows var-rows)]
            (println (row->string row)))))
      (unknown-pipeline pipelines pipeline-name))
    (describe-task uri-task)))
