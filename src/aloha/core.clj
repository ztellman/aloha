(ns aloha.core
  (:use [aloha netty]))

(defn echo-pipeline [channel-group]
  (create-netty-pipeline "echo-server" channel-group
    :main-handler (upstream-stage
                    (fn [evt]
                      (when-let [msg (message-event evt)]
                        (.write (.getChannel evt) msg))
                      nil))))

(defn start-echo-server []
  (start-server echo-pipeline {:port 10000}))
