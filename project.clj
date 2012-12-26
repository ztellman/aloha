(defproject aloha "1.0.2-SNAPSHOT"
  :description "a simple, friendly webserver"
  :repositories {"jboss" "http://repository.jboss.org/nexus/content/groups/public/"
                 "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :main aloha.core
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [io.netty/netty "3.5.11.Final"]
                 [clj-http "0.3.6"]
                 [org.clojure/tools.cli "0.2.1"]
                 [potemkin "0.2.0-SNAPSHOT"]]
  :jvm-opts ["-server" "-XX:+UseConcMarkSweepGC"])
