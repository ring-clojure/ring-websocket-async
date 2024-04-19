# Ring-Websocket-Async [![Build Status](https://github.com/ring-clojure/ring-websocket-async/actions/workflows/test.yml/badge.svg?branch=master)](https://github.com/ring-clojure/ring-websocket-async/actions/workflows/test.yml)

A Clojure library for using [core.async][] with [Ring's][] websocket API
(currently in beta testing).

[core.async]: https://github.com/clojure/core.async
[ring's]: https://github.com/ring-clojure/ring

## Installation

Add the following dependency to your deps.edn file:

    org.ring-clojure/ring-websocket-async {:mvn/version "0.1.0-beta2"}

Or to your Leiningen project file:

    [org.ring-clojure/ring-websocket-async "0.1.0-beta2"]

## Usage

The most convenient way to get started is to use the `go-websocket`
macro. Here's an example that echos any message received back to the
client:

```clojure
(require '[clojure.core.async :as a :refer [<! >!]]
         '[ring.websocket.async :as wsa])

(defn echo-websocket-handler [request]
  (wsa/go-websocket [in out err]
    (loop []
      (when-let [mesg (<! in)]
        (>! out mesg)
        (recur)))))
```

The macro sets three binding variables:

* `in`  - the input channel
* `out` - the output channel
* `err` - the error channel

Then executes its body in a core.async `go` block. When the block
completes, the websocket is closed by the server. If the client closes
the websocket, the associated channels are also closed.

A Ring websocket response will be returned by the macro, so you can
directly return it from the handler.

The error channel may be omitted. In this case, any errors with the
websocket protocol will close the connection with a 1011 unexpected
server error message.

To close the connection from the server with a specific error code of
your choice, you can use the `closed` function to send a special message
to the output channel:

```clojure
(defn closes-with-error [request]
  (wsa/go-websocket [in out err]
    (>! out (wsa/closed 1001 "Gone Away"))))
```

If you want more control, you can use the lower-level
`websocket-listener` function. The following handler example is
equivalent to the one using the `go-websocket` macro:

```clojure
(defn echo-websocket-handler [request]
  (let [in  (a/chan)
        out (a/chan)
        err (a/chan)]
    (go (try
          (loop []
            (when-let [mesg (<! in)]
              (>! out mesg)
              (recur)))
          (finally
            (a/close! out))))  ;; closes the websocket
    {:ring.websocket/listener (websocket-listener in out err)}))
```

## License

Copyright © 2023 James Reeves

Released under the MIT license.
