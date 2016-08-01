(ns jurenal.routes.home
  (:require [jurenal.layout :as layout]
            [jurenal.db.core :as db]
            [compojure.core :refer [defroutes GET POST ANY]]
            [ring.util.http-response :as response]
            [ring.util.response :refer [redirect]]
            [clojure.java.io :as io]
            [bouncer.core :as b]
            [bouncer.validators :as v]
            [clojure.tools.logging :as log]))

(defn home-page []
  (layout/render "home.html"))

(defn log-request [{:keys [params]}]
  (log/debug params)
  (response/ok {:status :ok}))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/docs" [] (response/ok (-> "docs/docs.md" io/resource slurp))))
