(ns core-async-demo.choice
  (:require [clojure.core.async :as async]))

(def c1 (async/chan 1))
(def c2 (async/chan 1))


(async/>!! c1 "from channel 1")

;;If more than one value is available it will be a choice at random
(async/go-loop []
  (let [[val chan] (async/alts!! [c1 c2])]
    (println val chan))
  (recur))

(async/>!! c1 "from channel 2")