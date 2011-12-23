(ns aloha.netty
  (:require
    [clojure.tools.logging :as log])
  (:import
    [org.jboss.netty.channel
     Channels
     Channel
     ChannelHandler
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

(def upstream-error-handler
  (reify ChannelUpstreamHandler
    (handleUpstream [_ ctx evt]
      (if (instance? ExceptionEvent evt)
        (log/error (.getCause ^ExceptionEvent evt) "Error in Netty pipeline.")
        (.sendUpstream ctx evt)))))

(def downstream-error-handler
  (reify ChannelDownstreamHandler
    (handleDownstream [_ ctx evt]
      (if (instance? ExceptionEvent evt)
        (log/error (.getCause ^ExceptionEvent evt) "Error in Netty pipeline.")
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
       (.addLast ~pipeline-sym "outgoing-error" downstream-error-handler)
       (.addFirst ~pipeline-sym "incoming-error" upstream-error-handler)
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

