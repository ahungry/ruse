(ns ruse.util
  (:require
   [clojure.repl :refer :all]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.test.alpha :as stest]
   [clj-http.client :as client]
   )
  (:import
   )
  (:gen-class)
  )

(defn split-by-slash [s]
  (clojure.string/split s #"/"))

(defn member [s col]
  (some #(= s %) col))
