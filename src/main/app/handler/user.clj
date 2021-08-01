(ns app.handler.user
  (:require [app.db
             [user :as u]]
            [app.result :as r]
            [app.http :as h]
            [app.handler.utils :as hu]))

(defn delete!
  [{:keys [app-context path-params] :as req}]
  (r/let-r [user (hu/login? app-context)]
           (u/delete (:id user))
           (h/success true)))
