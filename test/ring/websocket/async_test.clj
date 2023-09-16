(ns ring.websocket.async-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.async :as a]
            [ring.websocket :as ws]
            [ring.websocket.async :as wsa]))

(deftest test-websocket-listener
  (testing "message sending and receiving"
    (let [client   (a/chan 10)
          socket   (reify
                     ws/Socket
                     (-close [_ code reason]
                       (a/>!! client [:close code reason]))
                     ws/AsyncSocket
                     (-send-async [_ mesg succeed _]
                       (a/>!! client [:send mesg])
                       (succeed)))
          in       (a/chan 10)
          out      (a/chan 10)
          err      (a/chan 10)
          listener (wsa/websocket-listener in out err)]
      (ws/on-open listener socket)
      (ws/on-message listener socket "First")
      (is (= "First" (a/<!! in)))
      (a/>!! out "Second")
      (is (= [:send "Second"] (a/<!! client)))
      (ws/on-message listener socket "Third")
      (is (= "Third" (a/<!! in)))
      (a/>!! out "Fourth")
      (is (= [:send "Fourth"] (a/<!! client)))
      (ws/on-close listener socket 1000 "Normal Closure")
      (is (= [:close 1000 "Normal Closure"] (a/<!! client)))))
  (testing "errors"
    (let [socket   (reify
                     ws/Socket
                     (-close [_ _ _])
                     ws/AsyncSocket
                     (-send-async [_ _ _ fail]
                       (fail (ex-info "send" {}))))
          in       (a/chan 10)
          out      (a/chan 10)
          err      (a/chan 10)
          listener (wsa/websocket-listener in out err)]
      (ws/on-open listener socket)
      (a/>!! out "foo")
      (let [ex (a/<!! err)]
        (is (some? ex))
        (is (instance? clojure.lang.ExceptionInfo ex))
        (is (= "send" (.getMessage ex))))
      (ws/on-error listener socket (ex-info "on-error" {}))
      (let [ex (a/<!! err)]
        (is (some? ex))
        (is (instance? clojure.lang.ExceptionInfo ex))
        (is (= "on-error" (.getMessage ex)))))))
