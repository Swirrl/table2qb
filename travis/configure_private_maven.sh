chown -R travis travis/profiles.clj
chmod +x travis/profiles.clj
mkdir -p /etc/leiningen/
mv travis/profiles.clj /etc/leiningen/profiles.clj
