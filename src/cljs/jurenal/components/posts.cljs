(ns jurenal.components.posts
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r]
            [reagent.session :as session]
            [markdown.core :refer [md->html]]
            [jurenal.ajax :as ajax :refer [save-mail! get-reacts get-posts get-comments get-post load-interceptors!]]
            [cljs.core.async :refer [put! chan <! >! timeout close!]]
            [ajax.core :refer [GET POST]]
            [jurenal.components.post :refer [post]]
            [jurenal.utils :as utils]
            [goog.dom.classes :as classes])
  (:import goog.History))

(defn comments-list [comments]
  [:div.posts
   (for [p comments]
     ^{:key (:id p)} [post p])])

(defn posts-list [posts]
  [:div.posts
   (for [p posts]
     ^{:key (:id p)} [post p])])

(defn root-post [id]
  (let [p (r/atom nil)]
    (r/create-class
      {:component-did-mount
       (fn []
         (go
           (let [response (<! (get-post id))]
             (reset! p response))))
       :reagent-render
       (fn []
         (when @p
           [post (assoc @p :singleton true)]))})))


(defn update-button []
  (let [value (r/atom "Next")]
    (fn [posts offset limit getting-fn]
      [:div
       [:input.loader {:type "button"
                       :value @value
                       :on-click (fn []
                                   (go
                                     (let [response (<! (getting-fn @offset @limit))]
                                       (swap! posts concat response)
                                       (reset! value "Next")))

                                   (reset! offset (+ @offset @limit))
                                   (reset! value "..."))}]

       [:span.s]
       [:input {:type "button"
                :value "Scroll to top"
                :on-click (fn []
                            (utils/page-scroll 0))}]])))

(defn superposts [getting-fn]
  (let [posts (r/atom nil)
        offset (r/atom 0)
        limit (r/atom 10)]
    (r/create-class
      {:component-did-mount
       (fn []
         (go
           (let [response (<! (getting-fn @offset @limit))]
             (reset! offset (+ @offset @limit))
             (reset! posts response))))
       :reagent-render
       (fn []
         [:div
          (when @posts
            [posts-list @posts])
          [update-button posts offset limit getting-fn]])})))


(defn subposts [id]
  (let [comments (r/atom nil)]
    (r/create-class
      {:component-did-mount
       (fn []
         (go
           (let [response (<! (get-comments id))]
             (reset! comments response))))
       :reagent-render
       (fn []
         (when @comments
           [comments-list @comments]))})))

(defn post-page []
  [:div
   [:div.header
    [:a {:href "#/"} [:div.logo]]]
   [:div.wrap
    [root-post (session/get :pid)]
    [:hr]
    [subposts (session/get :pid)]]])

(defn post-modal []
  (fn [pid]
    (when pid
      (classes/add (.-body js/document) "modal-open")
      [:div.comments
       [:div.overlay
        {:on-click (fn[]
                     (classes/remove (.-body js/document) "modal-open")
                     ;(set! (.-hash (.-location js/window)) "/")
                     ;window.history.go(1); TODO: fix scrolling
                     (.go js/history -1)
                     (session/remove! :pid))}]


       [:div.layer
        [root-post pid]
        [:hr]
        [subposts pid]]])))

(defn posts-wrap []
  [:div
   [superposts (fn [offset limit]
                   (get-posts offset limit))]])
