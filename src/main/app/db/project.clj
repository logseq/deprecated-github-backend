(ns app.db.project
  (:refer-clojure :exclude [get update])
  (:require [toucan.db :as db]
            [clojure.java.jdbc :as jdbc]
            [honeysql.core :as h]
            [toucan.models :as model]
            [ring.util.response :as resp]
            [app.config :as config]
            [app.cookie :as cookie]
            [app.util :as util]
            [app.db.util]
            [app.db.user :as u]
            [app.db.repo :as repo]
            [app.db.page :as page]))

;; TODO: safe check
(model/defmodel Project :projects)

(extend (class Project)
  model/IModel
  (merge model/IModelDefaults
         {:types (constantly {:settings :json})}))

(defn get
  [project-id]
  (db/select-one Project :id project-id))

(defn belongs-to?
  [name user-id]
  (= user-id
     (db/select-one-field :user_id Project :name name)))

(defn insert
  [args]
  (cond
    (and
     (:user_id args) (:name args)
     (db/exists? Project {:name (:name args)}))
    (let [project-id (db/select-one-field :id Project :name (:name args))]
      (db/update! Project project-id
                  (dissoc args :user_id))
      (get project-id))

    (:user_id args)
    (db/insert! Project args)

    :else
    nil))

(defn get-user-projects
  [user-id]
  (when user-id
    (db/select Project
               :user_id user-id)))

(defn exists?
  [name]
  (db/exists? Project {:name name}))

(defn get-id-by-name
  [name]
  (db/select-one-field :id Project :name name))

(defn get-user-id-by-name
  [name]
  (db/select-one-field :user_id Project :name name))

(defn get-id-by-repo-id
  [repo-id]
  (db/select-one-field :id Project :repo_id repo-id))

(defn get-name-by-id
  [id]
  (db/select-one-field :name Project :id id))

(defn create-user-default-project
  [{:keys [id name] :as user}]
  (when-not (exists? name)
    (insert {:user_id id
             :name name})))

(defn get-project-info
  [project-id]
  (let [{:keys [name settings repo_id user_id]} (get project-id)
        user (-> (u/get user_id)
                 (select-keys [:name :avatar :website]))
        repo (repo/get repo_id)
        repo-users (and repo (repo/get-repo-users repo_id))]
    (cond->
     {:name name
      :settings settings
      :creator user
      :contributors (remove (fn [u] (= (:name u) (:name user)))
                            repo-users)}
      (:public repo)
      (assoc :url (:url repo)))))

(defn update
  [id m]
  (db/update! Project id m))

(defn get-project-pages
  [name]
  (when-let [project-id (get-id-by-name name)]
    (db/select [page/Page :permalink :title :published_at]
               :project_id project-id)))

(defn delete
  [id]
  (when id
    (db/delete! Project :id id)))