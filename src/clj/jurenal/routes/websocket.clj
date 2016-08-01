(ns jurenal.routes.websocket
  (:require [compojure.core :refer [GET defroutes wrap-routes]]
            [clojure.tools.logging :as log]
            [immutant.web.async :as async]))

(defonce channels (atom #{}))

(defn connect! [channel]
  (log/info "channel open")
  (swap! channels conj channel))

(defn disconnect! [channel {:keys [code reason]}]
  (log/info "close code:" code "reason:" reason)
  (swap! channels #(remove #{channel} %)))

(defn broadcast!
  ([msg]
   (doseq [channel @channels]
     (async/send! channel msg)))
  ([channel msg]))
    ;(log/info channel msg)



(def websocket-callbacks
  "WebSocket callback functions"
  {:on-open connect!
   :on-close disconnect!
   :on-message broadcast!})

(defn ws-handler [request]
  (async/as-channel request websocket-callbacks))

(defroutes websocket-routes
           (GET "/ws" [] ws-handler))