(ns core-async-demo.pipeline
  (:require [clojure.core.async :as async]))

(def from (async/chan 10))
(def to (async/chan 10))

;;lets create a pipe
(async/pipe from to)

;;put values into pipe
(async/>!! from 0)

;;read value from pipe
(async/<!! to)

(def from (async/chan 10))
(def to (async/chan 10))

;; lets do something interesting and transform all values
;; inside the pipeline into Strings
;
;=======from=======>(map str)=====to=======>
;
(async/pipeline 1 to (map str) from)

;;put value into pipeline
(async/>!! from 10)

;;take value
(async/<!! to)