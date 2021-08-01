(ns app.routes
  (:require [reitit.swagger :as swagger]
            [clj-social.core :as social]
            [app.config :as config]
            [app.util :as util]
            [app.db.user :as u]
            [app.db.repo :as repo]
            [app.db.project :as project]
            [app.db.page :as page]
            [app.handler.page :as page-handler]
            [app.handler.user :as user-handler]
            [ring.util.response :as resp]
            [ring.util.codec :as codec]
            [app.views.home :as home]
            [app.interceptors :as interceptors]
            [io.pedestal.http.ring-middlewares :as ring-middlewares]
            [ring.util.response :as response]
            [app.s3 :as s3]
            [app.aws :as aws]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [lambdaisland.uri :as uri]
            [app.slack :as slack]
            [app.handler.rss :as rss]
            [app.reserved-routes :as reserved]
            [app.github :as github]
            [app.result :as r]
            [app.handler.project :as h-project]
            [app.handler.auth :as h-auth]
            [app.http :as h]
            [app.db.oauth-user :as oauth-user]
            [schema.utils :as utils]
            [app.cookie :as cookie]))

;; TODO: spec validate, authorization (owner?)

(defn default-handler
  [{:keys [app-context query-params] :as req}]
  (let [github-authed? (-> (get-in app-context [:user :id])
                           (oauth-user/get-github-auth)
                           boolean)
        user (some-> (:user app-context)
                     (assoc :github-authed? github-authed?))
        git-branch-name (:b query-params)
        spa? (or (:spa query-params)
                 (get-in req [:cookies "spa" :value])
                 user)
        body (home/home user spa? git-branch-name)]
    (cond->
      {:status 200
       :body body
       :headers {"Content-Type" "text/html"
                 "X-Frame-Options" "SAMEORIGIN"}}
      (or spa? user)
      (assoc :cookies cookie/spa))))

(def project-check
  {:name ::project-check
   :enter (fn [{:keys [request response] :as ctx}]
            (let [{:keys [path-params]} request
                  project (:project path-params)
                  resp (if (project/exists? project)
                         response
                         (default-handler request))]
              (assoc ctx :response resp)))})

