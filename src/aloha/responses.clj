;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns aloha.responses
  (:use
    [aloha netty utils])
  (:require
    [clojure.string :as str])
  (:import
    [java.nio
     ByteBuffer]
    [java.io
     InputStream
     File
     RandomAccessFile]
    [org.jboss.netty.buffer
     ChannelBuffers]
    [org.jboss.netty.handler.codec.http
     HttpChunk
     DefaultHttpChunk
     HttpHeaders
     DefaultHttpResponse
     HttpResponse
     HttpResponseStatus
     HttpVersion]
    [java.net
     URLConnection]
    [java.nio.channels
     FileChannel
     FileChannel$MapMode]))

;;;

(defn write-chunk [channel ^ByteBuffer chunk callback]
  (write-to-channel channel
    (if chunk
      (DefaultHttpChunk. (ChannelBuffers/wrappedBuffer chunk))
      (HttpChunk/LAST_CHUNK))
    callback))

(defn send-input-stream
  [channel ^InputStream stream chunk-size callback]
  (let [buffer? (and chunk-size (pos? chunk-size))
	chunk-size (if buffer? chunk-size 1024)
	create-array (if buffer?
		       #(byte-array chunk-size)
		       #(byte-array
			  (if (pos? (.available stream))
			    (.available stream)
			    1024)))]
    (loop [ary ^bytes (create-array), offset 0]
      (let [ary-len (count ary)]
	(if (= ary-len offset)
	  (do
            (write-chunk channel (ByteBuffer/wrap ary) nil)
	    (recur (create-array) 0))
	  (let [byte-count (.read stream ary offset (- ary-len offset))]
	    (if (neg? byte-count)
	      (do
                (.close stream)
                (if (zero? offset)
                  (write-chunk channel nil callback)
                  (do
                    (write-chunk channel (ByteBuffer/wrap ary 0 offset) nil)
                    (write-chunk channel nil callback))))
	      (recur ary (+ offset byte-count)))))))))

(defn respond-with-input-stream [channel ^HttpResponse response body callback]
  (.setHeader response "Transfer-Encoding" "chunked")
  (write-to-channel channel response nil)
  (doto
    (Thread. (fn [] (send-input-stream channel body 8192 callback)))
    (.setName "InputStream reader")
    .start))

(defn respond-with-file [channel ^HttpResponse response ^File body callback]
  (let [content-type (or (http-content-type response)
                       (let [content-type (or (URLConnection/guessContentTypeFromName (.getName body))
                                            "application/octet-stream")]
                         (.setHeader response "Content-Type" content-type)
                         content-type))
        fc (.getChannel (RandomAccessFile. body "r"))]
    (.setContent response
      (ChannelBuffers/wrappedBuffer (.map fc FileChannel$MapMode/READ_ONLY 0 (.size fc))))
    (HttpHeaders/setContentLength response (-> response .getContent .readableBytes))
    (write-to-channel channel response #(do (.close fc) (when callback (callback))))))

(defn respond-with-string [channel ^HttpResponse response ^String body callback]
  (let [encoding (or (http-character-encoding response)
                   (do
                     (.setHeader response "Content-Type"
                       (str (.getHeader response "Content-Type") "; charset=utf-8"))
                     "utf-8"))]
    (.setContent response (-> body (.getBytes encoding) ChannelBuffers/wrappedBuffer))
    (HttpHeaders/setContentLength response (-> response .getContent .readableBytes))
    (write-to-channel channel response callback)))

(defn respond-with-nothing [channel response callback]
  (HttpHeaders/setContentLength response 0)
  (write-to-channel channel response callback))

(defn respond [channel response body callback]
  (cond
    (= nil body)
    (respond-with-nothing channel response callback)
    
    (instance? String body)
    (respond-with-string channel response body callback)

    (instance? File body)
    (respond-with-file channel response body callback)

    (sequential? body)
    (respond-with-string channel response (apply str (map str body)) callback)

    (instance? InputStream body)
    (respond-with-input-stream channel response body callback)))

;;;

(defn format-header-key
  "content-length -> Content-Length"
  [s]
  (->> (str/split (name s) #"-")
    (map str/capitalize)
    (str/join "-")))

(defn transform-response [rsp keep-alive?]
  (let [response (DefaultHttpResponse.
                   (HttpVersion/HTTP_1_1)
                   (HttpResponseStatus/valueOf (:status rsp)))]
    (doseq [[k v] (:headers rsp)]
      (.setHeader response (format-header-key k) v))
    (.setHeader response "Server" "aloha/1.0.2")
    (.setHeader response "Connection" (if keep-alive? "keep-alive" "close"))
    response))
