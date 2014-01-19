;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns aloha.core
  (:require
    [aloha.netty :as n]))

(defn -main [& args]
  (n/start-server 8080)
  (println "Server listening on port 8080.")
  (Thread/sleep 1e9))
