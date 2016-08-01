(ns jurenal.user
  (:require
    [clojure.string :as str]
    [clojure.walk :as walk]
    [environ.core :refer [env]]))

(defn contextual-eval [ctx expr]
  (eval
    `(let [~@(mapcat (fn [[k v]]
                       (println `~v)
                       [k `~v]) ctx)]
       ~expr)))

(defmacro do-until [& clauses]
  (when clauses
    (list 'clojure.core/when (first clauses)
          (if (next clauses)
            (second clauses)
            (throw (IllegalArgumentException.
                     "do-until requires an even number of forms")))
          (cons 'do-until (nnext clauses)))))


(defmacro unless [condition & body]
  `(if (not ~condition)
     (do ~@body)))




















