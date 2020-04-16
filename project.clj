(defproject swirrl/table2qb "0.3.3-SNAPSHOT"
  :description "Transform tables of observations and reference data into RDF data cube resources specified as csvw"
  :url "http://publishmydata.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :profiles {:uberjar {:main table2qb.main
                       :aot :all
                       :uberjar-name "table2qb.jar"
                       :lein-tools-deps/config {:aliases [:with-logging]}}
             :dev {:lein-tools-deps/config {:aliases [:with-logging]}}
             :test {:lein-tools-deps/config {:aliases [:test, :with-logging]}}}
  :main table2qb.main
  :min-lein-version "2.9.1")
