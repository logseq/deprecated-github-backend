(ns app.system
  (:require [io.pedestal.http :as server]
            [reitit.ring :as ring]
            [reitit.http :as http]
            [reitit.coercion.spec]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.http.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.dev :as dev]
            [reitit.http.spec :as spec]
            [spec-tools.spell :as spell]
            [reitit.pedestal :as pedestal]
            [clojure.core.async :as a]
            [muuntaja.core :as m]
            [com.stuartsierra.component :as component]
            [app.components.http :as component-http]
            [app.components.hikari :as hikari]
            [app.components.services :as services]
            [app.routes :as routes]
            [app.config :as config]
            [io.pedestal.http.ring-middlewares :as ring-middlewares]
            [app.interceptors :as interceptors]
            [app.slack :as slack]))

(def router
  (pedestal/routing-interceptor
   (http/router
    routes/routes

    {;:reitit.interceptor/transform dev/print-context-diffs ;; pretty context diffs
     ;;:validate spec/validate ;; enable spec validation for route data
     ;;:reitit.spec/wrap spell/closed ;; strict top-level validation
     ;; :exception pretty/exception
     :data {:coercion reitit.coercion.spec/coercion
            :muuntaja m/instance
            :interceptors [;; swagger feature
                           swagger/swagger-feature
                           ;; query-params & form-params
                           (parameters/parameters-interceptor)
                           ;; content-negotiation
                           (muuntaja/format-negotiate-interceptor)
                           ;; encoding response body
                           (muuntaja/format-response-interceptor)
                           ;; exception handling
                           (exception/exception-interceptor (assoc
                                                             exception/default-handlers
                                                             :reitit.http.interceptors.exception/default
                                                             (fn [^Exception e _]
                                                               (slack/error e)
                                                               {:status 500
                                                                :body {:type "exception"
                                                                       :class (.getName (.getClass e))}})))

                           ;; decoding request body
                           (muuntaja/format-request-interceptor)
                           ;; coercing response bodys
                           (coercion/coerce-response-interceptor)
                           ;; coercing request parameters
                           (coercion/coerce-request-interceptor)]}
     :conflicts nil})

   ;; optional default ring handler (if no routes have matched)
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/swagger"
      :config {:validatorUrl nil
               :operationsSorter "alpha"}})
    (ring/create-default-handler
     {:not-found routes/default-handler}))))

(defn merge-interceptors-map
  [system-map interceptors]
  (update system-map :io.pedestal.http/interceptors
          (fn [old]
            (vec (concat interceptors old)))))

(defn new-system
  [{:keys [env port hikari-spec] :as config}]
  (let [port (if (string? port)
               (Integer/parseInt port)
               port)
        service-map (-> {:env env
                         ::server/type :jetty
                         ::server/port port
                         ::server/host "0.0.0.0"
                         ::server/join? false
                         ;; no pedestal routes
                         ::server/routes []
                         ;; allow serving the swagger-ui styles & scripts from self
                         ;; ::server/secure-headers {:content-security-policy-settings
                         ;;                          {:default-src "'self'"
                         ;;                           :style-src "'self' 'unsafe-inline'"
                         ;;                           :script-src "'self' 'unsafe-inline'"}}
                         ::server/secure-headers {:content-security-policy-settings {:object-src "'none'"}}}
                        (server/default-interceptors)
                        ;; use the reitit router
                        (pedestal/replace-last-interceptor router))
        service-map (let [current-dir (System/getProperty "user.dir")]
                      (merge-interceptors-map
                       service-map
                       [ring-middlewares/cookies
                        server/html-body
                        interceptors/cookie-interceptor
                        interceptors/etag-interceptor
                        interceptors/gzip-interceptor
                        (ring-middlewares/content-type)
                        (ring-middlewares/file current-dir)]))
        ;; service-map (if config/dev? (server/dev-interceptors service-map) service-map)
        ]
    (println "Server is running on port " port "!")
    (component/system-map :service-map service-map
                          :hikari (hikari/new-hikari-cp hikari-spec)
                          :http
                          (component/using
                           (component-http/new-server)
                           [:service-map])
                          :services
                          (component/using
                            (services/new-services)
                            [:hikari :service-map]))))
