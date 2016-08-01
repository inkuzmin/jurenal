(ns jurenal.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [jurenal.core-test]))

(doo-tests 'jurenal.core-test)

