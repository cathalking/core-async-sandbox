(ns core-async-demo.choice
  (:require [clojure.core.async :refer [chan go-loop 
                                        >!! 
                                        alts!!]]))

(def c1 (chan 1))
(def c2 (chan 1))


;(async/>!! c1 "from channel 1")

;;If more than one value is available it will be a choice at random
(defn choice []
  (go-loop []
    (let [[v c] (alts!! [c1 c2])]
      (println "value :" v ", c :" c))
    (recur))
  nil)

;(async/>!! c1 "from channel 2")
