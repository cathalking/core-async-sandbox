(ns core-async-demo.channels
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan go thread go-loop
                                        >! >!! <! <!! put! take! 
                                        close! timeout
                                        alts! alts!!
                                        sliding-buffer dropping-buffer]]
            [clojure.core.async.impl.concurrent :as conc]
            [clojure.core.async.impl.exec.threadpool :as tp]
            ))

(comment 
  (defonce my-executor
    (java.util.concurrent.Executors/newFixedThreadPool
    1
    (conc/counted-thread-factory "my-async-dispatch-%d" true)))

  (alter-var-root #'clojure.core.async.impl.dispatch/executor
    (constantly (delay (tp/thread-pool-executor my-executor))))
)

;(async/go
;  (println 
;    (Thread/currentThread)))

;;use the (chan) function to create an unbuffered channel
(def unbuffered (chan))

;;we use a blocking put and blocking take to via channels in Ordinary Threads
(defn example-1 []
(let [c (chan 10)]
  (>!! c "hello")
  (assert (= "hello" (<!! c)))
  (close! c))
)

(comment 
;;because these actions are blocking, if we try to put a value onto an
;;unbuffered channel it will block the main thread
(let [c (chan)]
  (>!! c "Hello")
  (println "I'm blocked");; Unfortunately it blocks the repl
  (println (<!! c))
  (close! c))
)

;;To get around this we can put this operation in its own thread, therefore freeing
;;up the main thread
(defn example-3 []
  (let [c (chan)]
    (log/infof "Example-3 - Coordinating via channels")
    (thread 
      (do 
        (log/infof "Other thread, hard at work... ")
        (Thread/sleep 5000)
        (log/infof "I'm done. Add result to channel, like so (>!! c \"Hello\")")
        (>!! c "Hello")
        ))
    (Thread/sleep 500)
    (log/infof "I can carry on with other tasks whilst worker thread does its thing")
    (log/infof "Nothing left to do on main thread, take value from channel c, or block until something appears")
    (log/infof "Something ('%s') appeared on chan c" (<!! c))
    (close! c))
)

(defn example-3a []
  (let [] ;[c (chan)]
    (log/infof "Example-3 - Simple thread coordination via channels (2 Threads)")
    (let [c (thread 
              (do 
                (log/infof "Other thread, hard at work... ")
                (Thread/sleep 5000)
                (log/infof "I'm done. The value I return will be added to the implicit channel")
                "Hello"
                ;(>!! c "Hello")
                ))]
      (Thread/sleep 500)
      (log/infof "I can carry on with other tasks whilst worker thread does its thing")
      (log/infof "Nothing left to do on main thread, take value from channel c, or block until something appears")
      (log/infof "Something ('%s') appeared on chan c" (<!! c))
      (close! c)))
)

(def cntr (atom 0))

(defn work [& {:keys [duration desc ret] :or {desc "some work"
                                              duration 1
                                              ret nil}}]
  (let [msecs (* duration 1000)
        task-num (swap! cntr inc)]
    (log/infof "task-%d [ Start] %s [%d %s]" task-num desc duration (if (> duration 1) "secs" "sec"))
    (Thread/sleep msecs)
    (log/infof "task-%d [Finish]" task-num)
    ret))

(defn example-3b []
  (let [c (chan)]
    (log/infof "Example-3b - Simple thread coordination via channels (2 Threads 'back-and-forth')")
    (thread 
      (do 
        (>!! c (work :duration 3 :desc "Produce value1 and put on chan" :ret "Hello"))
        ;(work :duration 1 :desc "Do other work")
        (>!! c (work :duration 3 :desc "Produce value2 and put on chan" :ret "World"))
        ))
    (let [v1 (do
               (work :duration 1 :desc "Do misc tasks")
               (log/infof "Take value1 from chan")
               (<!! c))
          v2 (do
               (work :duration 1 :desc "Do more misc tasks")
               (log/infof "Take value2 from chan")
               (<!! c))]
      ;(work :duration 1 :desc "More work in main thread")
      (log/infof "Use what we got from channel: %s %s" v1 v2))
      (close! c)
  ))

(defn example-3b-go []
  ; main thread
  (let [work-chan (chan)
        result-chan (chan)]
    (log/infof "Example-3b - Simple thread coordination via channels (2 Go-blocks 'back-and-forth')")
    ; thread 2
    (go 
      (do 
        (>! work-chan (work :duration 3 :desc "Produce value1 and put on chan" :ret "Hello"))
        ;(work :duration 1 :desc "Do other work")
        (>! work-chan (work :duration 3 :desc "Produce value2 and put on chan" :ret "World"))
        ))
    ; thread 3
    (go 
      (let [v1 (do
                 (work :duration 1 :desc "Do misc tasks")
                 (log/infof "Take value1 from chan")
                 (<! work-chan))
            v2 (do
                 (work :duration 1 :desc "Do more misc tasks")
                 (log/infof "Take value2 from chan")
                 (<! work-chan))]
        ;(work :duration 1 :desc "More work in main thread")
        (>! result-chan (str v1 " " v2 "!!!"))
        (close! work-chan)
        ))
    (log/infof "Results from channel: %s" (<!! result-chan))
    (close! result-chan)
    nil
  ))

