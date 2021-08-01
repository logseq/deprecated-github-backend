(ns app.spec
  (:require [clojure.spec.alpha :as s]
            [expound.alpha :as expound]
            [io.pedestal.log :as log]
            [app.config :as config]))

(when config/dev? (s/check-asserts true))

;;(set! s/*explain-out* expound/printer)

(defn validate
  "This function won't crash the current thread, just log error."
  [spec value]
  (when config/dev?
    (if (s/explain-data spec value)
      (let [error-message (expound/expound-str spec value)
             ex (ex-info "Error in validate" {})]
        (log/error :exception ex :spec/validate-failed error-message)
        false)
      true)))

(s/def :oauth/oauth_source keyword?)
(s/def :oauth/name string?)
(s/def :oauth/email string?)
(s/def :oauth/avatar string?)
(s/def :oauth/open_id string?)
