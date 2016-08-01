(ns jurenal.utils
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r]
            [reagent.session :as session]
            [reagent.cookies :as cookies]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [jurenal.ajax :as ajax :refer [save-mail! get-reacts get-posts get-comments get-post load-interceptors!]]
            [cljs.core.async :refer [put! chan <! >! timeout close!]]
            [ajax.core :refer [GET POST]]
            [jurenal.websockets :as ws]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            [cljs.reader :as reader :refer [read-string]]
            [goog.dom :as dom]
            [goog.dom.classes :as classes])
  (:import goog.History))

(defn page-height []
  (let [body (.-body js/document)
        html (.-documentElement js/document)]
    (.max js/Math
          (.-scrollHeight body)
          (.-offsetHeight body)
          (.-clientHeight html)
          (.-scrollHeight html)
          (.-offsetHeight html))))

(defn page-scroll
  ([]
   (.max js/Math
         (.-pageYOffset js/window)))
  ([to]
   (.scrollTo js/window 0 to)))


(defn fix-scroll [func]
  (let [heigth (page-height)
        scroll (page-scroll)]
    (func)

    (page-scroll (+ scroll (page-height) (- heigth)))))