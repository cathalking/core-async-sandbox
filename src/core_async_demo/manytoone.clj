(ns core-async-demo.manytoone
  (:require [clojure.core.async :as async]))

;;Merge 3 channels into one using merge function
;
;=====c1======>
;               \
;                \
;=====c2======>--->merged-channels--->
;                /
;               /
;=====c3======>

(def c1 (async/chan 10))
(def c2 (async/chan 10))
(def c3 (async/chan 10))

;;merge takes a vector of channels and returns a single channel
(def merge-all (async/merge [c1 c2 c3]))

;;takes values from all 3 channels indirectly
(async/go-loop []
  (let [val (async/<!! merge-all)]
    (if-not (nil? val)
     (do
       (println val)
       (recur)))))

(do
  (async/>!! c1 "Message from channel 1")
  (async/>!! c2 "Message from channel 2")
  (async/>!! c3 "Message from channel 3")
  (doseq [c [c1 c2 c3]]
    (async/close! c)))

;;Mixing channels
;=====channel1======>
;                     \
;                      \
;                       >==ch-out==>
;                      /
;                     /
;=====channel2======>
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

