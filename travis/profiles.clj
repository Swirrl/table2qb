{:system
 {:repositories
  [["snapshots"
    {:url "s3p://swirrl-jars/snapshots/"
     :username :env
     :passphrase :env
     :releases false}]
   ["releases"
    {:url "s3p://swirrl-jars/releases/"
     :username :env
     :passphrase :env
     :snapshots false}]]
  :plugins [[s3-wagon-private "1.3.4"]]}}
