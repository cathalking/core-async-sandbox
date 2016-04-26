(ns core-async-demo.onetomany
  (:require [clojure.core.async :as a]))

(def from (a/chan 10))

(defn start-count [type channel]
  (a/go-loop [count 0]
    (if (nil? (a/<!! channel))
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
(def partitioned-channels (a/split number? from))

(comment 
  (start-count "Number" (first partitioned-channels))
  (start-count "String" (last partitioned-channels))

  (a/>!! from 100)
  (a/>!! from "Hello World")
  (a/>!! from "Jack Daniels")

  (a/close! from)
  )

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

;;Pub Sub
;
;                                        >harp-subscriber (ch1 + harp-topic-subscription)
;                                      / 
;                                    /
;  beer-publication ---------------->
;  (ch0 + topics)                    \
;                                      \ 
;                                        >carlsberg-subscriber (ch2 + carlsberg-topic-subsription)
;
(def beer-chan (a/chan))
(def harp-channel (a/chan))
(def carlsberg-channel (a/chan))
(def dead-letter-channel (a/chan))
(def wine-chan (a/chan))
(def drink-chan (a/chan))

(def wines #{"merlot" "shiraz" "rioja"})
(def beers #{"harp" "carlsberg" "coors"})

(defn categorise-drink [drink]
  (let [topic (cond 
                (contains? wines drink) "wine"
                (contains? beers drink) "beer"
                :else nil)]
    (println "topic of" drink "is" topic)
    topic))

;(def beer-publisher (a/pub beer-chan :beer))
(def beer-publication (a/pub beer-chan :beer))
;(register-subs)
(def drink-publication (a/pub drink-chan categorise-drink))

(defn register-subs [publication & subscriptions]
  (doseq [[ch topic] subscriptions]
    (println "subscribing to topic" topic "for chan" ch)
    (a/sub publication topic ch))
  "All topic subcriptions registered")

(register-subs drink-publication 
               [beer-chan "beer"] 
               [wine-chan "wine"] 
               [dead-letter-channel nil])

;(a/sub beer-publication "harp" harp-channel)
;(a/sub beer-publication "carlsberg" carlsberg-channel)
;(a/sub beer-publication nil dead-letter-channel)

(comment 

  (a/go-loop []
    (a/<!! harp-channel)
    (println (str "Found Harp"))
    (recur))

  (a/go-loop []
    (a/<!! carlsberg-channel)
    (println (str "Found Carlsberg"))
    (recur))

  (a/>!! beer-chan {:beer :carlsberg})
  (a/>!! beer-chan {:beer :harp})
  (a/>!! beer-chan {:beer :bud}))
