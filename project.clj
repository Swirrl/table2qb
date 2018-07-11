(defproject table2qb "0.2.0"
  :description "Transform tables of observations and reference data into RDF data cube resources specified as csvw"
  :url "http://publishmydata.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [grafter/grafter "0.11.2"]
                 [grafter/extra "0.2.2"]
                 [csv2rdf "0.2.4"]
                 [org.clojure/data.csv "0.1.4"]
                 [net.cgrand/xforms "0.16.0"]
                 [integrant "0.6.3"]
                 [org.clojure/tools.cli "0.3.7"]]
  :profiles {:uberjar {:main table2qb.main
                       :uberjar-name "table2qb.jar"
                       :dependencies [[org.apache.logging.log4j/log4j-api "2.11.0"]
                                      [org.apache.logging.log4j/log4j-core "2.11.0"]
                                      [org.apache.logging.log4j/log4j-slf4j-impl "2.11.0"]]}
             :dev {:dependencies [[org.apache.logging.log4j/log4j-api "2.11.0"]
                                  [org.apache.logging.log4j/log4j-core "2.11.0"]
                                  [org.apache.logging.log4j/log4j-slf4j-impl "2.11.0"]]
                   :resource-paths ["test/resources"]}})
