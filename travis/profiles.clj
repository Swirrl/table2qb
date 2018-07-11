{:system
  {:repositories
  [["swirrl-jars-snapshots"
    {:url "s3p://swirrl-jars/snapshots/"
     :username :env
     :passphrase :env
     :releases false}]
   ["swirrl-jars-releases"
    {:url "s3p://swirrl-jars/releases/"
     :username :env
     :passphrase :env
     :snapshots false}]]}}