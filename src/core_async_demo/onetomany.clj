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


;;Mixing channels
(def ch-out (async/chan 10))
(def mix-out (async/mix ch-out))


(def channel1 (async/chan 10))
(def channel2 (async/chan 10))

;;lets add our two channels to the mix
(async/admix mix-out channel1)
(async/admix mix-out channel2)

(async/go-loop []
  (let [val (async/<!! ch-out)]
    (if-not (nil? val)
      (do
        (println (async/<!! ch-out))
        (recur)))))

(async/go-loop []
  (async/<!! (async/timeout 1000))
  (async/>!! channel1 "I'm from channel1")
  (recur))

(async/go-loop []
  (async/<!! (async/timeout 1000))
  (async/>!! channel2 "I'm from channel2")
  (recur))

;;lets mute channel1
(async/toggle mix-out {channel1 {:mute true}})

;;lets unmute channel1
(async/toggle mix-out {channel1 {:mute false}})

;lets only process values on channel2
(async/toggle mix-out {channel2 {:solo true}})

;lets pause channel2
(async/toggle mix-out {channel2 {:pause true}})

;close output channel
(async/close! ch-out)