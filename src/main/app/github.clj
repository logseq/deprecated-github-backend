(ns app.github
  (:require [app.config :as config]
            [buddy.sign.jwt :as jwt]
            [clj-http.client :as http]
            [buddy.core.keys :as ks]
            [clj-social.core :as social]
            [cheshire.core :as json]
            [app.slack :as slack]
            [clojure.string :as string]
            [app.util :as util]
            [app.db.oauth-user :as oauth-user]
            [clj-time.core :as t])
  (:import [java.util Date]))

;; There're several problems which need to be resolved before released:
;; 1. What if the user suspended the installation, I suspect the token will not working anymore
;; A: the app prompt the user to install the app
;; 2. What if the user uninstalled the app
;; A: same answer as above :)
;; 3. Is one refresh token corresponds to all the tokens for the user and all the organization repos?
;; A: TBD
;; 4. Do user and organizations have different installation ids
;; A: Yes

(def api-url "https://api.github.com")
(def app-id (get-in config/config [:oauth :github-app :app-id]))
(def app-key (get-in config/config [:oauth :github-app :app-key]))
(def app-secret (get-in config/config [:oauth :github-app :app-secret]))
(def app-name (if config/dev?
                (get-in config/config [:oauth :github-app :app-name])
                "logseq"))
(def redirect-uri (get-in config/config [:oauth :github-app :redirect-uri]))
(def private-key-pem (get-in config/config [:oauth :github-app :app-private-key-pem]))
(def app-install-uri (str "https://github.com/apps/" app-name "/installations/new"))

(defn jwt-sign
  []
  (let [now (Date.)
        now-30s (Date. ^long (-> now (.getTime) (- (* 1000 30))))
        now+8m  (Date. ^long (-> now (.getTime) (+ (* 1000 60 8))))]
    (jwt/sign {:iss app-id
               :iat now-30s
               :exp now+8m}
              (ks/private-key private-key-pem)
              {:alg :rs256})))

;; Get app information
(defn app-auth
  []
  (http/get (str api-url "/app")
            {:headers {"authorization" (str "Bearer " (jwt-sign))
                       "accept" "application/vnd.github.machine-man-preview+json"}}))

(defn exchange-token
  [code]
  (-> (http/post "https://github.com/login/oauth/access_token"
                 {:headers {"accept" "application/json"}
                  :query-params {:client_id app-key
                                 :client_secret app-secret
                                 :code code
                                 :redirect_uri redirect-uri}})
      :body
      (json/parse-string true)))

(defn get-user
  [access-token]
  (-> (http/get (str api-url "/user")
                {:headers {"authorization" (str "token " access-token)
                           "accept" "application/json"}})
      :body
      (json/parse-string true)))

;; /installation/repositories
(defn get-installation-repos
  [installation-token]
  (->>
   (->
    (http/get (str api-url "/installation/repositories")
              {:headers {"authorization" (str "Bearer " installation-token)
                         "accept" "application/vnd.github.machine-man-preview+json"}})
    :body
    (json/parse-string true)
    :repositories)
   (map :html_url)
   (distinct)))

;; /app/installations/{installation_id}/access_tokens
;; https://docs.github.com/en/rest/reference/apps#create-an-installation-access-token-for-an-app
;; curl \
;; -X POST \
;; -H "Accept: application/vnd.github.machine-man-preview+json" \
;; TODO: better error handler, e.g. github api problem, invalid installation id due to revoking
;; TODO: refresh token
(defn get-installation-access-token
  [installation-id]
  (try
    (let [result (:body (http/post (str api-url "/app/installations/" installation-id "/access_tokens")
                                   {:headers {"authorization" (str "Bearer " (jwt-sign))
                                              "accept" "application/vnd.github.machine-man-preview+json"}}))]
      (json/parse-string result true))
    (catch Exception e
      ;; (slack/error (str "Get installation access token for " installation-id) e)
      nil)))

