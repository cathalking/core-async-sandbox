(ns core-async-demo.channels
  (:require [clojure.core.async :refer :all]))

;;use the (chan) function to create an unbuffered channel
(def unbuffered (chan))

;;we use a blocking put and blocking take to via channels in Ordinary Threads
(let [c (chan 10)]
  (>!! c "hello")
  (assert (= "hello" (<!! c)))
  (close! c))

;;because these actions are blocking, if we try to put a value onto an
;;unbuffered channel it will block the main thread
(let [c (chan)]
  (>!! c "Hello")
  (println "I'm blocked");; Unfortunately it blocks the repl
  (println (<!! c))
  (close! c))

;;To get around this we can put this operation in its own thread, therefore freeing
;;up the main thread
(let [c (chan)]
  (thread (>!! c "Hello"))
  (println (str (<!! c) " I'm not blocked"))
  (close! c))

;;We can use a go-block to execute its body asynchronously in a special thread pool
(let [c (chan)]
  (go (>! c "Hello"))
  (println (<!! c))
  (close! c))

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