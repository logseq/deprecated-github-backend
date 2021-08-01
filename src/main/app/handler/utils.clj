(ns app.handler.utils
  (:require [app.result :as r]
            [app.http :as h]
            [app.db.project :as project]))

(defn login?
  [app-context]
  (let [user (:user app-context)]
    (if (map? user)
      (r/success user)
      (h/unauthorized))))

(defn permit-to-access-project?
  [user project-name]
  (if (project/belongs-to? project-name (:id user))
    (r/success)
    (h/forbidden)))