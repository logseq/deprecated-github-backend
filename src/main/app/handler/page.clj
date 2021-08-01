(ns app.handler.page
  (:require [hiccup.page :as html]
            [hiccup.util :as hiccup-util]
            [hickory.core :as hickory]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clj-time.format :as tf]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [clojure.core.cache :as cache]
            [ring.util.codec :as codec])
  (:require [app.db.page :as page]
            [app.db.project :as project]
            [app.config :as config]
            [app.util :as util]
            [app.result :as r]
            [app.interceptors :as interceptors]
            [app.http :as h]
            [app.handler.utils :as hu]
            [app.db.repo :as repo]))

(defn sql-date->str
  [sql-date]
  (let [date-time (tc/to-date-time sql-date)]
    (tf/unparse (tf/formatter "MMM dd, yyyy") date-time)))

(defn full-sql-date->str
  [sql-date]
  (let [date-time (tc/to-date-time sql-date)]
    (tf/unparse (tf/formatter "yyyy-MM-dd HH:mm:ss") date-time)))

(def all-pages-cache
  (atom (cache/ttl-cache-factory {} :ttl (* 3600 12)))) ; 12 hours

(defn clear-project-cache!
  [project-id]
  (swap! all-pages-cache dissoc project-id))

(defn render-hiccup-or-html
  [hiccup-or-html]
  (if (string? hiccup-or-html)
    (hiccup-util/raw-string hiccup-or-html)
    (walk/postwalk
     (fn [f]
       (cond
         (and (vector? f) (empty? f))
         nil

         (and (vector? f) (string? (first f)))
         (update f 0 keyword)

         :else
         f))
     hiccup-or-html)))

(defn page-tags
  [project-id]
  (let [tags (take 10 (page/get-all-tags project-id))]
    (when (seq tags)
      [:ul
       (for [[tag _count] tags]
         [:li
          [:a.tag {:href (str "tag/" tag)}
           (str "#" tag)]])])))

(defn subscribe-button
  []
  [:a.btn-subscribe
   {:target "_blank",
    :href "latest.rss"
    :style "margin-bottom: 10px"}
   [:svg
    {:viewbox "0 0 800 800"}
    [:path
     {:d
      "M493 652H392c0-134-111-244-244-244V307c189 0 345 156 345 345zm71 0c0-228-188-416-416-416V132c285 0 520 235 520 520z"}]
    [:circle {:r "71", :cy "581", :cx "219"}]]
   " Subscribe"])

(defn back
  [project]
  [:a.back {:href (str "/" project)
            :title (str "Back to " project)}
   [:svg
    {:fill "currentColor", :viewbox "0 0 24 24", :height "36", :width "36"}
    [:path
     {:d
      "M9.70711 16.7071C9.31658 17.0976 8.68342 17.0976 8.29289 16.7071L2.29289 10.7071C1.90237 10.3166 1.90237 9.68342 2.29289 9.29289L8.29289 3.29289C8.68342 2.90237 9.31658 2.90237 9.70711 3.29289C10.0976 3.68342 10.0976 4.31658 9.70711 4.70711L5.41421 9H17C17.5523 9 18 9.44772 18 10C18 10.5523 17.5523 11 17 11L5.41421 11L9.70711 15.2929C10.0976 15.6834 10.0976 16.3166 9.70711 16.7071Z",
      :clip-rule "evenodd",
      :fill-rule "evenodd"}]]])

(defn contributors-cp
  [contributors]
  (let [n (count contributors)]
    [:div.contributors.flex
     (for [{:keys [name avatar website]} contributors]
       (let [img [:img.avatar {:src avatar
                               :alt name
                               :class (if (= 1 n)
                                        "avatar-bigger"
                                        "")
                               :onerror "this.style.display='none'"}]]
         (if website
           [:a.avatar {:href website
                       :title name}
            img]
           img)))]))

