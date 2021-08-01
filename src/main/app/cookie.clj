(ns app.cookie
  (:require [buddy.sign.compact :as buddy]
            [app.util :as util]
            [app.config :as config]
            [app.slack :as slack]))

(defn sign [token]
  (buddy/sign token (:cookie-secret config/config)))

(defn unsign [cookie]
  (buddy/unsign cookie (:cookie-secret config/config)))

;; domain path expires
(defn token-cookie [value & {:keys [max-age path]
                             :or {path "/"
                                  max-age (* (* 3600 24) 30)}}]
  (let [dev? config/dev?
        xsrf-token (str (util/uuid))
        domain (if-not dev?
                 config/cookie-domain
                 "")
        secure (if-not dev?
                 true
                 false)]
    {"x" (cond->
          {:value   (sign value)
           :max-age max-age
           :http-only true
           :path path
           :secure secure
            ;; :same-site "Strict"
           }
           domain
           (assoc :domain domain))
     "xsrf-token" (cond->
                   {:value xsrf-token
                    :max-age max-age
                    :http-only true
                    :path "/"
                    :secure secure
                     ;; :same-site "Strict"
                    }
                    domain
                    (assoc :domain domain))}))

(def delete-token
  (let [domain (if-not config/dev?
                 config/cookie-domain
                 "")
        delete-value {:value ""
                      :path "/"
                      :expires "Thu, 01 Jan 1970 00:00:00 GMT"
                      :http-only true
                      :max-age 0
                      :domain domain}]
    {"x" delete-value
     "xsrf-token" delete-value
     "spa" delete-value}))

(def spa
  (let [domain (if-not config/dev?
                 config/cookie-domain
                 "")
        max-age (* (* 3600 24) 60)]
    {"spa" {:value "true"
            :path "/"
            :max-age max-age
            :http-only true
            :domain domain}}))

(defn get-tokens [req]
  (when-let [x (get-in req [:cookies "x" :value])]
    (try
      (unsign x)
      (catch Exception e
        (slack/debug "Cookie exception: "
                     {:token x
                      :e e})))))
