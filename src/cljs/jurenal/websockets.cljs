(ns jurenal.websockets
  (:require [cognitect.transit :as t]
            [cljs.reader :as reader :refer [read-string]]))

(defonce ws-chan (atom nil))
(def json-reader (t/reader :json))
(def json-writer (t/writer :json))


(defn receive-event! [update-fn]
  (fn [event]
    (let [data (read-string (.-data event))]
      (update-fn data))))

(defn send-transit-msg!
  [msg]
  (if @ws-chan
    (.send @ws-chan (t/write json-writer msg))
    (throw (js/Error. "Websocket is not available!"))))

(defn send-ping! []
  (if @ws-chan
    (.send @ws-chan (str {
                          :type :ping}))))



(defn make-websocket! [url receive-handler]
  (println "attempting to connect websocket")
  (if-let [chan (js/WebSocket. url)]
    (do
      (set! (.-onmessage chan) (receive-event! receive-handler))
      (reset! ws-chan chan)
      (println "Websocket connection established with: " url))
    (throw (js/Error. "Websocket connection failed!"))))