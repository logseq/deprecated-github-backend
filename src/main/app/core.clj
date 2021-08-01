(ns app.core
  (:require [app.config :as config]
            [app.system :as system]
            [app.util :as util]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]
            [com.stuartsierra.component :as component])
  (:gen-class))

(defn set-logger!
  [log-path]
  (timbre/merge-config! (cond->
                         {:level :info
                          :appenders {:spit (appenders/spit-appender {:fname log-path})}}
                          config/production?
                          (assoc :output-fn (partial timbre/default-output-fn {:stacktrace-fonts {}})))))

(defn start []
  (System/setProperty "https.protocols" "TLSv1.2,TLSv1.1,SSLv3")
  (set-logger! (:log-path config/config))

  (let [system (system/new-system (util/attach-db-spec! config/config))]
    (component/start system)))

(defn -main [& args]
  (start))
