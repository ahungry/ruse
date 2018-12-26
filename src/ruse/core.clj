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
   (ru.serce.jnrfuse.examples HelloFuse)
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
  (HelloFuse.))

(defn xhello-fuse-custom []
  (let [o
        (proxy [HelloFuse] []
          (getattr [path stat]
            (doto stat
              (-> .-st_mode (.set (bit-or FileStat/S_IFDIR (read-string "0755")))))))]
    o))

(defn hello-fuse-custom []
  (let [hello-path (String. "/hello")
        hello-str (String. "Hello World!")
        o (proxy [
                  FuseStubFS
                  ;; HelloFuse
                  ] []
            (getattr [path stat]
              ;; Here we set attributes
              (cond
                (Objects/equals path "/")
                (do
                  (doto stat
                    (-> .-st_mode (.set (bit-or FileStat/S_IFDIR (read-string "0755"))))
                    (-> .-st_nlink (.set 2))
                    )
                  0
                  )

                (.equals hello-path path)
                (do
                  (doto stat
                    (-> .-st_mode (.set (bit-or FileStat/S_IFREG (read-string "0444"))))
                     (-> .-st_nlink (.set 1))
                     (-> .-st_size (.set (count hello-str)))
                    )
                  0
                  )

                :else
                (enoent-error)
                ))

            (readdir [path buf filt offset fi]
              ;; Here we choose what to list.
              (prn "In readdir")
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
              ;; Here we handle errors on opening
              (prn "In open")
              (if
                  (not (.equals hello-path path))
                  (enoent-error)
                  0))

            (read [path buf size offset fi]
              ;; Here we read the contents
              (prn "In read")
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

(defn mount-it [dir]
  (let [stub (hello-fuse-custom)]
    (future (reset! stub-atom stub)
            (-> stub (.mount (string-to-path dir) true true (into-array String []))))
    ;; (-> stub (.mount (string-to-path "/tmp/tmp-23") nil true (into-array String [])))
    ;; (reset! stub-atom stub)
    ))

(defn umount-it []
  (-> @stub-atom .umount))

(defn cleanup-hooks [mnt]
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn [] (println "Unmounting " mnt)))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [dir (first args)]
    (cleanup-hooks dir)
    (println "Mounting: " dir)
    (deref (mount-it dir))
    (println "Try going to the directory and running ls.")))
