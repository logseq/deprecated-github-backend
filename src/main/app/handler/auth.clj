(ns app.handler.auth
  (:require [ring.util.response :as resp]
            [clj-social.core :as social]
            [clojure.set :as set]
            [clojure.string :as str])
  (:require [app.db.user :as u]
            [app.config :as config]
            [app.result :as r]
            [app.http :as h]
            [app.github :as github]
            [app.db.repo :as repo]
            [app.slack :as slack]
            [app.db.oauth-user :as oauth-user]
            [schema.utils :as utils]
            [app.util :as util]
            [clojure.spec.alpha :as s]
            [app.spec :as spec]))

(defn- prepare-user-data
  [{:keys [name] :as oauth-user}]
  (let [m (select-keys oauth-user [:name :email :avatar])
        website (when (= "github" (:oauth_source oauth-user))
                  (str "https://github.com/" name))]
    (utils/assoc-when m :website website)))

(def ^:private user-appendix-length 6)

(s/def :oauth/data
  (s/keys
   :req-un [:oauth/oauth_source :oauth/open_id]
   :opt-un [:oauth/name :oauth/email :oauth/avatar]))

(defn get-from-oauth-user
  [{:keys [oauth_source open_id] :as oauth-data}]
  (when (and oauth_source open_id)
    (let [oauth-user (oauth-user/get-by-source-&-open-id oauth_source open_id)]
     (when oauth-user
       (oauth-user/update (:id oauth-user) oauth-data)
       oauth-user))))

(defn- get-user-by-oauth-data
  [oauth-data]
  (spec/validate :oauth/data oauth-data)
  (if-let [oauth-user (get-from-oauth-user oauth-data)]
    (if-let [user (u/get (:user_id oauth-user))]
      (r/success user)
      (h/internal-server-error "Can't find user."))
    (let [user-data (prepare-user-data oauth-data)
          user-data
          (if (u/get-by-name (:name user-data))
            (let [rand-appendix (util/rand-str user-appendix-length)]
              (update user-data :name str rand-appendix))
            user-data)
          user (u/insert user-data)]
      (do (-> (assoc oauth-data :user_id (:id user))
              (oauth-user/insert))
          (r/success user)))))

(defn- get-github-info
  [data]
  (try
    (let [{:keys [app-key app-secret redirect-uri]} (get-in config/config [:oauth :github-app])
          instance (social/make-social :github app-key app-secret redirect-uri
                                       :scope (str "user:email"))
          access-token (social/getAccessToken instance (:code data))
          info (social/getUserInfo instance access-token)]
      {:info info
       :access-token (.getAccessToken access-token)})
    (catch Exception e
      ;; TODO: figure out why code is not working here
      ;; Get github info error:
      ;; com.github.scribejava.core.exceptions.OAuthException: Response body is incorrect. Can't extract a 'access_token=([^&]+)' from this: 'error=bad_verification_code&error_description=The+code+passed+is+incorrect+or+expired.&error_uri=https%3A%2F%2Fdocs.github.com%2Fapps%2Fmanaging-oauth-apps%2Ftroubleshooting-oauth-app-access-token-request-errors%2F%23bad-verification-code'
      ;; (slack/error "Get github info error: " e)
      nil)))

(defn- get-user-from-github-oauth [data]
  ;; User might reject the grant
  (when-let [m (get-github-info data)]
    (let [{:keys [info]} m]
      (r/check-r
       (when (:message info)
         (h/bad-request))

       (let [oauth-data
             (-> (select-keys info [:avatar_url :login :email :id])
                 (set/rename-keys {:avatar_url :avatar
                                   :login :name
                                   :id :open_id})
                 (assoc :open_id (str (:id info))
                        :oauth_source :github))]
         (get-user-by-oauth-data oauth-data))))))

(defn auth-github
  [{:keys [params] :as req}]
  (if (= (:error params) "access_denied")
    (resp/redirect config/website-uri)
    (r/check-r
     (when-not (:code params)
       (h/bad-request))
     (r/let-r [user (get-user-from-github-oauth params)
               user-id (:id user)
               installation-id (:installation_id params)
               token (when installation-id
                       (:token (github/get-installation-access-token installation-id)))
               _ (when (and installation-id token)
                    ;; update repos installation_id and default branch
                   (let [repos (github/get-installation-repos token)]
                     (when (seq repos)
                       (repo/update-installation-id! installation-id repos)
                       (doseq [url repos]
                         (let [default-branch (github/get-repo-default-branch token url)
                               branch (cond
                                        (= default-branch "master")
                                        default-branch
                                        (github/repo-empty? token url)
                                        "master"
                                        :else
                                        default-branch)]
                           (repo/update-branch-by-user-id-and-url
                            user-id url branch))))))
               redirect-uri config/website-uri]
              (if-let [user-id (:id user)]
                (let [cookies (u/generate-tokens user-id)]
                  (-> (resp/redirect redirect-uri)
                      (assoc :cookies cookies)))
                (slack/error "Github auth failed: " (util/k-map params user)))))))

(defn- get-google-info
  [request-data]
  (try
    (let [{:keys [scope]} request-data
          {:keys [app-key app-secret redirect-uri]}
          (get-in config/config [:oauth :google])
          instance (social/make-social :google app-key app-secret
                                       redirect-uri
                                       :scope scope)
          access-token (social/getAccessToken instance (:code request-data))
          info (social/getUserInfo instance access-token)]
      {:info info
       :access-token (.getAccessToken access-token)})
    (catch Exception e
      (slack/error "Get google user info error: " e))))

(defn- clip-user-name
  [user-name]
  (if (string? user-name)
    (-> (str/trim user-name)
      (str/replace #"\s" "")
      (str/lower-case))
    user-name))

(defn- get-user-from-google-oauth
  [data]
  (let [{:keys [info] :as google-info} (get-google-info data)]
    (r/check-r
     (when-not info
       (slack/error "Google auth failed: " (util/k-map data google-info))
       (h/bad-request "OAuth Can't get user info."))
     (let [oauth-data (-> (select-keys info [:picture :given_name :email :sub])
                          (set/rename-keys {:picture :avatar
                                            :given_name :name
                                            :sub :open_id})
                          (update :name clip-user-name)
                          (assoc :oauth_source :google))]
       (get-user-by-oauth-data oauth-data)))))

(defn auth-google
  [{:keys [params] :as req}]
  (r/check-r
   (when-not (:code params)
     (h/redirect config/website-uri))
   (r/let-r [user (get-user-from-google-oauth params)
             redirect-uri config/website-uri
             user-id (:id user)
             cookies (u/generate-tokens user-id)]
            (h/redirect redirect-uri :cookies cookies))))
