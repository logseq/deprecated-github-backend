(ns app.slack
  (:require
   [clj-http.client :as client]
   [cheshire.core :refer [generate-string]]
   [clojure.string :as str]
   [taoensso.timbre :as t]
   [app.config :as config]))

;; Replaced httpkit with clj-http because of a dokku weird bug Unsupported record version Unknown-0.0
(defn slack-escape [message]
  "Escape message according to slack formatting spec."
  (str/escape message {\< "&lt;" \> "&gt;" \& "&amp;"}))

(defn send-msg
  ([hook msg]
   (send-msg hook msg nil))
  ([hook msg {:keys [specific-user]}]
   (when-not config/dev?
     (let [body {"text" (slack-escape msg)}
           body (if specific-user
                  (assoc body "channel" (str "@" specific-user))
                  body)]
       (client/post hook
                    {:headers {"content-type" "application/json"
                               "accept" "application/json"}
                     :body (generate-string body)})))))

(def rules {:new-user (get-in config/config [:slack :webhooks :new-user])
            :exception (get-in config/config [:slack :webhooks :exception])
            ;; :api-latency {:webhook (:slack-hook config)}
            })

(defn at-prefix
  [msg]
  (str "[Logseq] @channel\n" msg))

(defn notify
  [channel msg]
  (let [msg (at-prefix msg)]
    (future (send-msg (get rules channel) msg))))

(defn new-exception
  [msg]
  (notify :exception msg))

(defn new-user
  [msg]
  (notify :new-user msg))

(defn to-string
  [& messages]
  (let [messages (cons (format "Environment: %s" (if config/production? "Production" "Dev")) messages)]
    (->> (map
          #(if (isa? (class %) Exception)
             (str % "\n\n"
                  (apply str (interpose "\n" (.getStackTrace %))))
             (str %))
          messages)
         (interpose "\n")
         (apply str))))

(defmacro error
  "Log errors, then push to slack,
  first argument could be throwable."
  [& messages]
  `(do
     (t/error ~@messages)
     (new-exception (to-string ~@messages))))

(defmacro debug
  "Debug and then push to slack"
  [& messages]
  `(do
     (t/debug ~@messages)
     (new-exception (to-string ~@messages))))
