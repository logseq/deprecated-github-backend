(ns app.components.services
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.nrepl
             [server :as repl]
             [transport :as nrepl-t]]))

(defn- start-nrepl
  []
  (repl/start-server :port 31415 :transport-fn nrepl-t/tty))

(defrecord Services [service-map repl]
  component/Lifecycle
  (start [this]
    (let [repl (start-nrepl)]
      (assoc this :repl repl)))
  (stop [this]
    (repl/stop-server repl)))

(defn new-services []
  (map->Services {}))
