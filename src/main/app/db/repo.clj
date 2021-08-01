(ns app.db.repo
  (:refer-clojure :exclude [get update])
  (:require [toucan.db :as db]
            [toucan.models :as model]
            [ring.util.response :as resp]
            [app.config :as config]
            [app.cookie :as cookie]
            [clojure.java.jdbc :as j]
            [clojure.string :as string]))

(model/defmodel Repo :repos)

(defn get
  [id]
  (db/select-one Repo :id id))

(defn get-by-user-id-and-url
  [user-id url]
  (db/select-one Repo
                 :user_id user-id
                 :url url))

(defn insert
  [args]
  (cond
    (and
     (:user_id args) (:url args)
     (db/exists? Repo (select-keys args [:user_id :url])))
    (get-by-user-id-and-url (:user_id args) (:url args))

    :else
    (db/insert! Repo args)))

(defn get-user-repos
  [user-id]
  (db/select Repo :user_id user-id))

(defn delete
  [id]
  (when id
    (db/delete! Repo :id id)))

(defn update
  [id m]
  (db/update! Repo id m))

(defn update-branch-by-user-id-and-url
  [user-id url branch]
  (db/update-where! Repo {:user_id user-id
                          :url url}
    :branch branch))

(defn belongs-to?
  [repo-id user-id]
  (= user-id (:user_id (get repo-id))))

(defn get-repo-users
  [id]
  (let [url (:url (get id))]
    (j/query (db/connection)
             ["select u.name, u.avatar, u.website from repos r left join users u on r.user_id = u.id where r.url = ?"
              url])))

(defn update-installation-id!
  [installation-id repos]
  (when (and installation-id (seq repos))
    (j/execute! (db/connection)
                (format
                 "Update repos set installation_id = %s where url IN (%s)"
                 (format "'%s'" installation-id)
                 (string/join ", "
                              (map
                               (fn [repo]
                                 (format "'%s'" repo))
                               repos))))))

(defn update-repo-installation-id!
  [repo installation-id]
  (when (and repo installation-id)
    (j/update! (db/connection)
               :repos
               {:installation_id installation-id}
               ["url = ?" repo])))

(defn clear-user-repos!
  [user-id]
  (db/delete! Repo :user_id user-id))

(defn get-user-main-branch-repos
  [user-id]
  (db/select Repo
    :user_id user-id
    :branch "main"))
