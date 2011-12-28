;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns aloha.netty
  (:require
    [clojure.tools.logging :as log])
  (:import
    [org.jboss.netty.channel
     Channels
     Channel
     ChannelFutureListener
     ChannelUpstreamHandler
     ChannelDownstreamHandler
     ChannelPipelineFactory
     ExceptionEvent
     MessageEvent
     ChannelEvent]
    [org.jboss.netty.channel.group
     DefaultChannelGroup
     ChannelGroup]
    [org.jboss.netty.bootstrap
     ServerBootstrap]
    [org.jboss.netty.channel.socket.nio
     NioServerSocketChannelFactory]
    [java.util.concurrent
     Executors]
    [java.net
     InetSocketAddress]))

(defn upstream-error-handler [pipeline-name]
  (reify ChannelUpstreamHandler
    (handleUpstream [_ ctx evt]
      (if (instance? ExceptionEvent evt)
        (log/error (.getCause ^ExceptionEvent evt) (str "error in " pipeline-name))
        (.sendUpstream ctx evt)))))

(defn downstream-error-handler [pipeline-name]
  (reify ChannelDownstreamHandler
    (handleDownstream [_ ctx evt]
      (if (instance? ExceptionEvent evt)
        (log/error (.getCause ^ExceptionEvent evt) (str "error in " pipeline-name))
        (.sendDownstream ctx evt)))))

(defn connection-handler [^ChannelGroup channel-group]
  (let [latch (atom false)]
    (reify ChannelUpstreamHandler
      (handleUpstream [_ ctx evt]
        (when (and
                (instance? ChannelEvent evt)
                (compare-and-set! latch false (-> ^ChannelEvent evt .getChannel .isOpen)))
          (.add channel-group (.getChannel ^ChannelEvent evt)))
        (.sendUpstream ctx evt)))))

(defmacro create-netty-pipeline
  [pipeline-name channel-group & stages]
  (let [pipeline-sym (gensym "pipeline")]
    `(let [~pipeline-sym (Channels/pipeline)
           channel-group# ~channel-group]
       ~@(map
           (fn [[stage-name stage]]
             `(.addLast ~pipeline-sym ~(name stage-name) ~stage))
           (partition 2 stages))
       (.addFirst ~pipeline-sym "channel-group-handler" (connection-handler channel-group#))
       (.addLast ~pipeline-sym "outgoing-error" (downstream-error-handler ~pipeline-name))
       (.addFirst ~pipeline-sym "incoming-error" (upstream-error-handler ~pipeline-name))
       ~pipeline-sym)))

(defn create-pipeline-factory [channel-group pipeline-generator]
  (reify ChannelPipelineFactory
    (getPipeline [_]
      (pipeline-generator channel-group))))

;;;

(defn ^ChannelUpstreamHandler upstream-stage
  "Creates a pipeline stage for upstream events."
  [handler]
  (reify ChannelUpstreamHandler
    (handleUpstream [_ ctx evt]
      (if-let [upstream-evt (handler evt)]
	(.sendUpstream ctx upstream-evt)
	(.sendUpstream ctx evt)))))

(defn ^ChannelDownstreamHandler downstream-stage
  "Creates a pipeline stage for downstream events."
  [handler]
  (reify ChannelDownstreamHandler
    (handleDownstream [_ ctx evt]
      (if-let [downstream-evt (handler evt)]
	(.sendDownstream ctx downstream-evt)
	(.sendDownstream ctx evt)))))

(defn message-event
  "Returns contents of message event, or nil if it's a different type of message."
  [evt]
  (when (instance? MessageEvent evt)
    (.getMessage ^MessageEvent evt)))

(defn ^ChannelUpstreamHandler message-handler
  [handler]
  (reify ChannelUpstreamHandler
    (handleUpstream [_ ctx evt]
      (if-let [msg (message-event evt)]
        (handler msg (.getChannel evt))
        (.sendUpstream ctx evt)))))

(defn write-to-channel [^Channel ch msg on-complete]
  (let [channel-future (.write ch msg)]
    (when on-complete
      (.addListener channel-future
        (reify ChannelFutureListener
          (operationComplete [_ future]
            (on-complete)))))))

;;;

(def default-server-options
  {"child.reuseAddress" true,
   "reuseAddress" true,
   "child.keepAlive" true,
   "child.connectTimeoutMillis" 100,
   "tcpNoDelay" true,
   "readWriteFair" true,
   "child.tcpNoDelay" true})

(defn start-server [pipeline-generator options]
  (let [port (:port options)
        channel-factory (NioServerSocketChannelFactory.
                          (Executors/newCachedThreadPool)
                          (Executors/newCachedThreadPool))
        channel-group (DefaultChannelGroup.)
        server (ServerBootstrap. channel-factory)]
    
    (.setPipelineFactory server
      (create-pipeline-factory channel-group pipeline-generator))

    (doseq [[k v] (merge default-server-options (:netty options))]
      (.setOption server k v))

    (.add channel-group (.bind server (InetSocketAddress. port)))

    (fn []
      (let [close-future (.close channel-group)]
        (future
          (.awaitUninterruptibly close-future)
          (.releaseExternalResources server))))))

