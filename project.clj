(defproject aloha "1.0.0-SNAPSHOT"
  :description "hello, web"
  :repositories {"jboss" "http://repository.jboss.org/nexus/content/groups/public/"}
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :main aloha.core
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.jboss.netty/netty "3.2.7.Final"]
                 [clj-http "0.2.6"]
                 [potemkin "0.1.1-SNAPSHOT"]]
  :jvm-opts ["-server" "-XX:+UseConcMarkSweepGC"])
