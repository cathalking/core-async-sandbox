(ns core-async-demo.channels
  (:require [clojure.core.async :as async]))

;;create unbuffered channel
(def unbuffered (async/chan))

;;put something on channel
(async/put! unbuffered "Hello" (fn [_] (println "Put Message: Hello")))

;;take value from channel
(async/take! unbuffered #(println (str "Taken Message: " %)))

;;create a channel with buffer size 2
(def buffered-channel (async/chan 2))

;;put stuff on channel
(async/put! buffered-channel "Hello" (fn [_] (println "Put Message: Hello")))
(async/put! buffered-channel "World" (fn [_] (println "Put Message: World")))
(async/put! buffered-channel "Blocked!!" (fn [_] (println "Put Message: Blocked!!")))

;;take value from channel
(dotimes [times 3]
  (async/take! buffered-channel #(println (str "Taken Message: " %))))

(async/>!! buffered-channel "Hello")
(async/<!! buffered-channel)