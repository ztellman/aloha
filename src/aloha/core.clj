;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns aloha.core
  (:use [aloha netty requests responses])
  (:import
    [org.jboss.netty.channel
     Channel]
    [org.jboss.netty.handler.codec.http
     HttpHeaders
     HttpMessage
     HttpRequest
     HttpChunkAggregator
     HttpRequestDecoder
     HttpResponseEncoder
     HttpContentCompressor]))

(def error-response
  {:status 500,
   :headers {:content-type "text/plain"}
   :body "Uncaught exception in handler."})

(defn http-request-handler [handler]
  (let [current-request (atom nil)]
    (message-handler
      (fn [^HttpRequest msg ^Channel channel]
        (let [request (transform-netty-request channel msg)]
          (let [response (try
                           (handler request)
                           (catch Exception e
                             error-response))
                keep-alive? (HttpHeaders/isKeepAlive msg)]
            (respond channel
              (transform-response response keep-alive?)
              (:body response)
              (when-not keep-alive?
                #(.close channel)))))))))

(defn http-pipeline [handler]
  (fn [channel-group]
    (create-netty-pipeline "http-server" channel-group
      :deocder (HttpRequestDecoder.)
      :aggregator (HttpChunkAggregator. 1048576)
      :encoder (HttpResponseEncoder.)
      :deflater (HttpContentCompressor.)
      :handler (http-request-handler handler))))

(defn start-http-server [handler options]
  (start-server
    (http-pipeline handler)
    options))

(defn start-hello-world-server []
  (start-http-server
    (fn [request]
      {:status 200
       :headers {"content-type" "text/plain"}
       :body "Aloha!\n"})
    {:port 8080}))

(defn -main [& args]
  (start-hello-world-server)
  (println "Server listening on port 8080."))
