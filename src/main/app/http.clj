(ns app.http
  (:require [app.result :as r]
            [schema.utils :as utils]))

(defn- failed
  ([http-code error-message]
   (failed http-code error-message nil))
  ([http-code error-message opts]
   (let [m {:status http-code :result error-message}]
     (r/failed (merge m opts)))))

(defn redirect
  [url & {:as opts}]
  (let [data {:status 302
              :headers {"Location" url}
              :body ""}
        data (merge data opts)]
   (r/failed data)))

(defn bad-request
  ([]
   (bad-request "Bad Request."))
  ([message & {:as http-opts}]
   (failed 400 message http-opts)))

(defn unauthorized
  ([]
   (unauthorized "Unauthorized."))
  ([message & {:as http-opts}]
   (failed 401 message http-opts)))

(defn forbidden
  ([]
   (forbidden "Forbidden."))
  ([message & {:as http-opts}]
   (failed 403 message http-opts)))

(defn not-found
  ([]
   (not-found "Not Found."))
  ([message & {:as http-opts}]
   (failed 404 message http-opts)))

(defn internal-server-error
  ([]
   (internal-server-error "Internal Server Error."))
  ([message & {:as http-opts}]
   (failed 500 message http-opts)))

(defn success
  ([data]
   (let [status 200
         data {:status status :result data}]
     (r/success data)))
  ([status data & {:as http-opts}]
   (let [data (merge {:status status :result data} http-opts)]
     (r/success data))))

(defn result->http-map
  [result]
  {:pre [(r/result? result)]}
  (let [data (:data result)]
    (let [{:keys [status headers result cookies]} data
          body (if (r/failed? result)
                 {:message result}
                 {:result result})
          m {:status status :body body}]
      (utils/assoc-when m
        :headers headers
        :cookies cookies))))

(defn result->old-http-map
  "Deprecated: This function is only for compatibility with the old fashion result. New API should use result->http-map."
  [result]
  {:pre [(r/result? result)]}
  (let [data (:data result)]
    (let [{:keys [status headers result cookies]} data
          body (if (r/failed? result)
                 {:message result}
                 result)
          m {:status status :body body}]
      (utils/assoc-when m
        :headers headers
        :cookies cookies))))
