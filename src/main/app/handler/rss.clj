(ns app.handler.rss
  (:require [app.db.page :as page]
            [app.db.user :as u]
            [hiccup.page :as html]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clj-time.format :as tf]
            [clojure.string :as string]
            [app.config :as config]
            [app.util :as util]
            [clj-rss.core :as rss]))

(defn ->rss
  [project pages]
  (for [{:keys [title html permalink settings published_at]} pages]
    {:title title
     :description (format "<![CDATA[ %s ]]>" html)
     :link (str config/website-uri "/" project "/" permalink)
     :category (string/join ", " (:tags settings))
     :pubDate (tc/to-date published_at)}))

(defn rss-page
  [project project-id]
  (let [pages (page/get-project-pages-all project-id)]
    {:status 200
     :body (rss/channel-xml
            {:title project
             :link (str config/website-uri "/" project)
             :description (str "Latest posts from " project)}
            (->rss project pages))
     :headers {"Content-Type" "application/rss+xml; charset=utf-8"}}))
