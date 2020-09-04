(ns table2qb.main-test
  (:require [table2qb.main :as sut]
            [clojure.test :as t]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [grafter-2.rdf4j.io :as gio])
  (:import [java.io File]))

(defmacro with-temp-file
  "Create a temp file and bind it to the supplied symbol, then clean the
  file up afterwards."
  [tf & body]
  `(let [~tf (File/createTempFile "output" ".ttl")]

     ~@body
     (io/delete-file ~tf)))

(def unix-error? #(not= 0 %))

(def unix-success? #(= 0 %))



(defn table2qb-main
  "Execute table2qb's inner-main wrapped in some bindings to assist
  capture stdout and stderr for the tests."
  [args]
  (let [err-sw (java.io.StringWriter.)
        stderr (java.io.PrintWriter. err-sw)
        out-sw (java.io.StringWriter.)
        stdout (java.io.PrintWriter. out-sw)
        ret (binding [*err* stderr
                      *out* stdout]
              (sut/cli-main args))]
    {:status ret
     :err (str err-sw)
     :out (str out-sw)}))

(t/deftest main-test

  (t/testing "Calling with invalid arguments"
    (let [{:keys [err status]} (table2qb-main ["exec" "cube-pipeline"])]
      (t/is (unix-error? status)
            "Returns a UNIX error code")
      (t/is (str/includes? err
                           "Missing required argument")
            "Prints error message to stderr")))

  (t/testing "codelist-pipeline"
    (with-temp-file output-file
      (let [{:keys [status err out]}
            (table2qb-main ["exec" "codelist-pipeline" "--codelist-csv" "./examples/employment/csv/gender.csv" "--codelist-name" "gender" "--output-file" (str output-file) "--codelist-slug" "gender" "--base-uri" "http://base/uri/"])]

        (t/is (> (count (gio/statements output-file))
                 1)
              "Contains valid RDF") ;; don't check the details too much here... other tests do that.

        (t/is (unix-success? status))
        (t/is (str/blank? err)
              "Nothing printed to stderr"))))

  (t/testing "components-pipeline"
    (with-temp-file output-file
      (let [{:keys [status err out]}
            (table2qb-main ["exec" "components-pipeline"
                            "--output-file" (str output-file)
                            "--base-uri" "http://foo.bar/base/"
                            "--input-csv" "./examples/employment/csv/components.csv"])]


        (t/is (unix-success? status))
        (t/is (str/blank? err)
              "Nothing printed to stderr")

        (t/is (> (count (gio/statements output-file))
                 1)
              ;; don't check the details too much here... other tests
              ;; do that.
              "Contains valid RDF"))))

  (t/testing "cube-pipeline"
    (with-temp-file output-file
      (let [{:keys [status err out]}
            (table2qb-main ["exec" "cube-pipeline"
                            "--output-file" (str output-file)
                            "--base-uri" "http://foo.bar/base/"
                            "--input-csv" "./examples/employment/csv/input.csv"
                            "--dataset-slug" "employment"
                            "--dataset-name" "Employment"
                            "--column-config" "./examples/employment/columns.csv"])]

        (t/is (unix-success? status))
        (t/is (= "" err)
              "Nothing printed to stderr")

        (t/is (> (count (gio/statements output-file))
                 1)
              ;; don't check the details too much here... other tests
              ;; do that.
              "Contains valid RDF")))))










(comment

  (sut/cli-main ["exec" "cube-pipeline" ])

  (sut/cli-main ["exec" "codelist-pipeline" "--codelist-csv" "./examples/employment/csv/gender.csv" "--codelist-name" "gender" "--output-file" "output.ttl" "--codelist-slug" "gender" "--base-uri" "http://base/uri"])

  (sut/cli-main ["exec" "components-pipeline" "--output-file" "comp-output.ttl" "--base-uri" "http://foo.bar/base/" "--input-csv" "./examples/employment/csv/components.csv"])

  )
