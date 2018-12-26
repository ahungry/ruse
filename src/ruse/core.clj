(ns ruse.core
  (:require
   [clojure.repl :refer :all]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.test.alpha :as stest])
  (:import
   (jnr.ffi Platform Pointer)
   (jnr.ffi.types off_t size_t)
   (ru.serce.jnrfuse ErrorCodes FuseFillDir FuseStubFS)
   (ru.serce.jnrfuse.struct FileStat FuseFileInfo)
   (java.io File)
   (java.nio.file Paths)
   (java.util Objects))
  (:gen-class))

(defn string-to-uri [s]
  (-> s File. .toURI))

(defn uri-to-path [s]
  (Paths/get s))

(defn string-to-path [s]
  (-> s string-to-uri uri-to-path))

(defn enoent-error []
  (* -1 (ErrorCodes/ENOENT)))

(defn hello-fuse []
  (let [hello-path (String. "/hello")
        hello-str (String. "Hello World!")
        o (proxy [FuseStubFS] []
            (getattr [path stat]
              (cond
                (-> Objects (.equals path "/"))
                (do
                  (doto stat
                    (-> .st_mode (.set (bit-or FileStat/S_IFDIR 0755)))
                    (-> .st_nlink (.set 2))) 0)

                (.equals hello-path path)
                (do
                  (doto stat
                    (-> .st_mode (.set (bit-or FileStat/S_IFREG 0444)))
                    (-> .st_nlink (.set 1))
                    (-> .st_size (.set 11))) 0)

                :else
                (enoent-error)
                ))
            (readdir [path buf filt offset fi]
              (if
                  (not (.equals (String. "/") path))
                  (enoent-error)
                  (do
                    (doto filt
                      (.apply buf "." nil 0)
                      (.apply buf ".." nil 0)
                      (.apply buf (.substring hello-path 1) nil 0))
                    0))
              )
            (open [path fi]
              (if
                  (not (.equals hello-path path))
                  (enoent-error)
                  0))
            (read [path buf size offset fi]
              (if
                  (not (.equals hello-path path))
                  (enoent-error)
                  (let [bytes
                        ;; (->> hello-str .getBytes (into-array Byte/TYPE))
                        (-> hello-str .getBytes byte-array)
                        length (count bytes)
                        size (if (< offset length)
                               (do
                                 (-> buf (.put 0 bytes 0 length))
                                 (if (> (+ offset size) length)
                                   (- length offset)
                                   0))
                               ;; else
                               0
                               )]
                    size)
                  ))
            )]
    o))

(def stub-atom (atom nil))

(defn mount-it []
  (let [stub (hello-fuse)]
    (-> stub (.mount (string-to-path "/tmp/tmp-2") true true))
    (reset! stub-atom stub)))

(defn unmount-it []
  (-> @stub-atom .umount))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
