(defproject swirrl/table2qb "0.3.1"
  :description "Transform tables of observations and reference data into RDF data cube resources specified as csvw"
  :url "http://publishmydata.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-tools-deps "0.4.3"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :profiles {:uberjar {:main table2qb.main
                       :aot :all
                       :uberjar-name "table2qb.jar"
                       :lein-tools-deps/config {:resolve-aliases [:with-logging]}}
             :dev {:lein-tools-deps/config {:resolve-aliases [:with-logging]}
                   :resource-paths ["test/resources"]}}
  :main table2qb.main)
