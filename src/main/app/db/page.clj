(ns app.db.page
  (:refer-clojure :exclude [get])
  (:require [toucan.db :as db]
            [clojure.java.jdbc :as jdbc]
            [honeysql.core :as h]
            [toucan.models :as model]
            [ring.util.response :as resp]
            [app.config :as config]
            [app.cookie :as cookie]
            [app.util :as util]
            [app.db.util]))

;; TODO: safe check
(model/defmodel Page :pages)

;; https://github.com/metabase/toucan/issues/58
(extend (class Page)
  model/IModel
  (merge model/IModelDefaults
         {:types (constantly {:settings :json})}))

(defn get
  [page-id]
  (db/select-one Page :id page-id))

(defn get-by-user-id-and-permalink
  [user-id permalink]
  (db/select-one Page
                 :user_id user-id
                 :permalink permalink))

(defn get-by-project-id-and-permalink
  [project-id permalink]
  (db/select-one Page
                 :project_id project-id
                 :permalink permalink))

(defn get-all-by-project-id
  [project-id]
  (db/select Page :project_id project-id))

(defn belongs-to?
  [permalink user-id]
  (= user-id
     (db/select-one-field :user_id Page :permalink permalink)))

;; TODO: upsert
(defn insert
  [args]
  (cond
    (and
     (:user_id args) (:permalink args)
     (db/exists? Page (select-keys args [:user_id :permalink])))
    (let [page-id (db/select-one-field :id Page :permalink (:permalink args))]
      (db/update! Page page-id
                  (-> args
                      (dissoc :permalink :user_id)
                      (assoc :updated_at (util/sql-now))))
      (get page-id))

    :else
    (db/insert! Page args)))

(defn delete
  [id]
  (when id
    (db/delete! Page :id id)))

(defn get-project-pages
  [project-id]
  (db/select [Page :permalink :title :published_at]
             :project_id project-id))

(defn get-project-pages-all
  [project-id]
  (db/select Page :project_id project-id))

(defn get-all-tags
  [project-id]
  (->> (jdbc/query (db/connection)
                   ["SELECT settings -> 'tags' AS tags FROM pages where project_id = ?;"
                    project-id])
       (map :tags)
       (apply concat)
       (frequencies)
       (sort-by val)
       reverse))

(defn get-pages-by-tag
  [project-id tag]
  (->> (jdbc/query (db/connection)
                   ["SELECT permalink, title, published_at, settings->'tags' as tags FROM pages where project_id = ? order by published_at desc;"
                    project-id])
       (filter #(contains? (set (:tags %)) tag))))
