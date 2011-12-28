;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns aloha.requests
  (:use
    [potemkin]
    [aloha netty utils])
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log])
  (:import
    [org.jboss.netty.channel
     Channel]
    [org.jboss.netty.handler.codec.http
     HttpRequest]
    [java.nio.channels
     Pipe
     Channels]
    [org.jboss.netty.buffer
     ChannelBufferInputStream]))

;;;

(def-custom-map LazyMap
  :get
  (fn [_ data _ key default-value]
    `(if-not (contains? ~data ~key)
       ~default-value
       (let [val# (get ~data ~key)]
         (if (delay? val#)
           @val#
           val#)))))

(defn lazy-map [& {:as m}]
  (LazyMap. m))

(defn assoc-request-body [request ^HttpRequest netty-request]
  (if-not (.isChunked netty-request)
    (let [body (.getContent netty-request)]
      (assoc request
        :body (when-not (= 0 (.readableBytes body))
                (ChannelBufferInputStream. body))))
    (let [pipe (Pipe/open)]
      (with-meta
        (assoc request
          :body (Channels/newInputStream (.source pipe)))
        {::output-stream (Channels/newOutputStream (.sink pipe))}))))

(defn transform-netty-request [^Channel channel ^HttpRequest netty-request]
  (let [request (lazy-map
                  :scheme :http
                  :remote-addr (delay (channel-remote-host-address channel))
                  :server-name (delay (channel-local-host-address channel))
                  :server-port (delay (channel-local-port channel))
                  :request-method (delay (request-method netty-request))
                  :headers (delay (http-headers netty-request))
                  :content-type (delay (http-content-type netty-request))
                  :character-encoding (delay (http-character-encoding netty-request))
                  :uri (delay (request-uri netty-request))
                  :query-string (delay (request-query-string netty-request))
                  :content-length (delay (http-content-length netty-request)))]
    (assoc-request-body request netty-request)))

