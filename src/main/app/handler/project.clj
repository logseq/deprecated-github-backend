(ns app.handler.project
  (:require [clojure.string :as string])
  (:require [app.db
             [project :as project]]
            [app.interceptors :as interceptors]
            [app.handler.page :as page-handler]
            [app.result :as r]
            [app.http :as h]
            [app.reserved-routes :as reserved]
            [app.db.repo :as repo]
            [app.handler.utils :as hu]))

(defn get-id-by-name [name]
  {:pre [(string? name)]}
  (if-let [project-id (project/get-id-by-name name)]
    (r/success project-id)
    (h/not-found)))

(defn validate-project-name?
  [project-name]
  (r/check-r
    (when (or (string/blank? project-name)
              (< (count (string/trim project-name)) 2))
      (h/bad-request "Project name should be at least 2 chars."))

    (when (reserved/reserved? project-name)
      (h/bad-request "Please change to another name."))

    (when (project/exists? project-name)
      (h/bad-request "Please change to another name."))

    (r/success)))

(defn delete-project
  [{:keys [app-context path-params] :as req}]
  (let [project-name (:name path-params)]
    (r/check-r
      (when (string/blank? project-name)
        (h/bad-request))
      (r/let-r
        [user (hu/login? app-context)
         project-id (get-id-by-name project-name)
         _ (hu/permit-to-access-project? user project-name)]
        (project/delete project-id)
        (interceptors/clear-user-cache (:id user))
        (page-handler/clear-project-cache! project-id)
        (h/success true)))))

(defn update-project
  [{:keys [app-context path-params body-params] :as req}]
  (let [origin-name (:name path-params)
        new-name (:name body-params)
        new-settings (:settings body-params)]
    (r/let-r [user (hu/login? app-context)
              project-id (get-id-by-name origin-name)]
      (r/check-r
        (hu/permit-to-access-project? user origin-name)

        (when (string? new-name)
          (validate-project-name? new-name))

        (let [project (cond-> {}
                        new-name
                        (assoc :name new-name)

                        new-settings
                        (assoc :settings new-name))]
          (project/update project-id project)
          (interceptors/clear-user-cache (:id user))
          (page-handler/clear-project-cache! project-id)
          (h/success true))))))

(defn create-project
  [{:keys [app-context body-params] :as req}]
  (let [project-name (:name body-params)]
   (r/let-r [user (hu/login? app-context)
             _ (validate-project-name? project-name)]
     (let [{:keys [repo settings]} body-params
           repo-id (if repo (:id (repo/get-by-user-id-and-url (:id user) repo)))
           result (project/insert (cond->
                                    {:user_id (:id user)
                                     :name project-name}
                                    repo-id
                                    (assoc :repo_id repo-id)
                                    settings
                                    (assoc :settings settings)))
           body (let [result (select-keys result [:name :settings])]
                  (assoc result :repo repo))]
       (h/success 201 body)))))
