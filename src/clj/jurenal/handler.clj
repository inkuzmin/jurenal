(ns jurenal.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [jurenal.layout :refer [error-page]]
            [jurenal.routes.home :refer [home-routes mail-routes]]
            [jurenal.routes.websocket :refer [websocket-routes]]
            [jurenal.routes.bot :refer [bot-routes]]
            [jurenal.routes.api :refer [api-routes]]
            [compojure.route :as route]
            [jurenal.env :refer [defaults]]
            [mount.core :as mount]
            [jurenal.middleware :as middleware]))

(mount/defstate init-app
                :start ((or (:init defaults) identity))
                :stop  ((or (:stop defaults) identity)))


(def app-routes
  (routes
    #'websocket-routes
    (-> #'api-routes
        (wrap-routes middleware/wrap-formats))
    (-> #'bot-routes
        (wrap-routes middleware/wrap-formats))
    (-> #'home-routes
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats))
    (route/not-found
      (:body
        (error-page {:status 404
                     :title "page not found"})))))


(defn app [] (middleware/wrap-base #'app-routes))