(defn- get-git-owner-and-repo
  [repo-url]
  (take-last 2 (string/split repo-url #"/")))

(defn get-repo-permission
  [username installation-id repo-url]
  (when-let [token (:token (get-installation-access-token installation-id))]
    (let [[owner repo-name] (get-git-owner-and-repo repo-url)
          url (str api-url
                   (format "/repos/%s/%s/collaborators/%s/permission"
                           owner
                           repo-name
                           username))]
      (->
       (http/get url
                 {:headers {"authorization" (str "Bearer " token)
                            "accept" "application/vnd.github.machine-man-preview+json"}})
       :body
       (json/parse-string true)))))

(defn check-permission?
  [owner installation-id repo]
  (if-let [github-name (-> (oauth-user/get-by-user-id-from-github (:id owner)) :name)]
    (try
      (when (contains? #{"admin" "write"}
                       (:permission (get-repo-permission github-name installation-id repo)))
        true)
      (catch Exception e
        (slack/error (format "Someone(without the access) wants to visit: %s"
                             (util/k-map github-name installation-id repo))
                     e)
        false))
    (do (slack/error (format "Can't find github name. Args: %s"
                             (util/k-map owner installation-id repo)))
        false)))

(defn get-repo-installation-id
  [user repo]
  (try
    (let [[owner repo-name] (take-last 2 (string/split repo #"\/"))]
      (when (and owner repo-name)
        (let [{:keys [id app_id] :as result}
              (->
               (http/get (str api-url (format "/repos/%s/%s/installation" owner repo-name))
                         {:headers {"authorization" (str "Bearer " (jwt-sign))
                                    "accept" "application/vnd.github.machine-man-preview+json"}})
               :body
               (json/parse-string true))
              id (and (= (str app-id) (str app_id))
                      (str id))]
          (when id
            (when (check-permission? user id repo)
              id)))))
    (catch Exception e
      nil)))

;; GET /repos/:owner/:repo/contents
(defn repo-empty?
  [token repo-url]
  (try
    (let [[owner repo-name] (get-git-owner-and-repo repo-url)
          url (str api-url
                   (format "/repos/%s/%s/contents"
                           owner
                           repo-name))]
      (http/get url
                {:headers {"authorization" (str "Bearer " token)
                           "accept" "application/vnd.github.machine-man-preview+json"}})
      false)
    (catch Exception e
      (let [message (:message (json/parse-string (:body (ex-data e)) true))]
        (= message "This repository is empty.")))))

(defn get-repo-default-branch
  [token repo-url]
  (let [[owner repo-name] (get-git-owner-and-repo repo-url)
        url (str api-url
                 (format "/repos/%s/%s"
                         owner
                         repo-name))]
    (try
      (-> (http/get url
                    {:headers {"authorization" (str "Bearer " token)
                               "accept" "application/vnd.github.machine-man-preview+json"}})
          :body
          (json/parse-string true)
          :default_branch)
      (catch Exception e
        (slack/debug {:url url}
                     e)
        "master"))))

(defonce latest-release (atom nil))
(defonce last-fetched-at (atom nil))

;; GET /repos/:owner/:repo/releases/latest
(defn get-logseq-latest-release
  []
  (if (and @latest-release
           @last-fetched-at
           (t/before? (t/now) (t/plus @last-fetched-at (t/minutes 1))))
    @latest-release
    (try
      (let [result (-> (http/get (str api-url "/repos/logseq/logseq/releases/latest")
                                 {:headers {"accept" "application/vnd.github.machine-man-preview+json"}})
                       :body
                       (json/parse-string true))
            release (->> (map :browser_download_url (:assets result))
                         (filter #(not (string/ends-with? % ".zip"))))
            release (->> (for [asset release]
                           (let [k (cond
                                     (string/includes? asset "darwin-arm64")
                                     "Mac-M1"
                                     (string/includes? asset "darwin-x64")
                                     "Mac"
                                     (string/includes? asset "linux-x64")
                                     "Linux"
                                     (string/includes? asset "win-x64")
                                     "Windows")]
                             [k asset]))
                         (into {}))]
        (when (seq release)
          (reset! latest-release release)
          (reset! last-fetched-at (t/now)))
        release)
      (catch Exception e
        nil))))

(comment
  (def installation-id "11503160")
  )
