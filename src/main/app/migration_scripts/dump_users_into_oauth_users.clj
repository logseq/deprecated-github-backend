(ns app.migration-scripts.dump-users-into-oauth-users
  (:require [toucan.db :as db])
  (:require [app.db
             [user :as user]
             [oauth-user :as oauth-user]]
            [clojure.set :as set]))

(defn get-all-users
  []
  (db/select user/User))

(defn -main
  []
  (let [users (get-all-users)]
    (doseq [user users]
      (let [m (-> (select-keys user [:email :name :id :avatar])
                  (set/rename-keys {:id :user_id})
                  (assoc :oauth_source :github))]
        (prn "insert into oauth-user:" m)
        (try
          (oauth-user/insert m)
          (catch Throwable t
            (prn t)))))))
