(ns app.db.util
  (:require [toucan.models :as model]
            [cheshire.core :as json]
            [clojure.java.jdbc :as j]
            [clojure.string :as s]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.coerce :as tc])
  (:import org.postgresql.util.PGobject))

(defn- clj->pg-json
  [value]
  (doto (PGobject.)
    (.setType "json")
    (.setValue (json/generate-string value))))

(model/add-type! :json
                 :in clj->pg-json
                 :out identity)

(defn generate-enum-keyword
  [type value]
  {:pre [(every? keyword? [type value])]}
  (->> (map name [type value])
    (str/join "/")
    (keyword)))

(defn kw->pg-enum [kw]
  (let [type (-> (namespace kw)
                 (s/replace "-" "_"))
        value (name kw)]
    (doto (PGobject.)
      (.setType type)
      (.setValue value))))

(model/add-type! :enum
  :in kw->pg-enum
  :out identity)

(extend-protocol j/IResultSetReadColumn
  PGobject
  (result-set-read-column [pgobj metadata idx]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "jsonb" (json/parse-string value true)
        "enum" value
        :else value))))


(defn sql-now
  []
  (tc/to-sql-time (t/now)))