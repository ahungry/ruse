(ns ruse.core
  (:require
   [clojure.repl :refer :all]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.test.alpha :as stest]
   [clj-http.client :as client]
   [ruse.util :as u]
   [ruse.dog :as dog]
   [ruse.fuse-dog :as fdog]
   [ruse.pg :as pg]
   [ruse.fuse-pg :as fpg])
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [[type dir] args]
    (cond
      (= "dog" type) (fdog/main dir)
      (= "pg" type) (fpg/main dir)
      :else (println "Please use a known system as first arg [dog, pg]" ))))
