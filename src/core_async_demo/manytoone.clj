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
  (println (async/<!! merge-all))
  (recur))

(do
  (async/>!! c1 1)
  (async/>!! c2 "hello")
  (async/>!! c3 3))

