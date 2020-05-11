(defproject swirrl/table2qb "0.3.4-SNAPSHOT"
  :description "Transform tables of observations and reference data into RDF data cube resources specified as csvw"
  :url "http://publishmydata.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :source-paths [] :resource-paths [] ;; explicitly empty so leiningen doesn't set a default which combined with the values from tools.deps would mean duplicates on the classpath
  :profiles {:uberjar {:main table2qb.main
                       :aot :all
                       :uberjar-name "table2qb.jar"
                       :lein-tools-deps/config {:aliases [:with-logging]}}
             :dev {:lein-tools-deps/config {:aliases [:test, :with-logging]}}
             :test {:lein-tools-deps/config {:aliases [:test, :with-logging]}}}
  :aliases { "kaocha" ["with-profile" "+test" "run" "-m" "kaocha.runner"]}
  :main table2qb.main
  :min-lein-version "2.9.1")
