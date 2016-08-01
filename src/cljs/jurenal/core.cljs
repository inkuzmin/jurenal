(ns jurenal.core
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
            [bjurenal.websockets :as ws]
            [jurenal.components.posts :as posts]
            [jurenal.components.user :as user]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            [cljs.reader :as reader :refer [read-string]]
            [goog.dom :as dom]
            [goog.dom.classes :as classes])
  (:import goog.History))

(enable-console-print!)



(defn user-page []
  (let [username (session/get :username)
        cmd (session/get :command)]
    (.log js/console cmd)
    [:div
     [:div.header
      [:a {:href "#/"} [:div.logo]]]
     [:div.wrap
      [user/info username]

      (cond
        (= cmd :posts)
        [posts/superposts (fn [offset limit]
                            (ajax/get-posts-by-username username offset limit))]

        (= cmd :comments)
        [:div "comments"]

        (= cmd :reacts)
        [:div "reacts"]

        (= cmd :recommendations)
        [:div "recommendations"])

      [posts/post-modal (session/get :pid)]]
     [:div.footer]]))



(defn post-page []
  [:div
   [:div.header
    [:a {:href "#/"} [:div.logo]]]
   [:div.wrap
    [posts/root-post (session/get :pid)]
    [:hr]
    [posts/subposts (session/get :pid)]]])

(defn main-page []
  [:div
   [:div.header
    [:a {:href "#/"} [:div.logo]]]
   [:div.wrap
    [posts/posts-wrap]
    [posts/post-modal (session/get :pid)]]

   [:div.footer]])

(def pages
  {:main #'main-page
   :user #'user-page
   :post #'post-page})

(defn page []
  [(pages (session/get :page))])


(defn update! [event])


;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")


(defn subroute-to-cmd [s]
  (keyword (subs s 1)))

(defn fix-subroute [subroute default]
  (let [def-cmd (subroute-to-cmd default)]
    (cond
      (or (= subroute "/") (empty? subroute))
      def-cmd

      :else
      (subroute-to-cmd subroute))))




(secretary/defroute "/" []
  (session/remove! :pid)
  (session/put! :page :main))
(secretary/defroute "/post/:id" [id]
  (if (not (session/get :page))
    (session/put! :page :post))
  (session/remove! :pid)
  (session/put! :pid id))

;;--
(secretary/defroute #"/(user/|@)(\w+)(/\w+|$|/)" [prefix username subroute]
  (cond
    (= subroute "")
    (set! (.-hash js/location) (str (.-hash js/location) "/posts"))

    (= subroute "/")
    (set! (.-hash js/location) (str (.-hash js/location) "posts")))


  (let [cmd (fix-subroute subroute "/posts")]
    (session/remove! :pid)
    (session/put! :username username)
    (session/put! :page :user)
    (session/put! :command cmd)))

;(secretary/defroute "/about" []
;  (session/put! :page :about))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
      HistoryEventType/NAVIGATE
      (fn [event]
        (secretary/dispatch! (.-token event))))
    (.setEnabled true)))


;; -------------------------
;; Initialize app
;(defn fetch-docs! []
;  (GET (str js/context "/docs") {:handler #(session/put! :docs %)}))

(defn mount-components []
  (ws/make-websocket! (str
                        (clojure.string/replace (.-protocol js/location) "http" "ws")
                        "//" (.-host js/location) "/ws") update!)
  (r/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (load-interceptors!)
  (hook-browser-navigation!)
  (mount-components))

(.setInterval js/window
              (fn []
                (ws/send-ping!)) 30000)
