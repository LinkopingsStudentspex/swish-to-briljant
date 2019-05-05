(ns swish-to-briljant.utilities
  (:require [clojure.string :as string]))

(defn tprn
  [arg]
  (prn arg)
  arg)

(defn condp-fn
  "Works like clojure.core/condp except that instead of a being a
  macro taking pairs of clauses it is a function taking a sequence
  of clauses."
  [predicate expr clauses  & [default]]
  (or (second (first (filter #(predicate (first %) expr)
                             (partition 2 clauses))))
      default))

(defn re-find-safe
  [regex string]
  (if (= (type string) java.lang.String)
    (re-find regex string)
    nil))

(defn capitalize-words
  "Capitalize every word in a string"
  [s]
  (->> (string/split (str s) #"\b")
       (map string/capitalize)
       string/join))
