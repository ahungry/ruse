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

(defn api-get-dog-breeds []
  (-> (client/get "https://dog.ceo/api/breeds/list/all"
                  {:as :json})
      :body :message))

(def mapi-get-dog-breeds (memoize api-get-dog-breeds))

;; Pull some remote values
(defn api-get-dog-pics [breed]
  (-> (client/get
       ;; "https://dog.ceo/api/breeds/list/all"
       (str "https://dog.ceo/api/breed/" breed "/images")
       {:as :json})
      :body :message))

(def mapi-get-dog-pics (memoize api-get-dog-pics))

(defn api-get-dog-pic
  "Ensure that S has the leading slash.
  Sample: /whippet/n02091134_10242.jpg"
  [breed s]
  (-> (client/get
       (str "https://images.dog.ceo/breeds/" breed "/" s)
       ;; {:as :stream}
       {:as :byte-array}
       )
      :body))

(def mapi-get-dog-pic (memoize api-get-dog-pic))

(defn get-dog-pics [breed]
  (mapi-get-dog-pics breed))

(defn get-dog-pic [p]
  (let [[_ breed s] (u/split-by-slash p)]
    (mapi-get-dog-pic breed s)))

(defn get-breeds []
  (->> (mapi-get-dog-breeds)
       keys
       (map #(subs (str %) 1))
       (into [])))

(defn breed-exists? [path]
  (u/member (subs path 1) (get-breeds)))

(defn get-few-dog-pics [breed]
  (into [] (take 10 (get-dog-pics breed))))

(defn get-filename-only [s]
  (nth (reverse (u/split-by-slash s)) 0))

(defn get-pics-clean [breed]
  (doall
   (into [] (map get-filename-only (get-few-dog-pics breed)))))

(defn get-dog-list! [breed]
  (get-pics-clean breed))

(defn dog-exists?
  "Check against the path string, S always has a leading slash.
  Sample: /whippet/n02091134_10918.jpg"
  [p]
  (let [[_ breed s] (u/split-by-slash p)]
    (let [dogs (get-dog-list! breed)]
      (u/member s dogs))))
