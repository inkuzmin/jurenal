(ns jurenal.components.post
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
            [goog.string :as gstring]
            [goog.string.format]
            ;[taoensso.tower :as tower :refer-macros (with-tscope)]
            [goog.dom.classes :as classes])
  (:import goog.History))

(defn format-time [dt]
  (str
    (t/day (tc/from-date dt))
    " "
    (["января" "февраля" "марта"
      "апреля" "мая " "июня"
      "июля" "августа" "сентября"
      "октября" "ноября" "декабря"] (dec (t/month (tc/from-date dt))))
    " в "
    (gstring/format "%02d" (t/hour (tc/from-date dt)))
    ":"
    (gstring/format "%02d" (t/minute (tc/from-date dt)))))

(defn lapsed-time [t]
  (/ (- (tc/to-local-date-time (t/now)) t) 1000))

(defn today? [t]
  (< (tc/to-local-date-time (t/today-at-midnight)) t))



(defn reacts [emoji]
  (r/create-class
    {:component-did-mount
     (fn[])

     :component-will-unmount
     (fn [])

     :reagent-render
     (fn []
       (when (seq emoji)
         [:div "reactions: "
          (for [e emoji]
            (when-let [u (clojure.string/lower-case (:unified e))]
              ^{:key (:id e)}
              [:img.emoji
               {:alt (:unicode e)
                :src (str "/img/emoji/" u ".png")}]))]))}))

;(clojure.string/lower-case [])

(defn -s-ago [t]
  (int (/ (- (tc/to-local-date-time (t/now)) t) 1000)))

(defn -m-ago [t]
  (int (/ (- (tc/to-local-date-time (t/now)) t) (* 60 1000))))

(defn -h-ago [t]
  (int (/ (- (tc/to-local-date-time (t/now)) t) (* 60 60 1000))))

(defn ending [n]
  (cond
    (< 10 n 20)
    [nil "ов"]

    (= (mod n 10) 1)
    ["у" nil]

    (<= 2 (mod n 10) 4)
    ["ы" "а"]

    :default
    [nil "ов"]))

(defn fs [n]
  (str n " секунд" (first (ending n)) " назад"))

(defn fm [n]
  (str n " минут" (first (ending n)) " назад"))

(defn fh [n]
  (str n " час" (second (ending n)) " назад"))

(defn post-date [date-time]
  (let [dt (tc/to-local-date-time date-time)
        update (r/atom 0)]
    (r/create-class
      {:component-did-mount
       (fn[]
          (js/setInterval #(swap! update inc) (* 10 1000)))

       :component-will-unmount
       (fn [])
         ;(js/clearInterval interval))

       :reagent-render
       (fn []
         [:div.post-date {:data-update @update}
          (cond
            (< (lapsed-time dt) 60)
            (fs (-s-ago dt)) ;; set interval 10s ;; n sec ago

            (< 60 (lapsed-time dt) (* 60 60))
            (fm (-m-ago dt));; set interval 1 min

            :else
            (cond ;; clear interval ;; n min ago
              (today? dt)
              (fh (-h-ago dt)) ;; n h ago

              :else
              (format-time dt)))])}))) ;; at ...

(defn post-author [user]
  (if (:anon user)
    [:span "Anonymous"]
    [:a.post-author
     {:href
      (if (:username user)
        (str "#/@" (:username user))
        (str "#/user/" (:id user)))}
     (str (:first_name user) " " (:last_name user))]))




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




(defn post [p]
  (let [first_name (:first_name (:user p))
        last_name (:last_name (:user p))
        username (:username (:user p))
        anon (:anon (:user p))
        userpic (:userpic (:user p))
        uid (:id (:user p))
        pid (:id p)
        date (:date p)
        text (:text p)
        comments (:comments p)
        recommendations (:recommendations p)]

    (r/create-class
      {:component-did-mount
       (fn[])

       :component-will-unmount
       (fn [])

       :reagent-render
       (fn []
         [:div.post
          [:div.post-body
           (when (:attach p)
             [:img.attach {:src (str "https://s3.amazonaws.com/jurenal/"
                                     (:file_id (:attach p))
                                     (if (:singleton p) "/full.jpg" "/preview.jpg"))}])


           (when (:text p)
             [:div {:dangerouslySetInnerHTML {:__html text}}])]

          [:div.post-footer
           [post-userpic (:user p)]
           [:span.s]
           [post-author (:user p)]
           [:span.s]
           [:a {:href (str "#/post/" pid)}

            [post-date date]]
           (when (pos? comments)
             ;[:span.s]
             [:div (str "comments: " comments)])
           (when (seq recommendations)
             ;[:span.s]
             [:div (str "reacts: " (count recommendations))])
           [reacts (:reacts p)]]])})))