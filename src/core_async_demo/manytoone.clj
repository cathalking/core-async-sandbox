(ns core-async-demo.manytoone
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [clojure.core.async :refer [chan go thread go-loop
                                        >! >!! <! <!! put! take! 
                                        close! timeout
                                        alts! alts!!
                                        mult tap
                                        pub sub
                                        sliding-buffer dropping-buffer]]
            [clojure.core.async.impl.concurrent :as conc]
            [clojure.core.async.impl.exec.threadpool :as tp]
            ))

;(comment 
(comment
  (defonce my-executor
  (java.util.concurrent.Executors/newFixedThreadPool
  1
  (conc/counted-thread-factory "my-async-dispatch-%d" true)))
  )

(defn new-executor [n]
  (java.util.concurrent.Executors/newFixedThreadPool n
    (conc/counted-thread-factory "coreasync-tp-%d" true)))

(defn set-executor-threads [n]
  (alter-var-root #'clojure.core.async.impl.dispatch/executor
    (constantly (delay (tp/thread-pool-executor (new-executor n))))))
;)
;;Merge 3 channels into one using merge function
;
;=====c1======>
;               \
;                \
;=====c2======>--->merged-channels--->
;                /
;               /
;=====c3======>

(comment 
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
)

;;Mixing channels 
;;like merge but with added granular control over each channel and the mix make-up
;; e.g. (admix mixer ch), (unmix mixer ch), (toggle mixer {:mute bool, :pause bool, :solo bool})
;=====ch1======>
;                     \
;                      \
;                       > (mixer + ch-mix) --- ch-mix ==> controllable mix of ch1 + ch2 messages
;                      /
;                     /
;=====ch2======>
(def ch-out (async/chan 10))
(def mixer-for-ch-out (async/mix ch-out))

(def channel1 (async/chan 10))
(def channel2 (async/chan 10))

;;lets add our two channels to the mix
(defn create-channel-mix []
  (async/admix mixer-for-ch-out channel1)
  (async/admix mixer-for-ch-out channel2))

(defn put-all [ch col] 
  (doseq [v col] (put! ch v (fn [_] (log/infof "in put! completion callback")))))

(defn put-all! [ch col] 
  (doseq [v col] (>!! ch v)))

(defn consumer-main-loop [ch-out & {:keys [consumer-name delay-secs] 
                                    :or   {consumer-name "consumer"
                                           delay-secs 0}}]
  (async/go-loop []
    (let [v (do (async/timeout (* 1000 delay-secs)) ; mimick some work
                (async/<!! ch-out))]
      (if (nil? v)
        (println consumer-name "exiting. ch-out has returned nil so must be closed")
        (do
          (log/infof "%s [consumed by [%s]]" v consumer-name)
          (recur)))
        ;else
    )))

(defn gen-messages [n mess] 
  (repeatedly n (fn [] mess)))

(def job-cntr (atom 0))
(def ch1-test (atom (chan)))

(defn push-messages [n]
  (do 
    (reset! job-cntr 0)
    (reset! ch1-test (chan)) 
    (put-all! @ch1-test (range 1 n)) 
    (close! @ch1-test) 
    (reset! job-cntr 0)))

; (repeatedly 50 (fn [] (consumer-main-loop2 ch1-test)))
; (do (def ch1-test (a/chan)) (put-all! ch1-test (range 1 100000)) (a/close! ch1-test) (reset! job-cntr 0))

(defn go-consumer-loop [ch-out & {:keys [consumer-name delay-secs] 
                                    :or   {consumer-name (str "consumer" (swap! job-cntr inc))
                                           delay-secs 0}}]
  (go-loop [cntr 1]
    (let [v (do (async/timeout (* 1000 delay-secs)) ; mimick some work
                (async/<! ch-out))]
      (if (nil? v)
        (log/infof "exiting %s : consumed %d messages" consumer-name cntr)
        (recur (inc cntr)))
    ))
  (log/infof "Started %s" consumer-name))

