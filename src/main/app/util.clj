(ns app.util
  (:require [clojure.string :as str]
            [clj-time
             [coerce :as tc]
             [core :as t]
             [format :as tf]]
            [app.config :as config]
            [ring.util.codec :as codec])
  (:import  [java.util UUID]
            [java.util TimerTask Timer]))
(defn uuid
  "Generate uuid."
  []
  (UUID/randomUUID))

(defn ->uuid
  [s]
  (if (uuid? s)
    s
    (UUID/fromString s)))

(defn update-if
  "Update m if k exists."
  [m k f]
  (if-let [v (get m k)]
    (assoc m k (f v))
    m))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defmacro doseq-indexed
  "loops over a set of values, binding index-sym to the 0-based index of each value"
  ([[val-sym values index-sym] & code]
   `(loop [vals# (seq ~values)
           ~index-sym (long 0)]
      (if vals#
        (let [~val-sym (first vals#)]
          ~@code
          (recur (next vals#) (inc ~index-sym)))
        nil))))

(defn indexed [coll] (map-indexed vector coll))

(defn set-timeout [f interval]
  (let [task (proxy [TimerTask] []
               (run [] (f)))
        timer (new Timer)]
    (.schedule timer task (long interval))
    timer))

;; http://yellerapp.com/posts/2014-12-11-14-race-condition-in-clojure-println.html
(defn safe-println [& more]
  (.write *out* (str (clojure.string/join " " more) "\n")))

(defn safe->int
  [s]
  (if (string? s)
    (Integer/parseInt s)
    s))

(defn remove-nils
  [m]
  (reduce (fn [acc [k v]] (if v (assoc acc k v)
                              acc))
          {} m))

(defn deep-merge [& maps]
  (apply merge-with (fn [& args]
                      (if (every? map? args)
                        (apply deep-merge args)
                        (last args)))
         maps))

;; TODO: add database-name
(defn attach-db-spec!
  [config]
  (let [db-uri (java.net.URI. (or
                               (System/getenv "DATABASE_URL")
                               "postgres://localhost:5432/logseq"))
        user-info (.getUserInfo db-uri)
        [username password] (if user-info
                              (clojure.string/split user-info #":"))
        custom {:username username
                :password password
                :server-name (.getHost db-uri)}]
    (update config :hikari-spec merge custom)))

(defn asset-uri
  [branch-name path]
  (cond
    config/dev?
    path

    config/staging?
    (if branch-name
      (format "%s/%s%s" config/asset-uri branch-name path)
      (format "%s/%s%s" config/asset-uri "master" path))

    :else
    (str config/asset-uri path)))

(defn ->sql-time
  [datetime]
  (^java.sql.Timestamp tc/to-sql-time datetime))

(defn sql-now
  []
  (->sql-time (t/now)))

(defn capitalize-all [s]
  (some->> (str/split s #" ")
           (map str/capitalize)
           (str/join " ")))

;; TODO: e.g. not working if s contains? "%20".
(defn url-encoded?
  [s]
  (try
    (not= (codec/url-decode s) s)
    (catch Exception _e
      false)))

(defmacro k-map
  "(let [a 1 b 2 c 3] (kv-map a b c))
  => {:a 1 :b 2 :c 3}"
  [& syms]
  `(zipmap (map keyword '~syms) (list ~@syms)))

(defn rand-str
  [len]
  (->> (repeatedly #(char (+ (rand 26) 65)))
    (take len)
    (apply str)))
