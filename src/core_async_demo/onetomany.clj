(ns core-async-demo.onetomany
  (:require [clojure.core.async :as async]))

(def from (async/chan 10))

(defn start-count [type channel]
  (async/go-loop [count 0]
    (if (nil? (async/<!! channel))
      (println (str  type " Count: " count "\n"))
      (recur (inc count)))))

;;SPLIT by type

;                          > numbers-chan --> (Count number values)
;                         /
;                        /
;; from-channel---------
;                        \
;                         \
;                          > string-chan --> (Count String values)
(def partitioned-channels (async/split number? from))

(start-count "Number" (first partitioned-channels))
(start-count "String" (last partitioned-channels))

(async/>!! from 100)
(async/>!! from "Hello World")
(async/>!! from "Jack Daniels")

(async/close! from)

;;Pub Sub
;
;                                             >(harp-consumer)----->harp-channel
;                                            /
;                                           /
;; beer-chan -->(beer-publisher)----------->
;;                                          \
;                                            \
;                                             >(carlsberg-consumer)------>carlsberg-channel
;
(def beer-chan (async/chan 1))
(def harp-channel (async/chan 1))
(def carlsberg-channel (async/chan 1))


(def beer-publisher (async/pub beer-chan :beer))
(async/sub beer-publisher :harp harp-channel)
(async/sub beer-publisher :carlsberg carlsberg-channel)

(async/go-loop []
  (async/<!! harp-channel)
  (println (str "Found Harp"))
  (recur))

(async/go-loop []
  (async/<!! carlsberg-channel)
  (println (str "Found Carlsberg"))
  (recur))

(async/>!! beer-chan {:beer :carlsberg})
(async/>!! beer-chan {:beer :harp})
(async/>!! beer-chan {:beer :bud})