(defn thread-consumer-loop [ch-out & {:keys [consumer-name delay-secs] 
                                    :or   {consumer-name (str "consumer" (swap! job-cntr inc))
                                           delay-secs 0}}]
  (log/infof "Starting %s" consumer-name)
  (thread 
    (loop [cntr 1]
      (let [v (do (async/timeout (* 1000 delay-secs)) ; mimick some work
                  (async/<!! ch-out))]
        (if (nil? v)
          (log/infof "exiting %s : consumed %d messages" consumer-name cntr)
          (recur (inc cntr)))
      ))
  ))

(defn run-go-consumers [n]
  (repeatedly n (fn [] (go-consumer-loop @ch1-test))))

(defn run-thread-consumers [n]
  (repeatedly n (fn [] (thread-consumer-loop @ch1-test))))

(def exit-flag (atom false))

(defn dumb-go-loop [& {:keys [start label] :or {start 0
                                                label (str "job" (swap! job-cntr inc))}}]
  (go-loop [cntr start] 
    (if (= 0 (mod cntr 1000000)) 
      (log/infof "%s, count=%d" label cntr))
    (if @exit-flag
      (log/infof "Exiting %s, total=%d" label cntr)
      (recur (inc cntr)))))

(defn producer1-loop []
  (async/go-loop []
    (async/<!! (async/timeout 1000))
    (async/>!! channel1 "I'm from channel1")
    (recur)))

(defn producer-loop [c message]
  (async/go-loop []
    (async/<!! (async/timeout 1000))
    (async/>!! c message)
    (recur)))

(def ch1 (chan))
(def ch1-mult (mult ch1))
(def ch1-copyA (tap ch1-mult (chan)))
(def ch1-copyB (tap ch1-mult (chan)))

; (do 
;   (consumer-main-loop ch1-copyA :consumer-name "fast-consumer") 
;   (consumer-main-loop ch1-copyB :consumer-name "slow-consumer" :delay-secs 3))

; items will removed from ch1 (or just the ch1-mult?) as fast as the slowest consumer can take them

(def pub-chan (chan))
(def publication (pub pub-chan :topic))

(defn subscribe [publctn topic sub-ch]
  (do 
    (sub publctn topic sub-ch)
    sub-ch))

(def subscriber-chan1 
  (subscribe publication :topic-a (chan 2)))

(def subscriber-chan2 
  (subscribe publication :topic-a (chan 2)))

(comment
  ;;lets mute channel1 (new values get ignored)
  (async/toggle mixer-for-ch-out {channel1 {:mute true}})

  ;;lets unmute channel1
  (async/toggle mixer-for-ch-out {channel1 {:mute false}})

  ;lets only process values on channel2
  (async/toggle mixer-for-ch-out {channel2 {:solo true}})

  ;lets pause channel2 (new values will buffer, according to channel buffering config)
  (async/toggle mixer-for-ch-out {channel2 {:pause true}})

  ;lets remove channel2 from the mix entirely
  (async/unmix mixer-for-ch-out channel2)

  ;close output channel
  (async/close! ch-out)
)

(defn insert-newline [s]
  (clojure.string/replace s #"\}\{" "}\r\n{"))

(def chunks ["{\"foo\"", 
             ":1}\n{\"bar\":", 
             "42}", 
             "{\"baz\":42}", 
             "{\"bla\":42}"])

(defn streaming-buffer []
  (fn [step]
    (let [buff (atom "")]
      (fn
        ([r] (println "atom=" @buff) (step r))
        ([r x]
          (println (str "atom=" @buff ", x=" x))
          (let [json-lines (-> (str @buff x) (insert-newline) (clojure.string/split-lines))
                to-process (butlast json-lines)]
            (println (str "json-lines=" json-lines))
            (reset! buff (last json-lines))
            (println (str "to-process=" to-process))
            (if to-process 
              (reduce step r to-process)
              r)))
        ))
    ))

; (consumer-main-loop2 ch1-test :consumer-name "cons1")
; (consumer-main-loop2 ch1-test :consumer-name "cons2")
; (put-all! ch1-test (gen-messages 3 "hi"))
