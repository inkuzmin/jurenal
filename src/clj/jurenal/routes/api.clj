(ns jurenal.routes.api
  (:require [jurenal.layout :as layout]
            [jurenal.db.core :as db]
            [compojure.core :refer [defroutes GET POST ANY]]
            [ring.util.http-response :as response]
            [ring.util.response :refer [redirect]]
            [clojure.java.io :as io]
            [bouncer.core :as b]
            [bouncer.validators :as v]
            [clj-time.core :as t]
            [jurenal.utils.parser :as parser]
            [clj-time.coerce :as tc]
            [jurenal.utils.emoji :as emoji]
            [clojure.tools.logging :as log])
  (:use [jurenal.utils.useful]))



(defn log-request [{:keys [params]}]
  (log/debug params)
  (response/ok {:status :ok}))

(defn get-reactions [id]
  (response/ok (db/get-reactions {:post_id (read-string id)})))


(defn parse-post [post]
  (let [user (db/get-user {:id (:user_id post)})]
    (try
      (cond
        (= (:text post) "/start")
        (-> post
            (assoc :text  (str "Welcome abroad,  "
                               (:first_name post) " "
                               (:last_name post) "!"))
            (assoc :first_name "sys_message")
            (assoc :comments (:count (db/count-comments {:id (:id post)})))
            (assoc :last_name nil))

        :default
        (-> post
            (assoc :user (if (:anon post) {:anon true} user))
            (assoc :reacts (map
                             (fn [e]
                               (assoc ((comp emoji/get-emoji :emoji) e) :id (:id e)))
                             (db/get-reactions {:post_id (:id post)})))
            (assoc :recommendations (db/get-recommendations {:post_id (:id post)}))
            (assoc :attach
                   (if (:attach_id post)
                     (db/get-attach {:id (:attach_id post)}) nil))
            (assoc :text  (parser/parse (:text post)))
            (assoc :comments (:count (db/count-comments {:id (:id post)})))))

      (catch Exception e
        (assoc post :text  "Parsing error.")))))


(defn get-comments [id]
  (let [comments (db/get-comments {:id (read-string id)})
        parsed-comments (map parse-post comments)]
    (response/ok parsed-comments)))


(defn get-post [id]
  (let [post (db/get-post {:id (read-string id)})
        parsed-post (parse-post post)]
    (response/ok parsed-post)))



(defn get-posts-by-username [username offset limit]
  (let [user (db/get-user-by-username (keyed [username]))
        posts (db/get-posts-by-user-id {:user_id (:id user)
                                        :offset offset
                                        :limit limit})
        parsed-posts (map parse-post posts)]
    (response/ok parsed-posts)))

(defn get-info-by-username [username]
  (let [user (db/get-user-by-username (keyed [username]))
        subscriptions (:count (db/count-subscriptions {:id (:id user)}))
        subscribers (:count (db/count-subscribers {:id (:id user)}))]
    (response/ok (-> user
                     (assoc :subscriptions subscriptions)
                     (assoc :subscribers subscribers)))))



(defn get-posts-by-user_id [id offset limit]
  (let [user (db/get-user (keyed [id]))
        posts (db/get-posts-by-user-id {:user_id (:id user)
                                        :offset offset
                                        :limit limit})
        parsed-posts (map parse-post posts)]
    (response/ok parsed-posts)))

(defn get-info-by-user_id [id]
  (let [user (db/get-user (keyed [id]))
        subscriptions (:count (db/count-subscriptions {:id (:id user)}))
        subscribers (:count (db/count-subscribers {:id (:id user)}))]
    (response/ok (-> user
                     (assoc :subscriptions subscriptions)
                     (assoc :subscribers subscribers)))))

(defn get-posts-by- [{:keys [params]}]
  (let [user-identifier (read-string (:username-or-id params))
        user-id? (integer? user-identifier)
        offset   (read-string (:offset params))
        limit    (read-string (:limit params))]
    (if user-id?
      (get-posts-by-user_id user-identifier offset limit)
      (get-posts-by-username (str user-identifier) offset limit))))

(defn get-info-by- [{:keys [params]}]
  (let [user-identifier (read-string (:username-or-id params))
        user-id? (integer? user-identifier)]
    (if user-id?
      (get-info-by-user_id user-identifier)
      (get-info-by-username (str user-identifier)))))


(defn get-posts [{:keys [params]}]
  (let [posts (db/get-posts {
                             :limit (read-string (:limit params))
                             :offset (read-string (:offset params))})

        parsed-posts  (map parse-post posts)]

    (response/ok parsed-posts)))



(defroutes api-routes
  (GET "/api/user/:username-or-id/posts" request (get-posts-by- request))
  (GET "/api/user/:username-or-id/info" request (get-info-by- request))

  (GET "/api/reacts/:id{[0-9]+}" [id] (get-reactions id))
  (GET "/api/post/:id{[0-9]+}" [id] (get-post id))
  (GET "/api/posts" request (get-posts request))
  (GET "/api/comments/:id{[0-9]+}" [id] (get-comments id)))


