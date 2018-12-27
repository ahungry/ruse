(ns ruse.core
  (:require
   [clojure.repl :refer :all]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.test.alpha :as stest]
   [clj-http.client :as client]
   [ruse.util :as u]
   [ruse.dog :as dog]
   )
  (:import
   (jnr.ffi Platform Pointer)
   (jnr.ffi.types off_t size_t)
   (ru.serce.jnrfuse ErrorCodes FuseFillDir FuseStubFS)
   (ru.serce.jnrfuse.struct FileStat FuseFileInfo)
   (ru.serce.jnrfuse.examples HelloFuse)
   (java.io File)
   (java.nio.file Paths)
   (java.nio ByteBuffer)
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

(defn test-bytes []
  (let [bytes (->> (String. "Dog)") .getBytes)
        bytes-to-read 2
        bytes-read (byte-array bytes-to-read)
        contents (ByteBuffer/wrap bytes)
        ]
    (doto contents
      (.position 1)
      (.get bytes-read 0 bytes-to-read)
      (.position 0)
      )
    bytes-read))

(defn hello-fuse []
  (HelloFuse.))

(defn xhello-fuse-custom []
  (let [o
        (proxy [HelloFuse] []
          (getattr [path stat]
            (doto stat
              (-> .-st_mode (.set (bit-or FileStat/S_IFDIR (read-string "0755")))))))]
    o))

(defn hello-fuse-custom
  "A reference implementation.  Serves a directory from the dog API of
  the various images."
  []
  (let [hello-path (String. "/hello")
        hello-str (String. "Hello World!")
        o (proxy [
                  FuseStubFS
                  ;; HelloFuse
                  ] []
            (getattr [path stat]
              ;; path is just a string, easy.
              ;; Here we set attributes
              (cond
                ;; If it's the root, give dir permissions.
                (= "/" path)
                (doto stat
                  (-> .-st_mode (.set (bit-or FileStat/S_IFDIR (read-string "0755"))))
                  (-> .-st_nlink (.set 2))
                  )

                (dog/dog-exists? path)
                ;; (= hello-path path)
                (doto stat
                  (-> .-st_mode (.set (bit-or FileStat/S_IFREG (read-string "0444"))))
                  (-> .-st_nlink (.set 1))
                  ;; (-> .-st_size (.set (count hello-str)))

                  ;; TODO: Need to get the real size or tools won't know how to read it.
                  (-> .-st_size (.set (* 1024 1024 1)))
                                        ; Fake size reporting - 10MB is mostly plenty.

                  ;; (-> .-st_size (.set 67617))
                                        ; dane-0.jpg test case

                  ;; I bet we could do something weird like on full dir listings give a small number
                  ;; then on actual hits in the file (watching 'open') bump it way higher.
                  )

                :else
                (enoent-error)
                ))

            (readdir [path buf filt offset fi]
              ;; Here we choose what to list.
              (prn "In readdir")
              (if (not (= "/" path))
                (enoent-error)
                (do
                  (doto filt
                    (.apply buf "." nil 0)
                    (.apply buf ".." nil 0)
                    (.apply buf (.substring hello-path 1) nil 0)
                    )
                  (doseq [img @dog/http-cache]
                    (.apply filt buf img nil 0))
                  filt)
                )
              )

            (open [path fi]
              ;; Here we handle errors on opening
              (prn "In open: " path fi)
              (if (not (dog/dog-exists? path))
                (enoent-error)
                0))

            (read [path buf size offset fi]
              ;; Here we read the contents
              (prn "In read")
              (if
                  (not (dog/dog-exists? path))
                  (enoent-error)
                  ;; (clojure.java.io/copy
                  ;;  (get-dog-pic path)
                  ;;  buf)
                  (let [
                        bytes (dog/get-dog-pic path)
                        length (count bytes) ;; 67617 ;; (count bytes)
                        bytes-to-read (min (- length offset) size)
                        contents (ByteBuffer/wrap bytes)
                        bytes-read (byte-array bytes-to-read)
                        ]
                    (doto contents
                      (.position offset)
                      (.get bytes-read 0 bytes-to-read))
                    (-> buf (.put 0 bytes-read 0 bytes-to-read))
                    (.position contents 0)

                    ;; This works, but would the parsing code be needed?
                    ;; I guess if we had a very large file, it could exceed RAM/memory
                    ;; and this type of call would bomb out.
                    ;; (-> buf (.put 0 bytes 0 length))
                    ;; length
                    bytes-to-read
                    ;; https://github.com/SerCeMan/jnr-fuse/blob/master/src/main/java/ru/serce/jnrfuse/examples/HelloFuse.java
                    ;; (if (< offset length)
                    ;;   (do
                    ;;     (when (> (+ offset my-size) length)
                    ;;       (def my-size (- length offset)))
                    ;;     (-> buf (.put 0 bytes 0 my-size)))
                    ;;   ;; else
                    ;;   (def my-size 0)
                    ;;   )
                    ;; my-size
                    )
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
    (dog/set-http-cache!)
    (deref (mount-it dir))
    (println "Try going to the directory and running ls.")))
