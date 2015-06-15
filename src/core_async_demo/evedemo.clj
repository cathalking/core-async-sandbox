(ns core-async-demo.evedemo
  (:require [clojure.core.async :as async]
            [cheshire.core :as che]
            [clojure.java.io :as io]))

(def file-loc "......")

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
;;JSON --> (Parse JSON) --> Clojure Map
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


(defn process-file []
  (let [from-file-chan (async/chan 1024)
        to-json-chan (async/chan 1024)
        _ (async/pipeline 1 to-json-chan parse-json from-file-chan)
        split-chans (async/split order? to-json-chan)]
    (async/go (with-open [reader (io/reader file-loc)]
       (doseq [line (line-seq reader)]
         (async/>!! from-file-chan line))
       (async/close! to-json-chan)))
    (start-count "Orders" (first split-chans))
    (start-count "History" (last split-chans))
    "Processing, please wait!!!!"))
