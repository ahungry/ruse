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

(defn enoent-error []
  (* -1 (ErrorCodes/ENOENT)))

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

                ;; Otherwise, if the dog exists, treat it like its there.
                (dog/dog-exists? path)
                (doto stat
                  (-> .-st_mode (.set (bit-or FileStat/S_IFREG (read-string "0444"))))
                  (-> .-st_nlink (.set 1))
                  ;; Fake size reporting - 10MB is plenty.
                  (-> .-st_size (.set (* 1024 1024 1))))

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
                    ;; Go back up a directory
                    (.apply buf "." nil 0)
                    (.apply buf ".." nil 0)
                    ;; List other top level directories.
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
                    bytes-to-read
                    )
                  ))
            )]
    o))

(def stub-atom (atom nil))

(defn mount-it [dir]
  (let [stub (hello-fuse-custom)]
    (future
      (reset! stub-atom stub)
      ;; params: path blocking debug options
      (-> stub (.mount (u/string-to-path dir) true true (into-array String []))))
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
