(ns jurenal.components.user
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r]
            [reagent.session :as session]
            [reagent.cookies :as cookies]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [jurenal.ajax :as ajax :refer [get-posts-by-username load-interceptors!]]
            [cljs.core.async :refer [put! chan <! >! timeout close!]]
            [ajax.core :refer [GET POST]]
            [jurenal.websockets :as ws]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            [cljs.reader :as reader :refer [read-string]]
            [goog.dom :as dom]
            [goog.dom.classes :as classes]
            [jurenal.components.posts :as posts])
  (:import goog.History))

(defn id-to-color [id]
  (let [colors ["#fb9fb1" "#eda987" "#ddb26f" "#acc267"
                "#12cfc0" "#6fc2ef" "#e1a3ee" "#deaf8f"
                "#f92672" "#fd971f" "#f4bf75" "#a6e22e"
                "#a1efe4" "#66d9ef" "#ae81ff" "#cc6633"]
        total (count colors)
        index (mod id total)]
    (colors index)))



(defn post-userpic [user]
  (let [anon (:anon user)
        username (:username user)
        last-name (:last_name user)
        first-name (:first_name user)
        userpic (:userpic user)
        id (:id user)]
    [:div.userpic
     (cond
       anon
       [:img {:src "https://s3.amazonaws.com/jurenal/anon.png"}]

       userpic
       [:img {:src (str "https://s3.amazonaws.com/jurenal/" userpic)}]

       :default
       [:div.default-username
        {:style {:background-color (id-to-color id)}}
        (str
          (first first-name)
          (first last-name))])]))

(defn construct-route [subroute]
  (-> (.-hash js/location)
      (clojure.string/split #"/")
      (drop-last)
      (vec)
      (conj subroute)
      ((partial clojure.string/join "/"))))


(defn user-info [user]
  (let [userpic (:userpic user)
        first_name (:first_name user)
        last_name (:last_name user)]
    [:div.user
     [post-userpic user]
     [:div.user-info
      [:h1.name (str first_name " " last_name)]
      [:div.stats
       [:div "Followers: " (:subscribers user)]
       [:div "Subscribes: " (:subscriptions user)]]
      [:div.menu
       [:a {:href (construct-route "posts")} "Posts"]
       [:a {:href (construct-route "comments")} "Comments"]
       [:a {:href (construct-route "recommendations")} "Recommendations"]
       [:a {:href (construct-route "reacts")} "Reacts"]]]]))


(defn info [username]
  (let [user (r/atom nil)]
    (r/create-class
      {:component-did-mount
       (fn []
         (go
           (let [response (<! (ajax/get-info-by-username username))]
             (reset! user response))))
       :reagent-render
       (fn []
          (when @user
            [user-info @user]))})))


(defn user-page [username]
  [:div
   [:div.header
    [:a {:href "#/"} [:div.logo]]]
   [:div.wrap
    [info username]
    [posts/superposts (fn [offset limit]
                        (get-posts-by-username username offset limit))]]
   [:div.footer]])