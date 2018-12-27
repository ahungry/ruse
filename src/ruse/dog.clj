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
(defn get-dog-pics [breed]
  (-> (client/get
       ;; "https://dog.ceo/api/breeds/list/all"
       (str "https://dog.ceo/api/breed/" breed "/images")
       {:as :json})
      :body :message))

(defn get-few-dog-pics [breed]
  (into [] (take 10 (get-dog-pics breed))))

(defn get-filename-only [s]
  (nth (reverse (u/split-by-slash s)) 0))

(defn get-pics-clean [breed]
  (doall
   (into [] (map get-filename-only (get-few-dog-pics breed)))))

(def http-cache (atom {}))

(defn set-http-cache! [breed]
  (swap! http-cache conj {(keyword breed) (get-pics-clean breed)})
  ;; (reset! http-cache (doall (into [] (get-pics-clean))))
  )

(defn get-dog-list! [breed]
  (let [kw (keyword breed)]
    (if (kw @http-cache)
      (kw @http-cache)
      (kw (set-http-cache! breed)))))

(defn dog-exists?
  "Check against the path string, S always has a leading slash.
  Sample: /whippet/n02091134_10918.jpg"
  [p]
  (let [[_ breed s] (u/split-by-slash p)]
    (let [dogs ((keyword breed) @http-cache)]
      (u/member s dogs))))

(defn base-image-url [breed]
  (str "https://images.dog.ceo/breeds/" breed "/"))

(defn get-dog-pic
  "Ensure that S has the leading slash.
  Sample: /whippet/n02091134_10242.jpg"
  [p]
  (let [[_ breed s] (u/split-by-slash p)]
    (-> (client/get
         (str (base-image-url breed) s)
         ;; {:as :stream}
         {:as :byte-array}
         )
        :body)))
