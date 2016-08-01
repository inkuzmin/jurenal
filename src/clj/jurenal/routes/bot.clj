(ns jurenal.routes.bot
  (:require [jurenal.layout :as layout]
            [jurenal.db.core :as db]
            [compojure.core :refer [defroutes GET POST ANY]]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]
            [bouncer.core :as b]
            [bouncer.validators :as v]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [jurenal.utils.parser :as parser]
            [cprop.core :refer [load-config]]
            [jurenal.utils.emoji :as emoji]
            [markdown.core :as md]
            [jurenal.routes.websocket :as ws]
            [clj-http.client :as client]
            [environ.core :refer [env]]
            [amazonica.aws.s3 :as s3]
            [amazonica.aws.s3transfer :as s3transfer]
            [clojure.core.async :as async :refer [>! <! >!! <!! go chan buffer close! thread
                                                  alts! alts!! timeout]]

            [clojure.tools.logging :as log])
  (:use [jurenal.utils.useful]))



(def ^:dynamic *api*
  (str "https://api.telegram.org/bot" (env :api-token)))

(def ^:dynamic *file-api*
  (str "https://api.telegram.org/file/bot" (env :api-token) "/"))

(def s3-bucket "jurenal")
(def s3-url "https://s3.amazonaws.com/jurenal/")



(defn create-user! [user]
  (let [u {:id (:id user)
           :username (:username user)
           :last_name (:last_name user)
           :first_name (:first_name user)
           :created (tc/to-timestamp (t/now))}]
    (db/create-user! u)
    u))

(defn get-user [user]
  (db/get-user {:id (:id user)}))

(defn create-user-if-not-exists! [user]
  (let [u (get-user {:id (:id user)})]
    (if (nil? u)
      (create-user! user)
      u)))

(defn update-user! [user]
  (db/update-user! {:id (:id user)
                    :username (:username user)
                    :first_name (:first_name user)
                    :last_name (:last_name user)}))



(defn sync-users! [user]
  (let [u (create-user-if-not-exists! user)
        other-users (db/get-users-by-username {:username (:username user)})]
    (when (or
            (not (= (:username u) (:username user)))
            (not (= (:last_name u) (:last_name user)))
            (not (= (:first_name u) (:first_name user))))
      (update-user! user))

    (doseq [other other-users]
      (if (not (= (:id other) (:id user)))
        (update-user! {:id (:id other)
                       :first_name (:first_name other)
                       :last_name (:last_name other)
                       :username nil})))
    u))


(defn create-post! [user_id text attach_id anon]
  (db/create-post! (assoc (keyed [user_id text attach_id anon])
                     :date (tc/to-timestamp (t/now)))))

(defn create-comment! [user_id text parent_id anon]
  (db/create-comment! (assoc (keyed [text parent_id user_id anon])
                        :date (tc/to-timestamp (t/now)))))

(defn fetch-file!
  "makes an HTTP request and fetches the binary object"
  [url]
  (let [req (client/get url {:as :byte-array :throw-exceptions false})]
    (if (= (:status req) 200)
      (:body req))))

(defn save-file!
  "downloads and stores the photo on disk"
  [url path]
  (let [p (fetch-file! url)]
    (if (not (nil? p))
      (with-open [w (io/output-stream path)]
        (.write w p)))))


(defn api-request [method params]
  (let [resp (client/get (str *api* method)
                         {:query-params params
                          :as :json})
        status (:status resp)
        body (when (= status 200) (:body resp))
        ok (:ok body)]
    (when ok (:result body))))



(defn get-file-path [id]
  (:file_path
    (api-request "/getFile" {:file_id id})))

(defn upload-file-to-s3 [name bytes]
  (s3/put-object :bucket-name s3-bucket
                 :key name
                 :metadata {:content-length (count bytes)}
                 :input-stream (java.io.ByteArrayInputStream. bytes)))


(defn userpic-exists? [id]
  (if (io/resource (str "public/img/userpics/" id ".jpg")) true false))


(defn save-userpic! [id]
  (try
    (let [result (api-request "/getUserProfilePhotos" {:user_id id})
          file-id (when (not (zero? (:total_count result)))
                    (:file_id (first (first (:photos result)))))
          file-path (when file-id (get-file-path file-id))
          file (fetch-file! (str *file-api* file-path))]


      (upload-file-to-s3 (str id ".jpg") file)
      (db/update-userpic! {:id id
                           :userpic (str id ".jpg")}))
    (catch Exception e (do
                         (log/debug (str "caught exception: " (.getMessage e)))))))


