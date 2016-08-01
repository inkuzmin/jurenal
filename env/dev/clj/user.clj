(ns user
  (:require [mount.core :as mount]
            [jurenal.figwheel :refer [start-fw stop-fw cljs]]
            jurenal.core))

(defn start []
  (mount/start-without #'jurenal.core/repl-server))

(defn stop []
  (mount/stop-except #'jurenal.core/repl-server))

(defn restart []
  (stop)
  (start))


