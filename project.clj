(defproject swirrl/table2qb "0.3.1-SNAPSHOT"
  :description "Transform tables of observations and reference data into RDF data cube resources specified as csvw"
  :url "http://publishmydata.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-tools-deps "0.4.1"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:project]}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [grafter/grafter "0.11.2"]
                 [grafter/extra "0.2.2"]
                 [swirrl/csv2rdf "0.2.6"]
                 [org.clojure/data.csv "0.1.4"]
                 [integrant "0.6.3"]
                 [org.clojure/tools.cli "0.3.7"]]
  :profiles {:uberjar {:main table2qb.main
                       :aot :all
                       :uberjar-name "table2qb.jar"
                       :lein-tools-deps/config {:resolve-aliases [:with-logging]}}
             :dev {:lein-tools-deps/config {:resolve-aliases [:with-logging]}
                   :resource-paths ["test/resources"]}}
  :main table2qb.main)
