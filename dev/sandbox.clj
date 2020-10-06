(ns sandbox
  (:require [clojure.spec.alpha :as s]
            [factui.api :as f]
            [factui.specs :as fs]
            [factui.specs.clara :as cs]
            [factui.specs.datalog :as ds]))

(s/conform ::fs/condition '[?a :foo "b"])
(s/conform ::fs/condition '[(= ?b "b")])
(s/unform ::cs/test-expr '[:test (= ?b "b")])

(f/defrule foo
  [?a :b ?b]
  [(= ?b "b")]
  =>
  (println "BEE"))