(defn example-3b-go-take []
  ; main thread
  (let [work-chan (chan)
        result-chan (clojure.core.async/take 1 (chan))]
    (log/infof "Example-3b - Simple thread coordination via channels (2 Go-blocks 'back-and-forth')")
    ; thread 2
    (go 
      (do 
        (>! work-chan (work :duration 3 :desc "Produce value1 and put on chan" :ret "Hello"))
        ;(work :duration 1 :desc "Do other work")
        (>! work-chan (work :duration 3 :desc "Produce value2 and put on chan" :ret "World"))
        ))
    ; thread 3
    (go 
      (let [v1 (do
                 (work :duration 1 :desc "Do misc tasks")
                 (log/infof "Take value1 from chan")
                 (<! work-chan))
            v2 (do
                 (work :duration 1 :desc "Do more misc tasks")
                 (log/infof "Take value2 from chan")
                 (<! work-chan))]
        ;(work :duration 1 :desc "More work in main thread")
        (>! result-chan (str v1 " " v2 "!!!"))
        (close! work-chan)
        ))
    (log/infof "Results from channel: %s" (<!! result-chan))
    ;(close! result-chan)
    nil
  ))

(defn example-3b-go-timeout []
  ; main thread
  (let [work-chan (chan)
        result-chan (chan)
        timeout-chan (timeout 4500)]
    (log/infof "Example-3b - Coordination via channels + timeout (2 Go-blocks 'back-and-forth') ")
    ; thread 2
    (go 
      (do 
        (>! work-chan (work :duration 3 :desc "Produce value1 and put on chan" :ret "Hello"))
        ;(work :duration 1 :desc "Do other work")
        (>! work-chan (work :duration 3 :desc "Produce value2 and put on chan" :ret "World"))
        ))
    ; thread 3
    (go 
      (let [v1 (do
                 (work :duration 1 :desc "Do misc tasks")
                 (log/infof "Take value1 from chan")
                 (<! work-chan))
            v2 (do
                 (work :duration 1 :desc "Do more misc tasks")
                 (log/infof "Take value2 from chan")
                 (<! work-chan))]
        ;(work :duration 1 :desc "More work in main thread")
        (>! result-chan (str v1 " " v2 "!!!"))
        (close! work-chan)
        ))
    (let [[result _] (alts!! [result-chan timeout-chan])]
      (log/infof "Results from channel: %s" result)
      (close! result-chan))
    nil
  ))

(defn example-3b-go2 []
  (let [c (chan)]
    (log/infof "Example-3b-go - Simple thread coordination via channels (2 Threads 'back-and-forth')")
    (go
      (do 
        (>! c (work :duration 1 :desc "Produce value and put on chan" :ret "Hello"))
        (work :duration 1 :desc "More work to do")
        (>! c (work :duration 1 :desc "Produce another value and put on chan" :ret "World"))
        ))
    (let [v1 (do
               (work :duration 1 :desc "Misc tasks then take value from chan")
               (<!! c))
          v2 (do
               (work :duration 1 :desc "More misc tasks then take value from chan")
               (<!! c))]
      (work :duration 1 :desc "More work in main thread")
      (log/infof "Eventually got from channel: %s %s" v1 v2))
      (close! c)
  ))

;;We can use a go-block to execute its body asynchronously in a special thread pool
(defn example-4 []
(let [c (chan)]
  (log/infof "example-4")
  (go 
    (do
      (log/infof "(>! c \"Hello\")")
      (>! c "Hello")
      ))
  (println (str (<!! c) " I'm not blocked"))
  (close! c))
)

(defn go-thread-pool-demo [n]
  (let [c (chan 10)]
    (loop [acc 0]
      (if (>= acc n)
        (log/infof "All finished")
        (do
          (go
            (log/infof "go block %d" (inc acc))
            (>! c (inc acc))
            )
          (recur (inc acc))
        )))
    (Thread/sleep 10000)
    (log/infof "Will block whilst chan c is empty")
    (doseq [n (range 0 n)]
      (log/infof "Taken from chan c: %d" (<!! c)))
    ))

(defn regular-thread-pool-demo [n]
  (let [c (chan 10)]
    (loop [acc 0]
      (if (>= acc n)
        (log/infof "All finished")
        (do
          (thread
            (log/infof "go block %d" (inc acc))
            (>!! c (inc acc))
            )
          (recur (inc acc))
        )))
    (Thread/sleep 10000)
    (log/infof "Will block whilst chan c is empty")
    (doseq [n (range 0 n)]
      (log/infof "Taken from chan c: %d" (<!! c)))
    ))

(comment 
;;A go block returns a channel which we can use to perform a blocking take
(let [c (chan)]
  (go (>! c "Hello"))
  (println (<!! (go (<! c))))
  (close! c))

;;dropping-buffer silently drops new values
(let [c (chan (dropping-buffer 1))]
  (>!! c 1)
  (>!! c 2)
  (println (<!! c))
  (println (<!! c))
  (close! c))

;;sliding-buffer drops the current head value
(let [c (chan (sliding-buffer 1))]
  (>!! c 1)
  (>!! c 2)
  (println (<!! c))
  (close! c))
)
