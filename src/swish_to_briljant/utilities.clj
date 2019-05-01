(ns swish-to-briljant.utilities)

(defn tprn
  [arg]
  (prn arg)
  arg)

(defn condp-fn
  "Works like clojure.core/condp except that instead of a being a
  macro taking pairs of clauses it is a function taking a sequence
  of clauses."
  [predicate expr clauses]
  (second (first (filter #(predicate (first %) expr)
                         (partition 2 clauses)))))



(defn re-find-safe
  [regex string]
  (if (= (type string) java.lang.String)
    (re-find regex string)
    nil))
