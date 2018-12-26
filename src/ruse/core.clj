(ns ruse.core
  (:require
   [clojure.repl :refer :all]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.test.alpha :as stest]
   [clj-http.client :as client]
   )
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
       ;; {:as :byte-array}
       )
      :body))

(defn get-few-dog-pics []
  (into [] (take 10 (get-dog-pics))))

(defn split-by-slash [s]
  (clojure.string/split s #"/"))

(defn get-filename-only [s]
  (nth (reverse (split-by-slash s)) 0))

(defn get-pics-clean []
  (map get-filename-only (get-few-dog-pics)))

(defn member [s col]
  (some #(= s %) col))

(def http-cache (atom []))
(defn set-http-cache! []
  (reset! http-cache (doall (into [] (get-pics-clean)))))

(defn dog-exists?
  "Check against the path string, always has a leading slash."
  [s]
  (prn s)
  (member (subs s 1) @http-cache))

(defn hello-fuse-custom []
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

                (dog-exists? path)
                ;; (= hello-path path)
                (doto stat
                  (-> .-st_mode (.set (bit-or FileStat/S_IFREG (read-string "0444"))))
                  (-> .-st_nlink (.set 1))
                  ;; (-> .-st_size (.set (count hello-str)))
                  ;; TODO: Need to get the real size or tools won't know how to read it.
                  ;; (-> .-st_size (.set (* 1024 1024 10)))
                                        ; Fake size reporting - 10MB is mostly plenty.
                  (-> .-st_size (.set 67617))            ; dane-0.jpg test case
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
                  (doseq [img @http-cache]
                    (.apply filt buf img nil 0))
                  filt)
                )
              )

            (open [path fi]
              ;; Here we handle errors on opening
              (prn "In open: " path fi)
              (if (not (dog-exists? path))
                  (enoent-error)
                  0))

            (read [path buf size offset fi]
              ;; Here we read the contents
              (prn "In read")
              (if
                  (not (dog-exists? path))
                  (enoent-error)
                  ;; (clojure.java.io/copy
                  ;;  (get-dog-pic path)
                  ;;  buf)
                  (let [bytes
                        ;; (->> hello-str .getBytes (into-array Byte/TYPE))
                        ;; (-> hello-str .getBytes byte-array)
                        ;; (->> (get-dog-pic path) (.getBytes "ASCII") byte-array)
                        ;; (->> (get-dog-pic path) (.getBytes "UTF-8") byte-array)
                        ;; (->> (get-dog-pic path) (.getBytes "ISO-8859-1") byte-array)
                        (->> (String. (get-dog-pic path) "ASCII")
                             (.getBytes "ASCII") byte-array)
                        ;; (get-dog-pic path)
                        length (count bytes) ;; 67617 ;; (count bytes)
                        my-size size]
                    (if (< offset length)
                      (do
                        (when (> (+ offset my-size) length)
                          (def my-size (- length offset)))
                        (-> buf (.put 0 bytes 0 my-size)))
                      ;; else
                      (def my-size 0)
                      )
                    my-size)
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
