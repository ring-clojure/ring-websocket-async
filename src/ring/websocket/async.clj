(ns ring.websocket.async
  (:require [clojure.core.async :as a]
            [ring.websocket :as ws]))

(defn websocket-listener
  "Takes three core.async channels for input, output, and error reporting
  respectively, and returns a Ring websocket listener.

  Data sent to the 'in' channel will be sent to the client via the websocket.
  The 'out' channel will receive data sent from the client. The 'err' channel
  will deliver any Throwable exceptions that occur.

  Closing the out channel will close the socket and the two other channels."
  [in out err]
  (reify ws/Listener
    (on-open [_ sock]
      (letfn [(fail [ex]
                (a/put! err ex))
              (out-loop []
                (a/take! out (fn [mesg]
                               (if (some? mesg)
                                 (ws/send sock mesg out-loop fail)
                                 (ws/close sock)))))]
        (out-loop)))
    (on-message [_ _ mesg]
      (a/put! in mesg))
    (on-pong [_ _ _])
    (on-error [_ _ ex]
      (a/put! err ex))
    (on-close [_ _ _ _]
      (a/close! in)
      (a/close! out)
      (a/close! err))))

(defmacro go-websocket
  "Macro for returning a websocket response handled by an inner go block.
  Expects three binding symbols - in, out and err - and assigns them to
  channels (see: websocket-listener). The body of the macro is executed in a
  core.async go block. At the end of the body, the socket is closed.

  Example:
  (go-websocket [in out err]
    (loop []
      (when-let [msg (<! in)]
        (>! out msg)
        (recur))))"
  [[in out err] & body]
  {:pre [(symbol? in) (symbol? out) (symbol? err)]}
  `(let [~in  (a/chan)
         ~out (a/chan)
         ~err (a/chan)]
     (a/go (try ~@body (finally (a/close! ~out))))
     {::ws/listener (websocket-listener ~in ~out ~err)}))
