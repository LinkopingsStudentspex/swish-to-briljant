(ns swish-to-briljant.arguments
  (:require [clojure.tools.cli :as cli]))

(def cli-options
  [["-h" "--help"]])

(defn validate-args
  "Validate command line arguments. Either return a map indicating the
  program should exit (with a error message, and optional ok status),
  or a map indicating the action the program should take and the
  options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)    {:exit-message summary :ok? true}
      errors             {:exit-message errors}
      (empty? arguments) {:exit-message "Missing input file argument."}
      :else              {:options options :arguments arguments})))
