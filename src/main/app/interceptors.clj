(ns app.interceptors
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.helpers :as helpers]
            [io.pedestal.http.ring-middlewares :as ring-middlewares]
            [app.cookie :as cookie]
            [app.jwt :as jwt]
            [app.db.user :as u]
            [app.db.repo :as repo]
            [app.db.project :as project]
            [app.util :as util]
            [ring.util.response :as resp]
            [app.config :as config]
            [app.interceptor.etag :as etag]
            [app.interceptor.gzip :as gzip]
            [app.slack :as slack]))

;; move to a separate handler helper
(defn logout
  [_req]
  (-> (resp/redirect config/website-uri)
      (assoc :cookies cookie/delete-token)))

(defonce users-cache
  (atom {}))

(defn clear-user-cache
  [user-id]
  (swap! users-cache dissoc user-id))

(def cookie-interceptor
  (interceptor
   {:name ::cookie-authenticate
    :enter
    (fn [{:keys [request] :as context}]
      (let [tokens (cookie/get-tokens request)]
        (if tokens
          (let [{:keys [access-token refresh-token]} tokens]
            (if access-token
              (try
                (let [{:keys [id access-token]} (jwt/unsign access-token)
                      uid (some-> id util/->uuid)
                      user (when-let [user (u/get uid)]
                             (let [repos (map #(select-keys % [:id :url :branch :installation_id])
                                           (repo/get-user-repos uid))
                                   projects (map
                                              (fn [project]
                                                (let [repo-id (:repo_id project)
                                                      project (select-keys project [:name :description])]
                                                  (assoc project :repo
                                                         (:url (first (filter (fn [repo] (= (:id repo) repo-id)) repos))))))
                                              (project/get-user-projects uid))
                                   user (assoc user
                                               :repos repos
                                               :projects projects)
                                   user (assoc user :preferred_format
                                               (:preferred_format user))]
                               (swap! users-cache assoc uid user)
                               user))]
                  (if (:id user)
                    (-> context
                        (assoc-in [:request :app-context :uid] uid)
                        (assoc-in [:request :app-context :user] (assoc user :access-token access-token)))
                    context))
                (catch Exception e       ; token is expired
                  (when (= (ex-data e)
                           {:type :validation, :cause :exp})
                    (slack/debug "Jwt token expired: " {:token access-token
                                                        :e e})
                    (assoc context :response (logout request)))))))
          context)))}))

(defn cache-control
  [max-age]
  {:name ::cache-control
   :leave (fn [{:keys [response] :as context}]
            (let [{:keys [status headers]} response
                  response (if (= 200 status)
                             (let [val (if (= :no-cache max-age)
                                         "no-cache"
                                         (str "public, max-age=" max-age))]
                               (assoc-in response [:headers "Cache-Control"] val))
                             response)]
              (assoc context :response response)))})

(def etag-interceptor etag/etag-interceptor)
(def gzip-interceptor gzip/gzip-interceptor)
