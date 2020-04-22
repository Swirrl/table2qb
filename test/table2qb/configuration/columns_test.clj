(ns table2qb.configuration.columns-test
  (:require [clojure.test :refer :all]
            [table2qb.configuration.columns :refer :all]
            [table2qb.util :refer [exception?]]))

(deftest load-column-configuration-test
  (testing "input validation"
    (testing "required columns"
      (is
        (= #{"title" "name" "property_template"}
           (try
             (load-column-configuration (.getBytes "column-a\nvalue-1"))
             (catch clojure.lang.ExceptionInfo e
               (:missing-columns (ex-data e)))))))
    (testing "value validation"
      (letfn [(is-validated-that [csv matcher]
                (is (thrown-with-msg?
                      Throwable matcher
                      (load-column-configuration (.getBytes csv)))))]
        (is-validated-that
          "title,name,property_template
           ,reference_period,http://purl.org/linked-data/sdmx/2009/dimension#refPeriod"
          #"\"title\", row 1: Value cannot be blank")

        (is-validated-that
          "title,name,property_template
           reference period,,http://purl.org/linked-data/sdmx/2009/dimension#refPeriod"
          #"\"name\", row 1: Value cannot be blank")

        (is-validated-that
          "title,name,property_template
           reference period,reference-period,http://purl.org/linked-data/sdmx/2009/dimension#refPeriod"
          #"csvw:name cannot contain hyphens")

        (is-validated-that
          "title,name,property_template,component_attachment
           reference period,reference_period,http://purl.org/linked-data/sdmx/2009/dimension#refPeriod,not_an_option"
          #"\"component_attachment\", row 1: Value must be blank or one of qb:dimension, qb:measure or qb:attribute")

        (is-validated-that
          "title,name,property_template,value_transformation
           reference period,reference_period,http://purl.org/linked-data/sdmx/2009/dimension#refPeriod,renticulate"
          #"\"value_transformation\", row 1: Expected one of: slugize, unitize")))))
