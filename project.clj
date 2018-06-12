(defproject table2qb "0.1.3-SNAPSHOT"
  :description "Transform tables of observations and reference data into RDF data cube resources specified as csvw"
  :url "http://publishmydata.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [grafter/grafter "0.11.2"]
                 [grafter/extra "0.2.2-grafter-0.11.2-SNAPSHOT"]
                 [csv2rdf "0.2.4"]
                 [org.clojure/data.csv "0.1.4"]
                 [net.cgrand/xforms "0.16.0"]
                 [environ "1.1.0"]]
  :plugins [[lein-environ "1.1.0"]]
  :profiles {:dev {:env {:base-uri "http://gss-data.org.uk/"}}})
