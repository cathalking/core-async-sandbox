(ns core-async-demo.pipeline
  (:require [clojure.core.async :as async]
            [cheshire.core :as che]
            [clojure.java.io :as io]))

(def parse-json (map (fn [json] (che/parse-string json true))))

(defn order? [parsed-json]
  (= (:resultType parsed-json) "orders"))

;;count result type
(defn start-count [type channel]
  (async/go-loop [count 0]
    (if (nil? (async/<!! channel))
      (println (str  type " Count: " count "\n"))
      (recur (inc count)))))

(def from-file-channel (async/chan 1024))
(def parsed-data-channel (async/chan 1024))

;;PIPELINE
;;JSON --> (Parse JSON) -->
(async/pipeline 1 parsed-data-channel parse-json from-file-channel)

;;SPLIT Market data by resultType

;                         > orders-chan --> (Count Orders)
;                        /
;; parsed-data-channel--/
;                       \
;                        \
;                         > history-chan --> (Count History)
(def partitioned-channels (async/split order? parsed-data-channel))

;;Start processes to count result types
(start-count "Orders" (first partitioned-channels))
(start-count "History" (last partitioned-channels))

;;read from file and put json on from-file-channel
(do (async/go
    (with-open [reader (io/reader "PUT FILE LOCATION WITH EVE-DATA")]
     (doseq [line (line-seq reader)]
       (async/>!! from-file-channel line))
     (async/close! parsed-data-channel)))
    (println "Reading File..."))




;(defn process-file []
;  (let [from-file-chan (async/chan 1024)
;        to-json-chan (async/chan 1024)
;        pipeline (async/pipeline 1 to-json-chan parse-json from-file-chan)
;        split-chans (async/split order? to-json-chan)]
;    (async/go (with-open [reader (io/reader "/Users/e20042/eve-data/data.txt")]
;       (doseq [line (line-seq reader)]
;         (async/>!! from-file-chan line))
;       (async/close! to-json-chan)))
;    (start-count "Orders" (first split-chans))
;    (start-count "History" (last split-chans))
;    "Processing, please wait!!!!"))