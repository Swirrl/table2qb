
INSTALL_SCRIPT=linux-install-1.10.1.502.sh

curl -O https://download.clojure.org/install/$INSTALL_SCRIPT
chmod +x $INSTALL_SCRIPT
sudo ./$INSTALL_SCRIPT

clojure -Sdescribe


curl -O https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
chmod +x lein
sudo mv lein /usr/local/bin

pushd ..

lein version

popd
