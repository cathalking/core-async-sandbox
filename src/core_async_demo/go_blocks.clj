(ns core-async-demo.go-blocks
  (:require [clojure.core.async :as async]))

(def channel (async/chan))

;;Go-Blocks
(async/go
  (println (println (str "Taken Message: " (async/<!! channel)))))

(async/>!! channel "101")

;;Looping construct
(async/go-loop []
  (let [val (async/<!! channel)] 
    (if-not (nil? val) 
      (do
       (println (str "Taken Message: " val))
       (recur))
      (println "Channel closed: Stop Trying to take"))))

(async/>!! channel "101")
(async/>!! channel "102")

;;closed channel returns nil
(async/close! channel)

