(ns ruse.util
  (:require
   [clojure.repl :refer :all]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.test.alpha :as stest]
   [clj-http.client :as client]
   )
  (:import
   (java.io File)
   (java.nio.file Paths)
   )
  (:gen-class)
  )

(defn string-to-uri [s]
  (-> s File. .toURI))

(defn uri-to-path [s]
  (Paths/get s))

(defn string-to-path [s]
  (-> s string-to-uri uri-to-path))

(defn split-by-slash [s]
  (clojure.string/split s #"/"))

(defn member [s col]
  (some #(= s %) col))
