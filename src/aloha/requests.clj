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

(def-map-type RequestMap [^HttpRequest netty-request ^Channel netty-channel headers body]
  (get [_ k default-value]
    (case k
      :scheme :http
      :remote-addr (channel-remote-host-address netty-channel)
      :server-name (channel-local-host-address netty-channel)
      :server-port (channel-local-port netty-channel)
      :request-method (request-method netty-request)
      :headers @headers
      :content-type (http-content-type netty-request)
      :character-encoding (http-character-encoding netty-request)
      :uri (request-uri netty-request)
      :query-string (request-query-string netty-request)
      :content-length (http-content-length netty-request)
      :body body
      default-value))
  (assoc [this k v]
    (assoc (into {} this) k v))
  (dissoc [this k]
    (dissoc (into {} this) k))
  (keys [this]
    #{:scheme
      :remote-addr 
      :server-name 
      :server-port 
      :request-method 
      :headers 
      :content-type 
      :character-encoding 
      :uri
      :query-string 
      :content-length
      :body}))

(defn transform-netty-request [^Channel channel ^HttpRequest netty-request]
  (RequestMap.
    netty-request
    channel
    (delay (http-headers netty-request))
    (let [body (.getContent netty-request)]
      (when-not (= 0 (.readableBytes body))
        (ChannelBufferInputStream. body)))))

