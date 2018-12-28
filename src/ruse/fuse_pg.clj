(ns ruse.fuse-pg
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

(defn getattr-directory [{:keys [path stat]}]
  (doto stat
    (-> .-st_mode (.set (bit-or FileStat/S_IFDIR (read-string "0755"))))
    (-> .-st_nlink (.set 2))))

(defn getattr-file [{:keys [path stat]}]
  (doto stat
    (-> .-st_mode (.set (bit-or FileStat/S_IFREG (read-string "0444"))))
    (-> .-st_nlink (.set 1))
    ;; Fake size reporting - 10MB is plenty.
    (-> .-st_size (.set (* 1024 1024 1)))))

(defn readdir-list-files-base
  "FILES is a string col."
  [{:keys [path buf filt offset fi]} dirs files]
  (doto filt
    (.apply buf "." nil 0)
    (.apply buf ".." nil 0))
  (doseq [dir dirs]
    (.apply filt buf dir nil 0))
  (doseq [file files]
    (.apply filt buf file nil 0))
  filt)

(defn readdir-list-files [{:keys [path buf filt offset fi] :as m}]
  (cond
    (= "/" path) (readdir-list-files-base m (dog/get-breeds) [])
    ;; Pop off leading slash and show the list of breeds.
    :else (readdir-list-files-base m [] (dog/get-dog-list! (subs path 1)))
    ))

(defn read-fuse-file [{:keys [path buf size offset fi]}]
  (let [
        bytes (dog/get-dog-pic path)
        length (count bytes)
        bytes-to-read (min (- length offset) size)
        contents (ByteBuffer/wrap bytes)
        bytes-read (byte-array bytes-to-read)
        ]
    (doto contents
      (.position offset)
      (.get bytes-read 0 bytes-to-read))
    (-> buf (.put 0 bytes-read 0 bytes-to-read))
    (.position contents 0)
    bytes-to-read))

(defn set-stub-dirs []
  (->> (conj (map #(str "/" %) (dog/get-breeds)) "/")
       (into [])))

(def stub-dirs (set-stub-dirs))

(defn fuse-custom-mount []
  (proxy [FuseStubFS] []
    (getattr
      [path stat]                       ; string , jni
      (cond
        (u/member path stub-dirs) (getattr-directory (u/lexical-ctx-map))
        (dog/dog-exists? path) (getattr-file (u/lexical-ctx-map))
        :else (enoent-error)))
    (readdir
      [path buf filt offset fi]
      ;; Here we choose what to list.
      (prn "In readdir")
      (if (not (u/member path stub-dirs))
        (enoent-error)
        (readdir-list-files (u/lexical-ctx-map))))
    (open
      [path fi]
      ;; Here we handle errors on opening
      (prn "In open: " path fi)
      (if (and (u/member path stub-dirs) (not (dog/dog-exists? path)))
        (enoent-error)
        0))
    (read
      [path buf size offset fi]
      ;; Here we read the contents
      (prn "In read" path)
      (if
          (not (dog/dog-exists? path))
          (enoent-error)
          (read-fuse-file (u/lexical-ctx-map))))))

(def stub-atom (atom nil))

(defn mount-it! [dir]
  (let [stub (fuse-custom-mount)]
    (future
      (reset! stub-atom stub)
      ;; params: path blocking debug options
      (-> stub (.mount (u/string-to-path dir) true true (into-array String []))))
    ))

(defn umount-it! []
  (-> @stub-atom .umount))

(defn cleanup-hooks [mnt]
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn []
              (println "Unmounting " mnt)
              (umount-it!)))))

(defn main
  "I don't do a whole lot ... yet."
  [dir]
  (cleanup-hooks dir)
  (println "Mounting: " dir)
  (deref (mount-it! dir))
  (println "Try going to the directory and running ls."))