(defn project-card
  [project-name description contributors]
  (let [blog? (contains? #{"blog" "logseq blog"} (string/lower-case project-name))]
    [:div.project-card.flex-1.row.space-between.items-center
     [:div
      [:h1.title (if blog?
                   "Logseq"
                   project-name)]
      (if blog?
        [:div.description
         [:p "A local-first knowledge base which can sync using Github."]
         [:ul
          [:li "Twitter: " [:a {:href "https://twitter.com/logseq"}
                            "@logseq"]]
          [:li "Github: " [:a {:href "https://github.com/logseq"}
                           "@logseq"]]]]
        (when description
          (render-hiccup-or-html description)))
      (subscribe-button)]
     (if blog?
       [:img.avatar.avatar-bigger {:src "https://logseq.com/static/img/logo.png"
                                   :onerror "this.style.display='none'"}]
       (contributors-cp contributors))]))

(defn build-project-index-page
  [project-id git-branch-name]
  (when-let [project (project/get-name-by-id project-id)]
    (let [{:keys [settings creator contributors]} (project/get-project-info project-id)
          contributors (cons creator contributors)
          {:keys [alias description header footer custom-css]} (:settings settings)
          pages (->> (page/get-project-pages project-id)
                     (sort-by :published_at)
                     (reverse))
          project-name (if-not (string/blank? alias) alias project)]
      (html/html5
       {:lang "en"}
       [:head
        [:meta {:http-equiv "content-type"
                :content "text/html;charset=UTF-8"}]
        [:meta {:name "viewport"
                :content "minimum-scale=1, initial-scale=1, width=device-width, shrink-to-fit=no"}]
        [:base {:href (str config/website-uri "/" project "/")}]
        [:title project-name]
        [:meta {:content "A local-first knowledge base.", :property "og:title"}]
        [:meta
         {:content
          "A local-first knowledge base which can be synced using Git.",
          :property "og:description"}]
        [:meta
         {:content "https://asset.logseq.com/static/img/logo.png",
          :property "og:image"}]
        [:link {:type "text/css"
                :href (util/asset-uri git-branch-name "/static/public.css")
                :rel "stylesheet"}]
        [:link
         {:href (util/asset-uri git-branch-name "/static/img/logo.png"),
          :type "image/png",
          :rel "shortcut icon"}]
        (when-not (string/blank? custom-css)
          [:style custom-css])
        [:link {:href "latest.rss"
                :rel "alternate"
                :type "application/rss+xml"
                :title (str "Latest posts of " project)}]
        [:body
         [:div.pub-page.index
          [:div.post
           (when header
             (render-hiccup-or-html header))
           (project-card project-name description contributors)

           (if (seq pages)
             (let [pages (group-by
                          (fn [page] (-> (:published_at page)
                                         (tc/to-date-time)
                                         (t/year)))
                          pages)]
               [:ul
                (for [[year pages] pages]
                  [:li
                   [:b year]
                   [:ul
                    (for [{:keys [permalink title published_at]} pages]
                      [:li
                       [:a {:href permalink}
                        (util/capitalize-all title)]
                       [:span.date (let [dt (tc/to-date-time published_at)]
                                     (str (t/month dt) "/" (t/day dt)))]])]])]))

           (page-tags project-id)

           (when footer
             (render-hiccup-or-html footer))]]]]))))

(defn get-project-index-page
  [project-id git-branch-name]
  (str
   (if-let [html (get-in @all-pages-cache [project-id ::index])]
     html
     (let [html (build-project-index-page project-id git-branch-name)]
       (swap! all-pages-cache assoc-in [project-id ::index] html)
       html))))

(defn build-project-tag-page
  [project-id tag git-branch-name]
  (when-let [project (project/get-name-by-id project-id)]
    (let [pages (page/get-pages-by-tag project-id tag)]
      (html/html5
       [:head
        [:meta {:http-equiv "content-type"
                :content "text/html;charset=UTF-8"}]
        [:meta
         {:content
          "minimum-scale=1, initial-scale=1, width=device-width, shrink-to-fit=no",
          :name "viewport"}]
        [:meta {:content (str "Tag " tag), :property "og:title"}]
        [:meta
         {:content "https://asset.logseq.com/static/img/logo.png",
          :property "og:image"}]
        [:base {:href (str config/website-uri "/" project "/")}]
        [:title (str "Tag " tag)]
        [:link {:type "text/css"
                :href (util/asset-uri git-branch-name "/static/public.css")
                :rel "stylesheet"}]
        [:link
         {:href (util/asset-uri git-branch-name "/static/img/logo.png"),
          :type "image/png",
          :rel "shortcut icon"}]
        [:body
         [:div.pub-page
          [:div.post.tags-page
           (back project)
           [:h1.title
            (str "#" tag)]
           (if (seq pages)
             [:ul
              (for [{:keys [permalink title published_at]} pages]
                [:li
                 [:a {:href permalink}
                  (util/capitalize-all title)]
                 [:span.date (let [dt (tc/to-date-time published_at)]
                               (str (t/month dt) "/" (t/day dt)))]])])]]]]))))

(defn get-project-tag-page
  [project-id tag git-branch-name]
  (str
   (if-let [html (get-in @all-pages-cache [project-id ::tag tag])]
     html
     (let [html (build-project-tag-page project-id tag git-branch-name)]
       (swap! all-pages-cache assoc-in [project-id ::tag tag] html)
       html))))

;; TODO: might be slow
(defn- unescape-html
  [s]
  (-> s
      (.replace "&amp;" "&")
      (.replace "&lt;" "<")
      (.replace "&gt;" ">")
      (.replace "&quot;" "\"")))

(defonce html-unsafe-tags
  #{:applet :base :basefont :frame :frameset :head :iframe :isindex
    :link :meta :object :param :script :style :title})

(defn remove-unsafe-tags!
  [hiccup]
  (walk/postwalk
   (fn [x]
     (cond
       (and (vector? x)
            (contains? html-unsafe-tags (first x)))
       nil

       (string? x)
       (unescape-html x)

       :else
       x))
   hiccup))

(defn html->hiccup
  [html]
  (->> (hickory/parse-fragment html)
       (map (comp remove-unsafe-tags! hickory/as-hiccup))
       (remove nil?)))

(defn tags-cp
  [tags]
  [:p
   (for [tag tags]
     [:a {:href (str "tag/" tag)
          :style "margin-right: 10px"}
      (str "#" tag)])])

(defn build-specific-page
  [project permalink {:keys [title html published_at updated_at] :as page} git-branch-name]
  (when-let [project-id (project/get-id-by-name project)]
    (let [{:keys [settings creator contributors]} (project/get-project-info project-id)
          contributors (cons creator contributors)
          {:keys [alias description header footer custom-css twitter]} (:settings settings)
          project-name (if-not (string/blank? alias) alias project)
          {:keys [slide highlight latex]} (:settings page)]
      (html/html5
       [:html {:prefix "og: http://ogp.me/ns#"
               "xmlns:og" "http://opengraphprotocol.org/schema/"}
        [:head
         [:meta {:http-equiv "content-type"
                 :content "text/html;charset=UTF-8"}]
         [:meta
          {:content
           "minimum-scale=1, initial-scale=1, width=device-width, shrink-to-fit=no",
           :name "viewport"}]
         [:title (util/capitalize-all title)]
         [:meta {:name "author"
                 :content project}]
         [:meta {:property "og:title"
                 :content (util/capitalize-all title)}]
         [:meta
          {:content "https://asset.logseq.com/static/img/logo.png",
           :property "og:image"}]
         ;; TODO:
         ;; [:meta {:property "og:description"
         ;;         :content ""}]
         [:meta {:property "og:url"
                 :content (str "https://logseq.com/" project "/" permalink)}]
         [:meta {:property "og:type"
                 :content "article"}]
         [:meta {:property "og:site_name"
                 :content "logseq.com"}]
         [:meta {:property "article:published_time"
                 :content (str published_at)}]
         [:meta {:name "twitter:card"
                 :content "summary"}]
         [:meta {:name "twitter:title"
                 :content title}]
         ;; TODO:
         ;; [:meta {:property "twitter:description"
         ;;         :content ""}]
         (when twitter
           [:meta {:name "twitter:creator"
                   :content (str "@" twitter)}])

         [:link {:type "text/css"
                 :href (util/asset-uri git-branch-name "/static/public.css")
                 :rel "stylesheet"}]
         [:link
          {:href (util/asset-uri git-branch-name "/static/img/logo.png"),
           :type "image/png",
           :rel "shortcut icon"}]
         (when-not (string/blank? custom-css)
           [:style custom-css])
         [:link {:href "latest.rss"
                 :rel "alternate"
                 :type "application/rss+xml"
                 :title (str "Latest posts on " project)}]
         (when slide
           [:link {:type "text/css"
                   :href (util/asset-uri git-branch-name "/static/css/reveal.min.css")
                   :rel "stylesheet"}])
         (when slide
           [:link {:type "text/css"
                   :href (util/asset-uri git-branch-name "/static/css/reveal_black.min.css")
                   :rel "stylesheet"}])
         (when highlight
           [:link {:type "text/css"
                   :href (util/asset-uri git-branch-name "/static/css/highlight.css")
                   :rel "stylesheet"}])
         (when latex
           [:link {:type "text/css"
                   :href (util/asset-uri git-branch-name "/static/css/katex.min.css")
                   :rel "stylesheet"}])]
        [:body
         (if slide
           (html->hiccup html)
           [:div.pub-page
            [:div.post.article
             (if header
               (render-hiccup-or-html header)
               (back project))
             [:h1.title (util/capitalize-all title)]
             (let [tags (get-in page [:settings :tags])]
               (when (seq tags)
                 (tags-cp tags)))

             (html->hiccup html)
             [:p.footer
              [:span {:title (str "Last modified at: " (full-sql-date->str updated_at))}
               (sql-date->str published_at)]]

             (if footer
               (render-hiccup-or-html footer)
               (project-card project-name description contributors))]])
         (when slide
           [:script {:defer true
                     :src (util/asset-uri git-branch-name "/static/js/reveal.min.js")
                     :onload "Reveal.initialize({ transition: 'slide' });"}])
         (when highlight
           [:script {:defer true
                     :src (util/asset-uri git-branch-name "/static/js/highlight.min.js")
                     :onload "hljs.initHighlightingOnLoad();"}])
         (when latex
           [:script {:defer true
                     :src "https://cdn.jsdelivr.net/npm/katex@0.12.0/dist/katex.min.js"
                     :integrity "sha384-g7c+Jr9ZivxKLnZTDUhnkOnsh30B4H0rpLUpJ4jAIKs4fnJI+sEnkvrMWph2EDg4"
                     :crossorigin "anonymous"}])
         (when latex
           [:script {:defer true
                     :src "https://cdn.jsdelivr.net/npm/katex@0.12.0/dist/contrib/auto-render.min.js"
                     :integrity "sha384-mll67QQFJfxn0IYznZYonOWZ644AWYC+Pt2cHqMaRhXVrursRwvLnLaebdGIlYNa"
                     :crossorigin "anonymous"
                     :onload "renderMathInElement(document.body);"}])]]))))

(defn get-page
  [project-id project permalink page git-branch-name]
  (str
   (if-let [html (get-in @all-pages-cache [project-id permalink])]
     html
     (let [html (build-specific-page project permalink page git-branch-name)]
       (swap! all-pages-cache assoc-in [project-id permalink] html)
       html))))

(defn get-project-id-by-name
  [project-name]
  (let [project-id (and (string? project-name) (project/get-id-by-name project-name))]
   (if project-id
     (r/success project-id)
     (h/not-found "Project not found."))))

(defn get-page-by-project-id-and-permalink
  [project-id permalink]
  (if-let [page (page/get-by-project-id-and-permalink project-id permalink)]
    (r/success page)
    (h/not-found "Page note found.")))

(defn permit-user-to-access-page?
  [user page]
  (if (page/belongs-to? (:permalink page) (:id user))
    (r/success)
    (h/forbidden)))

(defn delete-page
  [{:keys [app-context path-params] :as req}]
  (let [project-name (:project path-params)]
    (r/check-r
      (when-not (and (string? (:permalink path-params))
                     project-name)
        (h/bad-request))
      (r/let-r [user (hu/login? app-context)
                permalink (codec/url-encode (:permalink path-params))
                project-id (get-project-id-by-name project-name)
                page (get-page-by-project-id-and-permalink project-id permalink)
                _ (permit-user-to-access-page? user page)]
        (page/delete (:id page))
        (interceptors/clear-user-cache (:id user))
        (clear-project-cache! project-id)
        (h/success true)))))

(defn get-page-list
  [{:keys [app-context path-params] :as req}]
  (r/let-r [project-name (:name path-params)
            user (hu/login? app-context)
            project-id (get-project-id-by-name project-name)
            _ (hu/permit-to-access-project? user project-name)
            pages (page/get-all-by-project-id project-id)]
    (h/success pages)))

(defn ->tags
  [tags]
  (vec (distinct (apply concat (map #(string/split % #",\s?") tags)))))

(defn create-page
  [{:keys [app-context body-params] :as req}]
  (r/let-r [user (hu/login? app-context)
            user-id (:id user)
            {:keys [permalink title html settings project]} body-params
            project-id (get-project-id-by-name project)]
    (r/check-r
      (hu/permit-to-access-project? user project)

      (when (or (string/blank? title)
                (string/blank? html))
        (h/bad-request))

      (let [permalink (if (string/blank? permalink)
                        (codec/url-encode title)
                        (if (util/url-encoded? permalink)
                          permalink
                          (codec/url-encode permalink)))
            settings (update settings :tags ->tags)
            result (page/insert
                     {:user_id user-id
                      :project_id project-id
                      :permalink permalink
                      :title title
                      :html html
                      :settings settings})]
        (clear-project-cache! project-id)
        (when-let [old-permalink (:old_permalink settings)]
          (let [page (page/get-by-user-id-and-permalink user-id old-permalink)]
            (page/delete (:id page))))
        (h/success 201 result)))))
