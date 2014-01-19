;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns aloha.netty
  (:require
    [clojure.tools.logging :as log]
    [byte-streams :as bs])
  (:import
    [io.netty.buffer
     Unpooled
     PooledByteBufAllocator]
    [io.netty.channel
     ChannelPipeline
     ChannelHandler
     ChannelOption
     ChannelInboundHandler
     ChannelOutboundHandler
     ChannelFutureListener]
    [io.netty.handler.codec.http
     HttpServerCodec
     HttpRequest
     HttpHeaders
     HttpVersion
     HttpResponseStatus
     DefaultFullHttpResponse]
    [io.netty.bootstrap
     ServerBootstrap]
    [io.netty.channel.nio
     NioEventLoopGroup]
    [io.netty.channel.socket.nio
     NioServerSocketChannel]))

(defmacro channel-handler
  [& {:as handlers}]
  `(reify
     ChannelHandler
     ChannelInboundHandler
     ChannelOutboundHandler
     
     (handlerAdded
       ~@(or (:handler-added handlers) `([_ _])))
     (handlerRemoved
       ~@(or (:handler-removed handlers) `([_ _])))
     (exceptionCaught
       ~@(or (:exception-caught handlers)
           `([_ ctx# cause#]
               (.fireExceptionCaught ctx# cause#))))
     (channelRegistered
       ~@(or (:channel-registered handlers)
           `([_ ctx#]
               (.fireChannelRegistered ctx#))))
     (channelUnregistered
       ~@(or (:channel-unregistered handlers)
           `([_ ctx#]
               (.fireChannelUnregistered ctx#))))
     (channelActive
       ~@(or (:channel-active handlers)
           `([_ ctx#]
               (.fireChannelActive ctx#))))
     (channelInactive
       ~@(or (:channel-inactive handlers)
           `([_ ctx#]
               (.fireChannelInactive ctx#))))
     (channelRead
       ~@(or (:channel-read handlers)
           `([_ ctx# msg#]
               (.fireChannelRead ctx# msg#))))
     (channelReadComplete
       ~@(or (:channel-read-complete handlers)
           `([_ ctx#]
               (.fireChannelReadComplete ctx#))))
     (userEventTriggered
       ~@(or (:user-event-triggered handlers)
           `([_ ctx# evt#]
               (.userEventTriggered ctx# evt#))))
     (channelWritabilityChanged
       ~@(or (:channel-writability-changed handlers)
           `([_ ctx#]
               (.fireChannelWritabilityChanged ctx#))))
     (bind
       ~@(or (:bind handlers)
           `([_ ctx# local-address# promise#]
               (.bind ctx# local-address# promise#))))
     (connect
       ~@(or (:connect handlers)
           `([_ ctx# remote-address# local-address# promise#]
               (.connect ctx# remote-address# local-address# promise#))))
     (disconnect
       ~@(or (:disconnect handlers)
           `([_ ctx# promise#]
               (.disconnect ctx# promise#))))
     (close
       ~@(or (:close handlers)
           `([_ ctx# promise#]
               (.close ctx# promise#))))
     (read
       ~@(or (:read handlers)
           `([_ ctx#]
               (.read ctx#))))
     (write
       ~@(or (:write handlers)
           `([_ ctx# msg# promise#]
               (.write ctx# msg# promise#))))
     (flush
       ~@(or (:flush handlers)
           `([_ ctx#]
               (.flush ctx#))))))

(defn pipeline-initializer [pipeline-builder]
  (channel-handler

    :channel-registered
    ([this ctx]
      (let [pipeline (.pipeline ctx)]
        (try
          (.remove pipeline this)
          (pipeline-builder pipeline)
          (.fireChannelRegistered ctx)
          (catch Throwable e
            (log/warn e "Failed to initialize channel")
            (.close ctx)))))))

(def body (bs/to-byte-array "Aloha!"))

(defn http-handler []
  (channel-handler
    
    :exception-handler
    ([_ ctx ex]
       (log/error ex "error in netty pipeline"))
    
    :channel-read
    ([_ ctx msg]
       (when (instance? HttpRequest msg)
         (let [keep-alive? (HttpHeaders/isKeepAlive msg)
               rsp (DefaultFullHttpResponse.
                     HttpVersion/HTTP_1_1
                     HttpResponseStatus/OK
                     (Unpooled/wrappedBuffer body)
                     false)]
           (-> rsp .headers (.set "Content-Type" "text/plain"))
           (-> rsp .headers (.set "Content-Length" (-> rsp .content .readableBytes)))
           (if keep-alive?
             (do
               (-> rsp .headers (.set "Connection" "keep-alive"))
               (-> ctx (.writeAndFlush rsp)))
             (-> ctx (.writeAndFlush rsp) (.addListener ChannelFutureListener/CLOSE))))))))

(defn http-initializer [^ChannelPipeline pipeline]
  (.addLast pipeline "codec" (HttpServerCodec. 4096 8192 8192 false))
  (.addLast pipeline "handler" (http-handler)))

(defn start-server [port]
  (let [group (NioEventLoopGroup. 32)]
    (try
      (let [b (doto (ServerBootstrap.)
                (.option ChannelOption/SO_BACKLOG (int 1024))
                (.group group)
                (.channel NioServerSocketChannel)
                (.childHandler (pipeline-initializer http-initializer))
                (.childOption ChannelOption/ALLOCATOR PooledByteBufAllocator/DEFAULT))
            ch (-> b (.bind port) .sync .channel)]
        (fn []
          (-> ch .close .sync)
          (.shutdownGracefully group))))))


