(ns ruse.dog
  (:require
   [clojure.repl :refer :all]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.test.alpha :as stest]
   [clj-http.client :as client]
   [ruse.util :as u]
   )
  (:import
   )
  (:gen-class)
  )

;; Pull some remote values
(defn get-dog-pics []
  (-> (client/get
       ;; "https://dog.ceo/api/breeds/list/all"
       "https://dog.ceo/api/breed/dane-great/images"
       {:as :json})
      :body :message))

(def base-image-url "See it?"
  "https://images.dog.ceo/breeds/dane-great")

(defn get-dog-pic
  [s]
  (-> (client/get
       (str base-image-url s)
       ;; {:as :stream}
        {:as :byte-array}
       )
      :body))

(defn test-get-dog-pic []
  (let [bytes (get-dog-pic "/dane-0.jpg")]
    (prn (count bytes))
    (clojure.java.io/copy
     ;; (->> (get-dog-pic "/dane-0.jpg") (.getBytes "UTF-8"))
     bytes
     (java.io.File. "/tmp/dane-0-test.jpg")))
  ;; (spit "/tmp/dane-0-test.jpg"
  ;;       (get-dog-pic "/dane-0.jpg")
  ;;       :encoding "UTF-8")
  )

(defn get-few-dog-pics []
  (into [] (take 10 (get-dog-pics))))

(defn get-filename-only [s]
  (nth (reverse (u/split-by-slash s)) 0))

(defn get-pics-clean []
  (map get-filename-only (get-few-dog-pics)))

(def http-cache (atom []))
(defn set-http-cache! []
  (reset! http-cache (doall (into [] (get-pics-clean)))))

(defn dog-exists?
  "Check against the path string, always has a leading slash."
  [s]
  (prn s)
  (u/member (subs s 1) @http-cache))
