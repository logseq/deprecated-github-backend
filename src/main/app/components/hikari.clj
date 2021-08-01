(ns app.components.hikari
  (:require [com.stuartsierra.component :as component]
            [hikari-cp.core :as hikari]
            [clojure.java.jdbc :as j]
            [toucan.db :as toucan]
            [app.db-migrate :as migrate]))

(defrecord Hikari [db-spec datasource]
  component/Lifecycle
  (start [component]
    (let [s (or datasource (hikari/make-datasource db-spec))]
      ;; migrate
      (migrate/migrate {:datasource s})
      ;; (try
      ;;   (migrate/migrate {:datasource s})
      ;;   (catch Exception e
      ;;     (prn "DB migrate failed: " e)))
      (toucan/set-default-db-connection! {:datasource s})
      (assoc component :datasource s)))
  (stop [component]
    (when datasource
      (hikari/close-datasource datasource))
    (assoc component :datasource nil)))

(defn new-hikari-cp [db-spec]
  (map->Hikari {:db-spec db-spec}))