(defn get-ext [path]
  (last (clojure.string/split path #"\.")))

(defn get-file-unique-part [file-id]
  (subs file-id 8 34))

(defn save-photo! [id name]
  (try
    (let [file-path (get-file-path id)
          file (fetch-file! (str *file-api* file-path))
          dir-name (str (get-file-unique-part id) "/")
          file-name (str name "." (get-ext file-path))]


      (upload-file-to-s3 (str dir-name file-name) file)
      file-name)

    (catch Exception e (do
                         (log/debug (str "caught exception: " (.getMessage e)))))))


(defn save-photos! [photos]
  (save-photo! (:file_id (second photos)) "preview")
  (thread (save-photo! (:file_id (last photos)) "full"))
  (str (get-file-unique-part (:file_id (first photos)))))


(defn photo-handler [params]
  (try
    (let [photos (:photo (:message params))
          user (sync-users! (:from (:message params)))
          cid (:id (:chat (:message params)))]

      (let [file_id (save-photos! photos)
            attach_id  (:id (first (db/create-attach! {:type "photo"
                                                       :file_id file_id})))
            pid (:id (first (create-post! (:id user) "" attach_id false)))]
        (ws/broadcast! (str {:username (:username user)
                             :userpic (:userpic user)
                             :first_name (:first_name user)
                             :last_name (:last_name user)
                             :text nil
                             :pid pid
                             :attach {:file_id file_id
                                      :type "photo"}
                             :parent_id 0
                             :date (tc/to-timestamp (t/now))})))


      (client/get (str *api* "/sendMessage")
                  {:query-params {:chat_id cid
                                  :parse_mode "HTML"
                                  :text "Message was delivered."}})
      (response/ok {:status :ok}))
    (catch Exception e (do
                         (log/debug (str "caught exception: " (.getMessage e)))
                         (response/ok {:status :ok})))))


(defn add-reaction! [pid uid emoji]
  (if (db/get-reaction {:post_id pid, :user_id uid})
    (db/update-reaction! {:post_id pid, :user_id uid, :emoji emoji})
    (db/create-reaction! {:post_id pid, :user_id uid, :emoji emoji})))

(defn add-recommendation! [pid uid]
  (when-not (db/get-recommendation {:post_id pid, :user_id uid})
    (db/create-recommendation! {:post_id pid, :user_id uid})))



(defn send-message
  ([cid]
   (client/get (str *api* "/sendMessage")
               {:query-params {:chat_id cid
                               :parse_mode "HTML"
                               :text "Message was delivered."}}))

  ([cid text]
   (client/get (str *api* "/sendMessage")
               {:query-params {:chat_id cid
                               :parse_mode "HTML"
                               :text text}})))

(defn subscribe! [subject_id object_id]
  (db/create-subscription! {:subject_id subject_id :object_id object_id}))

(defn unsubscribe! [subject_id object_id]
  (db/remove-subscription! {:subject_id subject_id :object_id object_id}))

(defn ! [text]
  (log/debug "=======================================================")
  (log/debug text)
  (log/debug "======================================================="))





(defn broadcast! [uid pid]
  (if-let [subscribers (db/get-subscribers {:object_id uid})]
    (let [post (db/get-post {:id pid})
          user (db/get-user {:id uid})]
      (doseq [s subscribers]
        (send-message (:subject_id s)
               (str
                 (:first_name user) " " (:last_name user)
                 " has posted new message "))))))



(defn subscribe [user message]
  (if-let [username (subs (first (:mentions message)) 1)]
    (do
      (if-let [object (db/get-user-by-username (keyed [username]))]
        (subscribe! (:id user) (:id object)))
      (send-message (:id user) "Subscription on."))))

(defn unsubscribe [user message]
  (if-let [username (subs (first (:mentions message)) 1)]
    (do
      (if-let [object (db/get-user-by-username (keyed [username]))]
        (unsubscribe! (:id user) (:id object)))
      (send-message (:id user) "Subscription off."))))

(defn react [user message]
  (! message)
  (add-reaction! (:reply message) (:id user)
                 (emoji/get-emoji-by-code (:text message)))
  (send-message (:id user) "Message was delivered"))

(defn reply [anon user message]
  (if (db/get-post {:id (:reply message)})
    (do
      (let [pid (:id (first (create-comment! (:id user) (:text message) (:reply message) anon)))]
        (send-message (:user_id (db/get-post {:id (:reply message)}))
               (str ":" pid " "
                    (if anon
                      "Anonymous"
                      (str (:first_name user) " " (:last_name user)))
                    ": "
                    (:source message))))
      (send-message (:id user)))
    (do
      (send-message (:id user) "Message was not delivered"))))


(defn recommend [user message]
  (! message)
  (add-recommendation! (:reply message) (:id user))
  (broadcast! (:id user) (:reply message))
  (send-message (:id user) "Message was delivered."))

(defn post [anon user message]
  (let [pid (:id (first (create-post! (:id user) (:text message) nil anon)))]
    (broadcast! (:id user) pid)
    (send-message (:id user))))


(defn text-handler [params]
  (let [user (sync-users! (:from (:message params)))
        message (parser/parse-message (:message params))]

    (when (not (:userpic user))
      (thread (save-userpic! (:id user))))

    (! message)

    ((cond
      (some #{"/subscribe"} (:commands message))
      (comp subscribe)

      (some #{"/unsubscribe"} (:commands message))
      (comp unsubscribe)

      (some #{"/recommend"} (:commands message))
      (comp recommend)

      (some #{"/update"} (:commands message))
      (do (thread (save-userpic! (:id user)))
        (send-message (:id user) "Information was updated."))


      (:reply message)
      (cond
        (:react message)
        (comp react)

        (some #{"/anon"} (:commands message))
        (partial reply true)

        :else
        (partial reply false))

      (and (:reply message) (:react message))
      (comp react)

      :else
        (cond
          (some #{"/anon"} (:commands message))
          (partial post true)

          :else
              (partial post false))) user message)))


(defn bot-handler [{:keys [params]}]
  (try
    (if (:text (:message params))
      (text-handler params))
    (if (:photo (:message params))
      (photo-handler params))
    (response/ok {:status :ok})
    (catch Exception e (do
                         (send-message
                           (:id (:chat (:message params)))
                           "!")
                         (log/debug "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                         (log/debug (str "caught exception: " (.getMessage e)))
                         (log/debug "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                         (response/ok {:status :ok})))))

(defroutes bot-routes
  (POST "/<bot_secret>" request (bot-handler request)))