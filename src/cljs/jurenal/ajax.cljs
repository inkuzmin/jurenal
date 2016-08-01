(ns jurenal.ajax
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [ajax.core :as ajax :refer [GET POST]]
            [cljs.core.async :refer [put! chan <! >! timeout close!]]))


(defn handler [response]
  (.log js/console (str response)))

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))

(defn default-headers [request]
  (-> request
      (update :uri #(str js/context %))
      (update
        :headers
        #(merge
          %
          {"Accept" "application/transit+json"
           "x-csrf-token" js/csrfToken}))))

(defn load-interceptors! []
  (swap! ajax/default-interceptors
         conj
         (ajax/to-interceptor {:name "default headers"
                               :request default-headers})))


(defn get-posts [offset limit]
  (let [ch (chan 1)]
    (GET "/api/posts"
         {:params {
                   :offset offset
                   :limit limit}

          :handler (fn [response]
                     (go (>! ch response)
                         (close! ch)))
          :error-handler #()})
    ch))

(defn get-posts-by-username [username offset limit]
  (let [ch (chan 1)]
    (GET (str "/api/user/" username "/posts")
         {:params {
                   :offset offset
                   :limit limit}

          :handler (fn [response]
                     (go (>! ch response)
                         (close! ch)))
          :error-handler #()})
    ch))

(defn get-info-by-username [username]
  (let [ch (chan 1)]
    (GET (str "/api/user/" username "/info")
         {:handler (fn [response]
                     (go (>! ch response)
                         (close! ch)))
          :error-handler #()})
    ch))



(defn get-comments [id]
  (let [ch (chan 1)]
    (GET (str "/api/comments/" id)
         {:handler (fn [response]
                     (go (>! ch response)
                         (close! ch)))
          :error-handler #()})
    ch))

(defn get-reacts [id]
  (let [ch (chan 1)]
    (GET (str "/api/reacts/" id)
         {:handler (fn [response]
                     (go (>! ch response)
                         (close! ch)))
          :error-handler #()})
    ch))

(defn get-post [id]
  (let [ch (chan 1)]
    (GET (str "/api/post/" id)
         {:handler (fn [response]
                     (go (>! ch response)
                         (close! ch)))
          :error-handler #()})
    ch))


(defn save-mail! [mail]
  (let [ch (chan 1)]
    (POST "/mail"
          {:params {:mail mail}
           :handler (fn [response]
                      (go (>! ch response)
                          (close! ch)))
           :error-handler #()})
    ch))