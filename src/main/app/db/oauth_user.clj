(ns app.db.oauth-user
  (:refer-clojure :exclude [get update])
  (:require [toucan.db :as db]
            [toucan.models :as model])
  (:require [app.db.util :as util]))

(model/defmodel OauthUser :oauth_users)

(def source->qualify-kw (partial util/generate-enum-keyword :oauth_source))

(defn source->pg-object
  [source]
  (-> (source->qualify-kw source)
      (util/kw->pg-enum)))

(extend (class OauthUser)
  model/IModel
  (merge model/IModelDefaults
    {:types (constantly {:oauth_source :enum})}))

(defn get
  [id]
  (db/select-one OauthUser :id id))

(defn get-by-user-id-from-github
  [user-id]
  (->
    (db/select OauthUser
      :user_id user-id
      :oauth_source (source->pg-object :github)
      {:order-by [[:created_at :desc]]})
    (first)))

(defn get-by-source-&-open-id
  [source open-id]
  (db/select-one OauthUser
    :oauth_source (source->pg-object source)
    :open_id open-id))

(defn get-github-auth
  [user-id]
  (db/select-one OauthUser
    :user_id user-id
    :oauth_source (source->pg-object :github)))

(defn insert
  [m]
  (let [m (clojure.core/update m :oauth_source source->qualify-kw)]
    (db/insert! OauthUser m)))

(defn update
  [id m]
  (let [m (if (:oauth_source m)
            (clojure.core/update m :oauth_source source->qualify-kw)
            m)
        m (assoc m :updated_at (util/sql-now))
        result (db/update! OauthUser id m)]
    (when result
      (get id))))
