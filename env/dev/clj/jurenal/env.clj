(ns jurenal.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [jurenal.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[jurenal started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[jurenal has shutdown successfully]=-"))
   :middleware wrap-dev})
