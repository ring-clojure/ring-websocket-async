(ns ring.websocket.async-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.async :as a]
            [ring.websocket :as ws]
            [ring.websocket.async :as wsa]
            [ring.websocket.protocols :as wsp]))

(deftest test-websocket-listener
  (testing "message sending and receiving"
    (let [client   (a/chan 10)
          socket   (reify
                     wsp/Socket
                     (-close [_ code reason]
                       (a/>!! client [:close code reason]))
                     wsp/AsyncSocket
                     (-send-async [_ mesg succeed _]
                       (a/>!! client [:send mesg])
                       (succeed)))
          in       (a/chan 10)
          out      (a/chan 10)
          err      (a/chan 10)
          listener (wsa/websocket-listener in out err)]
      (wsp/on-open listener socket)
      (wsp/on-message listener socket "First")
      (is (= "First" (a/<!! in)))
      (a/>!! out "Second")
      (is (= [:send "Second"] (a/<!! client)))
      (wsp/on-message listener socket "Third")
      (is (= "Third" (a/<!! in)))
      (a/>!! out "Fourth")
      (is (= [:send "Fourth"] (a/<!! client)))
      (wsp/on-close listener socket 1000 "Normal Closure")
      (is (= [:close 1000 "Normal Closure"] (a/<!! client)))))
  (testing "errors"
    (let [socket   (reify
                     wsp/Socket
                     (-close [_ _ _])
                     wsp/AsyncSocket
                     (-send-async [_ _ _ fail]
                       (fail (ex-info "send" {}))))
          in       (a/chan 10)
          out      (a/chan 10)
          err      (a/chan 10)
          listener (wsa/websocket-listener in out err)]
      (is (satisfies? wsp/Listener listener))
      (wsp/on-open listener socket)
      (a/>!! out "foo")
      (let [ex (a/<!! err)]
        (is (some? ex))
        (is (instance? clojure.lang.ExceptionInfo ex))
        (is (= "send" (.getMessage ex))))
      (wsp/on-error listener socket (ex-info "on-error" {}))
      (let [ex (a/<!! err)]
        (is (some? ex))
        (is (instance? clojure.lang.ExceptionInfo ex))
        (is (= "on-error" (.getMessage ex))))))
  (testing "closing"
    (let [client   (a/chan 10)
          socket   (reify
                     wsp/Socket
                     (-close [_ code reason]
                       (a/>!! client [:close code reason])))
          in       (a/chan 10)
          out      (a/chan 10)
          err      (a/chan 10)
          listener (wsa/websocket-listener in out err)]
      (wsp/on-open listener socket)
      (a/>!! out (wsa/closed 1001 "Going Away"))
      (is (= [:close 1001 "Going Away"] (a/<!! client))))))

(deftest go-websocket-test
  (testing "message sending and receiving"
    (let [client   (a/chan 10)
          server   (a/chan 10)
          socket   (reify
                     wsp/Socket
                     (-close [_ code reason]
                       (a/>!! client [:close code reason]))
                     wsp/AsyncSocket
                     (-send-async [_ mesg succeed _]
                       (a/>!! client [:send mesg])
                       (succeed)))
          response (wsa/go-websocket [in out _]
                     (a/>! server [:receive (a/<! in)])
                     (a/>! out "Second")
                     (a/>! server [:receive (a/<! in)])
                     (a/>! out "Fourth")
                     (a/close! out))
          listener (::ws/listener response)]
      (is (map? response))
      (is (satisfies? wsp/Listener listener))
      (wsp/on-open listener socket)
      (wsp/on-message listener socket "First")
      (wsp/on-message listener socket "Third")
      (is (= [:receive "First"] (a/<!! server)))
      (is (= [:send "Second"] (a/<!! client)))
      (is (= [:receive "Third"] (a/<!! server)))
      (is (= [:send "Fourth"] (a/<!! client)))
      (is (= [:close 1000 "Normal Closure"] (a/<!! client)))))
  (testing "errors"
    (let [server   (a/chan 10)
          socket   (reify
                     wsp/Socket
                     (-close [_ _ _])
                     wsp/AsyncSocket
                     (-send-async [_ _ _ fail]
                       (fail (ex-info "send" {}))))
          response (wsa/go-websocket [_ out err]
                     (a/>! server (a/<! err))
                     (a/>! out "expected failure")
                     (a/>! server (a/<! err))
                     (a/close! out))
          listener (::ws/listener response)]
      (is (satisfies? wsp/Listener listener))
      (wsp/on-open listener socket)
      (wsp/on-error listener socket (ex-info "on-error" {}))
      (let [ex (a/<!! server)]
        (is (some? ex))
        (is (instance? clojure.lang.ExceptionInfo ex))
        (is (= "on-error" (.getMessage ex))))
      (let [ex (a/<!! server)]
        (is (some? ex))
        (is (instance? clojure.lang.ExceptionInfo ex))
        (is (= "send" (.getMessage ex))))))
  (testing "closing"
    (let [client   (a/chan 10)
          socket   (reify
                     wsp/Socket
                     (-close [_ code reason]
                       (a/>!! client [:close code reason])))
          response (wsa/go-websocket [_ out _]
                     (a/>!! out (wsa/closed 1001 "Going Away")))
          listener (::ws/listener response)]
      (wsp/on-open listener socket)
      (is (= [:close 1001 "Going Away"] (a/<!! client)))))
  (testing "omitting err argument"
    (let [client   (a/chan 10)
          server   (a/chan 10)
          socket   (reify
                     wsp/Socket
                     (-close [_ code reason]
                       (a/>!! client [:close code reason]))
                     wsp/AsyncSocket
                     (-send-async [_ mesg succeed _]
                       (a/>!! client [:send mesg])
                       (succeed)))
          response (wsa/go-websocket [in out]
                     (a/>! server [:receive (a/<! in)])
                     (a/>! out "Second")
                     (a/>! server [:receive (a/<! in)])
                     (a/close! out))
          listener (::ws/listener response)]
      (wsp/on-open listener socket)
      (wsp/on-message listener socket "First")
      (is (= [:receive "First"] (a/<!! server)))
      (is (= [:send "Second"] (a/<!! client)))
      (wsp/on-error listener socket (ex-info "Error" {}))
      (is (= [:close 1011 "Unexpected Error"] (a/<!! client)))
      (wsp/on-close listener socket 1011 "Unexpected Error")
      (is (= [:receive nil] (a/<!! server))))))
