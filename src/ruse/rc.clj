(ns ruse.rc
  (:require
   [clojure.repl :refer :all]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.test.alpha :as stest]
   [ruse.util :as u]
   )
  (:import
   )
  (:gen-class)
  )

(defn get-xdg-config-home []
  (or (System/getenv "XDG_CONFIG_HOME")
      (System/getProperty "user.home")))

(defn get-rc-file-raw []
  (let [defaults (read-string (slurp "conf/default-rc.edn"))
        home-rc (format "%s/.ruserc" (System/getProperty "user.home"))
        xdg-rc (format "%s/ruse/ruserc" (get-xdg-config-home))]
    (conj
      defaults
      (if (.exists (clojure.java.io/file home-rc))
        (read-string (slurp home-rc)))
      (if (.exists (clojure.java.io/file xdg-rc))
        (read-string (slurp xdg-rc))))))

(defn get-rc []
  (let [rc (get-rc-file-raw)]
    rc))
