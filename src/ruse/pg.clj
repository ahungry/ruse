(ns ruse.pg
  (:require
   [clojure.repl :refer :all]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.test.alpha :as stest]
   [ruse.util :as u]
   [ruse.rc :as rc]
   [clojure.java.jdbc :as j]
   )
  (:import
   )
  (:gen-class)
  )

(defn pg-db []
  (:pg (rc/get-rc)))

(defn q-get-schemas []
  (j/query
   (pg-db)
   ["select distinct(table_schema)
from information_schema.tables
WHERE table_schema NOT IN ('pg_catalog', 'pg_statistic', 'information_schema') "]
   ))

(defn get-schemas []
  (->> (q-get-schemas)
      (map :table_schema)))

(def mget-schemas (memoize get-schemas))

(defn q-get-tables [schema]
  (j/query
   (pg-db)
   ["select distinct(table_name)
from information_schema.tables
WHERE table_schema = ? " schema]
   ))

(defn get-tables [schema]
  (->> (q-get-tables schema)
      (map :table_name)))

(def mget-tables (memoize get-tables))

;; FIXME: Use at your own risk, this is NOT injection proof.
;; TODO: Make the pk and limit configurable
(defn q-get-rows [schema table]
  (j/query
   (pg-db)
   [(format "select CTID::text
from \"%s\".\"%s\" LIMIT 500" schema table)]))

(defn safe-ctid
  "Convert an unsafe (file system) version to a safe one.
  will translate (2,3) into 2_3."
  [ctid]
  (clojure.string/replace ctid #"\((.*?),(.*?)\)" "$1_$2"))

(defn unsafe-ctid
  "Convert an unsafe (file system) version to a safe one.
  will translate (2,3) into 2_3."
  [ctid]
  (let [tuple
        (clojure.string/split ctid #"_")]
    (format "(%s,%s)" (first tuple) (second tuple))))

(defn get-rows [schema table]
  (->> (q-get-rows schema table)
       (map :ctid)
       (map safe-ctid)))

(def mget-rows (memoize get-rows))

(defn q-get-row [schema table ctid]
  (j/query
   (pg-db)
   [(format "select *
from \"%s\".\"%s\"
WHERE ctid = ?::tid " schema table) ctid]))

(defn get-row [schema table safe-ctid]
  (with-out-str
    (-> (q-get-row schema table (unsafe-ctid safe-ctid))
        first
        clojure.pprint/pprint)))

(def mget-row (memoize get-row))

(defn destructure-path
  "P is the path for fuse, such as:

  /public/project/1"
  [p]
  (let [[_ schema table pk] (u/split-by-slash p)]
    (u/lexical-ctx-map)))

(defn is-record? [p]
  (:pk (destructure-path p)))

(defn is-table? [p]
  (and (not (is-record? p))
       (:table (destructure-path p))))

(defn is-schema? [p]
  (and (not (is-record? p))
       (not (is-table? p))
       (:schema (destructure-path p))))

(defn what-is-path? [p]
  (cond (is-record? p) "record"
        (is-table? p) "table"
        (is-schema? p) "schema"
        :else "other"))