(defn ->tags
  [tags]
  (vec (distinct (apply concat (map #(string/split % #",\s?") tags)))))

(def routes
  [["/check.txt"
    {:get {:no-doc true
           :handler (fn [_]
                      {:status 200
                       :body "dokku-check"})}}]
   ["/swagger.json"
    {:get {:no-doc true
           :swagger {:info {:title "logseq api"
                            :description "with pedestal & reitit-http"}}
           :handler (swagger/create-swagger-handler)}}]

   ["/"
    {:get {:no-doc true
           :handler default-handler}}]

   ["/logout"
    {:get {:no-doc true
           :handler interceptors/logout}}]
   ["/login"
    {:swagger {:tags ["Login"]}}

    ["/github"
     {:get {:summary "Login with github"
            :handler
            (fn [req]
              (let [{:keys [app-key app-secret redirect-uri]} (get-in config/config [:oauth :github-app])
                    social (social/make-social :github app-key app-secret
                                               redirect-uri
                                               :scope "user:email")
                    url (social/getAuthorizationUrl social)]
                (resp/redirect url)))}}]
    ;; ["/google"
    ;;  {:get {:summary "Login with google"
    ;;         :handler
    ;;         (fn [req]
    ;;           (let [{:keys [app-key app-secret redirect-uri]} (get-in config/config [:oauth :google])
    ;;                 social (social/make-social :google app-key app-secret
    ;;                          redirect-uri
    ;;                          :scope "email profile openid")
    ;;                 url (social/getAuthorizationUrl social)]
    ;;             (resp/redirect url)))}}]
    ]
   ["/auth"
    {:swagger {:tags ["Authenticate"]}}

    ["/github"
     {:get {:summary "Authenticate with github"
            :handler h-auth/auth-github
            }}]
    ;; ["/google"
    ;;  {:get {:summary "Authenticate with google"
    ;;         :handler (fn [req]
    ;;                    (-> (h-auth/auth-google req)
    ;;                        (h/result->http-map)))}}]
    ]

   ["/static/" {:swagger {:no-doc true}
                :interceptors [(when-not config/dev?
                                 (interceptors/cache-control 315360000))]}
    ["*path" {:get {:no-doc true
                    :handler (fn [req]
                               (let [{{:keys [path]} :path-params} req]
                                 (response/resource-response (str "static/" path))))}}]]

   ["/js/" {:swagger {:no-doc true}
            :interceptors [(when-not config/dev?
                             (interceptors/cache-control 315360000))]}
    ["*path" {:get {:no-doc true
                    :handler (fn [req]
                               (let [{{:keys [path]} :path-params} req]
                                 (response/resource-response (str "js/" path))))}}]]

   ["/api/v1"
    ["/account"
     {:delete {:handler (fn [req]
                          (let [resp (user-handler/delete! req)]
                            (prn {:req req
                                  :resp resp})
                            (h/result->http-map resp)))}}]

    ["/refresh_github_token"
     {:post {:handler
             (fn [{:keys [app-context body-params] :as req}]
               (if-not (:uid app-context)
                 {:status 401
                  :body {:message "Unauthorized."}}
                 (let [user (:user app-context)
                       {:keys [repos]} body-params
                       repos (->> (map :url repos)
                               (remove nil?))]
                   (cond
                     (seq repos)
                     (let [installation-ids (->> (doall
                                                   (map (fn [repo]
                                                          (when-let [id (github/get-repo-installation-id user repo)]
                                                            (repo/update-repo-installation-id! repo id)
                                                            id)) repos))
                                              (remove nil?))]
                       (if (seq installation-ids)
                         (do
                           (interceptors/clear-user-cache (:id user))
                           {:status 200
                            :body (mapv
                                    (fn [installation-id]
                                      (let [{:keys [token expires_at]} (github/get-installation-access-token installation-id)]
                                        {:installation_id installation-id
                                         :token token
                                         :expires_at expires_at}))
                                    installation-ids)})
                         (do
                           (repo/clear-user-repos! (:id user))
                           {:status 200
                            :body []})))
                     :else
                     {:status 400
                      :body {:message "Invalid installation-ids"}}))))}}]

    ["/email"
     {:post {:summary "Update email"
             :handler
             (fn [{:keys [app-context body-params] :as req}]
               (let [email (:email body-params)]
                 (if (not (string/blank? email))
                   (let [user (:user app-context)
                         [ok-bad result] (u/update-email (:id user) email)]
                     (interceptors/clear-user-cache (:id user))
                     (if (= :ok ok-bad)
                       {:status 200
                        :body {:message "Update successfully"}}
                       {:status 400
                        :body {:message "Email address already exists!"}}))
                   {:status 400
                    :body {:message "email is required!"}})))}}]

    ["/cors_proxy"
     {:post {:summary "Update cors_proxy"
             :handler
             (fn [{:keys [app-context body-params] :as req}]
               (if-let [user (:user app-context)]
                 (if-let [cors-proxy (:cors-proxy body-params)]
                   (let [_result (u/update (:id user) {:cors_proxy cors-proxy})]
                     (interceptors/clear-user-cache (:id user))
                     {:status 200
                      :body {:message "Update successfully"}})
                   {:status 400
                    :body {:message "cors_proxy is required!"}})
                 {:status 400
                  :body {:message "Please login first."}}))}}]

    ["/set_preferred_format"
     {:post {:summary "Update preferred format"
             :handler
             (fn [{:keys [app-context body-params] :as req}]
               (let [preferred_format (string/lower-case (:preferred_format body-params))]
                 (if (contains? #{"org" "markdown"} preferred_format)
                   (let [user (:user app-context)
                         result (u/update (:id user) {:preferred_format preferred_format})]
                     (interceptors/clear-user-cache (:id user))
                     {:status 200
                      :body {:message "Update successfully"}})
                   {:status 400
                    :body {:message "Only org and markdown are supported!"}})))}}]
    ["/set_preferred_workflow"
     {:post {:summary "Update preferred workflow"
             :handler
             (fn [{:keys [app-context body-params] :as req}]
               (let [preferred_workflow (string/lower-case (:preferred_workflow body-params))]
                 (if (contains? #{"todo" "now"} preferred_workflow)
                   (let [user (:user app-context)
                         result (u/update (:id user) {:preferred_workflow preferred_workflow})]
                     (interceptors/clear-user-cache (:id user))
                     {:status 200
                      :body {:message "Update successfully"}})
                   {:status 400
                    :body {:message "Only todo and now are supported!"}})))}}]
    ["/repos"
     {:post {:summary "Add a repo"
             :handler
             (fn [{:keys [app-context body-params] :as req}]
               (let [user (:user app-context)]
                 (if (:id user)
                   (let [repo (:url body-params)]
                     (if-let [installation-id (github/get-repo-installation-id user repo)]
                       (let [result (repo/insert {:user_id (:id user)
                                                  :url (:url body-params)
                                                  :branch (:branch body-params)
                                                  :installation_id installation-id})]
                         {:status 201
                          :body result})
                       ;; install app
                       (let [result (repo/insert {:user_id (:id user)
                                                  :url (:url body-params)
                                                  :branch (:branch body-params)})]
                         {:status 201
                          :body result})))
                   {:status 401
                    :body "Invalid request"})))}}]

    ["/repos/:id"
     {:post {:summary "Update a repo's url"
             :handler
             (fn [{:keys [app-context params body-params] :as req}]
               (let [user (:user app-context)
                     args (select-keys body-params [:url :branch])
                     args (util/remove-nils args)
                     result (when (seq args)
                              (repo/update (:id params) args))]
                 (interceptors/clear-user-cache (:id user))

                 {:status 200
                  :body result}))}
      :delete {:summary "Delete a repo"
               :handler
               (fn [{:keys [app-context path-params] :as req}]
                 (let [user (:user app-context)
                       id (util/->uuid (:id path-params))]
                   (if (and user id (repo/belongs-to? id (:id user)))
                     (do
                       (repo/delete id)
                       (interceptors/clear-user-cache (:id user))
                       {:status 200
                        :body {:result true}})
                     {:status 401
                      :body "Invalid request"})))}}]

    ["/projects"
     {:post {:summary "Add a project"
             :handler (fn [req]
                        (-> (h-project/create-project req)
                            (h/result->http-map)))}}]
    ["/projects/:name"
     [""
      {:post {:summary "Update a project's settings"
              :handler (fn [req]
                         (-> (h-project/update-project req)
                             (h/result->http-map)))}

       :delete {:summary "delete a project"
                :handler (fn [req]
                           (-> (h-project/delete-project req)
                               (h/result->http-map)))}}]
     ["/pages"
      {:get {:summary "Get pages that belong the project."
             :handler (fn [req]
                        (-> (page-handler/get-page-list req)
                            (h/result->http-map)))}}]]

    ["/pages"
     {:post {:summary "Add a new page"
             :handler (fn [req]
                        (-> (page-handler/create-page req)
                            (h/result->old-http-map)))
             }}]

    ["/:project/:permalink"
     {:delete {:summary "Delete a page"
               :handler (fn [req]
                          (-> (page-handler/delete-page req)
                              (h/result->http-map)))}}]

    ;; TODO: limit usage
    ["/presigned_url"
     {:post {:summary "Request a aws s3 presigned url."
             :handler
             (fn [{:keys [app-context body-params params] :as req}]
               (let [{:keys [filename mime-type]} body-params
                     {:keys [access-key-id secret-access-key bucket]} (:aws config/config)
                     user (:user app-context)
                     presigned-url (s3/generate-presigned-url
                                    access-key-id
                                    secret-access-key
                                    bucket
                                    (str "/" (:id user) (util/uuid) filename))]
                 (if presigned-url
                   (let [result {:presigned-url presigned-url
                                 :s3-object-key (-> (:path (uri/uri presigned-url))
                                                    (string/replace (format "/%s/" bucket) ""))}]
                     {:status 201
                      :body result})
                   {:status 400
                    :body {:message "Something wrong"}})))}}]

    ;; TODO: track user images usage
    ["/signed_url"
     {:post {:summary "Request a aws cloudfront presigned url."
             :handler
             (fn [{:keys [app-context body-params params] :as req}]
               (let [{:keys [s3-object-key]} body-params
                     signed-url (aws/get-signed-url s3-object-key
                                                    ;; 100 years, oh my!
                                                    (* 60 24 365 100))]
                 (if signed-url
                   {:status 201
                    :body {:signed-url signed-url}}
                   {:status 400
                    :body {:message "Something wrong"}})))}}]]

   ["/:project" {:swagger {:no-doc true}
                 :interceptors [project-check
                                (when-not config/dev?
                                  (interceptors/cache-control 315360000))]}
    [""
     {:get {:handler
            (fn [{:keys [app-context path-params query-params] :as req}]
              (let [{:keys [project]} path-params
                    git-branch-name (:b query-params)]
                (if-let [project-id (project/get-id-by-name project)]
                  (let [html (page-handler/get-project-index-page project-id git-branch-name)]
                    {:status 200
                     :headers {"Content-Type" "text/html"}
                     :body html})
                  {:status 404
                   :body "Not found"})))}}]
    ["/latest.rss"
     {:get {:handler
            (fn [{:keys [app-context path-params] :as req}]
              (let [{:keys [project]} path-params]
                (if-let [project-id (project/get-id-by-name project)]
                  (rss/rss-page project project-id)
                  {:status 404
                   :body "Not found"})))}}]
    ["/tag/:tag"
     {:get {:handler
            (fn [{:keys [app-context path-params query-params] :as req}]
              (let [{:keys [project tag]} path-params
                    git-branch-name (:b query-params)]
                (if-let [project-id (project/get-id-by-name project)]
                  (let [html (page-handler/get-project-tag-page project-id tag git-branch-name)]
                    {:status 200
                     :headers {"Content-Type" "text/html"}
                     :body html})
                  {:status 404
                   :body "Not found"})))}}]
    ["/:permalink"
     {:get {:handler
            (fn [{:keys [app-context path-params query-params] :as req}]
              (let [{:keys [project permalink]} path-params
                    permalink (codec/url-encode permalink)
                    git-branch-name (:b query-params)]
                (if-let [project-id (project/get-id-by-name project)]
                  (if-let [page (page/get-by-project-id-and-permalink project-id permalink)]
                    (let [html (page-handler/get-page project-id project permalink page git-branch-name)]
                      {:status 200
                       :headers {"Content-Type" "text/html"}
                       :body html})
                    {:status 404
                     :body "Page not found"})
                  {:status 404
                   :body "Not found"})))}}]]])
