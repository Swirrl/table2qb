(defproject table2qb "0.1.0-SNAPSHOT"
  :description "Transform tables of observations and reference data into RDF data cube resources specified as csvw"
  :url "http://publishmydata.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [grafter/grafter "0.12.0-alpha1-SNAPSHOT"]
                 [org.clojure/data.csv "0.1.4"]
                 [net.cgrand/xforms "0.16.0"]])
