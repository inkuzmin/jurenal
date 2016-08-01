(ns jurenal.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[jurenal started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[jurenal has shutdown successfully]=-"))
   :middleware identity})
