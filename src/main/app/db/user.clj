(ns app.db.user
  (:refer-clojure :exclude [get update])
  (:require [toucan.db :as db]
            [toucan.models :as model]
            [ring.util.response :as resp]
            [app.config :as config]
            [app.cookie :as cookie]
            [app.jwt :as jwt]
            [app.db.refresh-token :as refresh-token]))

(model/defmodel User :users)

(defn get
  [id]
  (db/select-one User :id id))

(defn get-by-name
  [name]
  (db/select-one User :name name))

(defn insert
  [{:keys [email] :as args}]
  (if (nil? email)
    (db/insert! User args)
    (if-let [user (db/select-one User :email email)]
      user
      (db/insert! User args))))

(defn delete
  [id]
  (db/delete! User :id id))

(defn update
  [id m]
  (when id
    (db/update! User id m)))

(defn update-email
  [id email]
  (when id
    (cond
      (= (:email (get id)) email)
      [:ok true]

      (db/exists? User {:email email})
      [:bad :email-address-exists]

      :else
      [:ok (db/update! User id {:email email})])))

(defn generate-tokens
  [user-id]
  (cookie/token-cookie
   {:access-token  (jwt/sign {:id user-id})
    :refresh-token (refresh-token/create user-id)}))
