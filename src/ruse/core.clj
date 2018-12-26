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
  (let [o (proxy [FuseStubFS] []
            (getattr [path stat]
              (cond
                (-> Objects (.equals path "/"))
                (do
                  (doto stat
                    (-> .st_mode (.set (bit-or FileStat/S_IFDIR 755)))
                    (-> .st_nlink (.set 2))) 0)

                (= "/hello" path)
                (do
                  (doto stat
                    (-> .st_mode (.set (bit-or FileStat/S_IFREG 444)))
                    (-> .st_nlink (.set 1))
                    (-> .st_size (.set 11))) 0)

                :else
                (enoent-error)
                ))
            (readdir [path buf filt offset fi]
              (if (not (= "/" path))
                (enoent-error)
                (do
                  (doto filt
                    (.apply buf "." nil 0)
                    (.apply buf ".." nil 0)
                    (.apply buf "hello" nil 0))
                  0))
              )
            (open [path fi]
              (if (not (= "/hello" path))
                (enoent-error)
                0))
            (read [path buf size offset fi]
              (if (not (= "/hello" path))
                (enoent-error)
                (let [bytes (->> (String. "Hello World!") .getBytes (into-array Byte/TYPE))
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
    (-> stub (.mount (string-to-path "/tmp/mnth") true true))
    (reset! stub-atom stub)))

(defn unmount-it []
  (-> @stub-atom .umount))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
