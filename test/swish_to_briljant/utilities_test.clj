(ns swish-to-briljant.utilities-test
  (:require [swish-to-briljant.utilities :as sut]
            [clojure.test :refer [deftest is]]))

(deftest condp-map-simple-matches
  (is (= (sut/condp-map re-find "Hi there sailor." {#"Hi" :hi}) :hi))
  (is (= (sut/condp-map re-find "Hi there sailor." {#"hi" :hi}) nil)))

(deftest condp-map-default
  (is (= (sut/condp-map re-find "Hi there sailor." {#"hi" :hi} :default) :default)))
