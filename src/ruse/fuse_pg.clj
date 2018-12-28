(ns ruse.fuse-pg
  (:require
   [clojure.repl :refer :all]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.test.alpha :as stest]
   [clj-http.client :as client]
   [ruse.util :as u]
   [ruse.pg :as pg]
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

(defn path-exists? [path dirs]
  (let [pmap (pg/destructure-path path)]
    (or (u/member path dirs)
        (case (pg/what-is-path? path)
          "schema" true
          "table" (u/member (:table pmap) (pg/mget-tables (:schema pmap)))
          "record" (u/member (:pk pmap) (pg/mget-rows (:schema pmap) (:table pmap)))
          "other" false))))

(defn getattr-directory [{:keys [path stat]}]
  (doto stat
    (-> .-st_mode (.set (bit-or FileStat/S_IFDIR (read-string "0755"))))
    (-> .-st_nlink (.set 2))))

;; TODO: May need some type of fake size reporting if this causes tons of issues.
;; Maybe we can query postgres for just the storage size and not the data hm...
;; Worst case, at least we're limiting to ~50 records per dir.
(defn getattr-file [{:keys [path stat]}]
  (let [pmap (pg/destructure-path path)
        row (pg/mget-row (:schema pmap) (:table pmap) (:pk pmap))
        bytes (-> row .getBytes)
        length (count bytes)]
    (doto stat
      (-> .-st_mode (.set (bit-or FileStat/S_IFREG (read-string "0444"))))
      (-> .-st_nlink (.set 1))
      (-> .-st_size (.set length)))))

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
  (prn "Path was: " path)
  (cond
    ;; Show our available schemas
    (= "/" path) (readdir-list-files-base m (pg/mget-schemas) [])

    (= "/custom" path) (readdir-list-files-base m [] (pg/get-custom-rows-files))

    ;; List the tables under the schema
    (pg/is-schema? path)
    (do
      (prn "WAS SCHEMA?" path)
      (readdir-list-files-base
       m
       (pg/mget-tables (:schema (pg/destructure-path path)))
       []))

    ;; List the records under the path
    (pg/is-table? path)
    ;; :else
    (do
      (readdir-list-files-base
       m []
       (pg/mget-rows (:schema (pg/destructure-path path))
                     (:table (pg/destructure-path path)))))
    ))

(defn read-fuse-file [{:keys [path buf size offset fi]}]
  (let [pmap (pg/destructure-path path)
        row (pg/mget-row (:schema pmap)
                          (:table pmap)
                          (:pk pmap))]
    (let [
          bytes
          (byte-array
           (concat
            (-> row .getBytes)
            (byte-array [0x3])))        ; add a byte at end of file
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
      bytes-to-read)))

(defn set-root-dirs []
  (->> (conj (conj (map #(str "/" %) (pg/mget-schemas)) "/") "/custom")
       (into [])))

(def root-dirs (set-root-dirs))

(defn is-valid-dir? [path root-dirs]
  (or (u/member path root-dirs)
      (pg/is-table? path)))

(defn fuse-custom-mount []
  (proxy [FuseStubFS] []
    (getattr
      [path stat]                       ; string , jni
      (if (not (path-exists? path root-dirs))
        (enoent-error)
        (cond
          (is-valid-dir? path root-dirs )
          (getattr-directory (u/lexical-ctx-map))

          (pg/is-record? path)
          (getattr-file (u/lexical-ctx-map))

          :else (enoent-error))))
    (readdir
      [path buf filt offset fi]
      ;; Here we choose what to list.
      (prn "In readdir")
      (if (not (is-valid-dir? path root-dirs))
        (enoent-error)
        (readdir-list-files (u/lexical-ctx-map))))
    (open
      [path fi]
      ;; Here we handle errors on opening
      (prn "In open: " path fi)
      (let [pmap (pg/destructure-path path)]
        (if (not (path-exists? path root-dirs))
          (enoent-error)
          0)))
    (read
      [path buf size offset fi]
      ;; Here we read the contents
      (prn "In read" path)
      (if
          (not (pg/what-is-path? path))
          (enoent-error)
          (read-fuse-file (u/lexical-ctx-map))))))

(def stub-atom (atom nil))

(defn mount-it! [dir]
  (let [stub (fuse-custom-mount)]
    (future
      (reset! stub-atom stub)
      ;; params: path blocking debug options
      (-> stub (.mount (u/string-to-path dir) true false (into-array String []))))
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
