(ns app.views.home
  (:require [hiccup.page :as html]
            [app.config :as config]
            [app.util :as util]
            [cheshire.core :refer [generate-string]]
            [schema.utils :as utils]
            [app.github :as github]))

(def logo
  [:svg {:width "42" :height "36" :viewbox "0 0 145 135" :fill "none" :xmlns "http://www.w3.org/2000/svg"} [:g {:filter "url(#filter0_bd)"} [:ellipse {:rx "21.0711" :ry "13.2838" :transform "matrix(0.988865 -0.148815 0.0688008 0.99763 81.142 18.3005)" :fill "#80a1bd"}] [:ellipse {:rx "18.9009" :ry "21.6068" :transform "matrix(-0.495846 0.86841 -0.825718 -0.564084 27.213 28.6018)" :fill "#80a1bd"}] [:g {:filter "url(#filter1_d)"} [:ellipse {:rx "49.827" :ry "39.2324" :transform "matrix(0.987073 0.160274 -0.239143 0.970984 85.5314 87.3209)" :fill "#80a1bd"}]]] [:defs [:filter#filter0_bd {:x "3.05615" :y "0.679199" :width "136.554" :height "133.572" :filterunits "userSpaceOnUse" :color-interpolation-filters "sRGB"} [:feflood {:flood-opacity "0" :result "BackgroundImageFix"}] [:fegaussianblur {:in "BackgroundImage" :stddeviation "2"}] [:fecomposite {:in2 "SourceAlpha" :operator "in" :result "effect1_backgroundBlur"}] [:fecolormatrix {:in "SourceAlpha" :type "matrix" :values "0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0"}] [:feoffset {:dy "4"}] [:fegaussianblur {:stddeviation "2"}] [:fecolormatrix {:type "matrix" :values "0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.25 0"}] [:feblend {:mode "normal" :in2 "effect1_backgroundBlur" :result "effect2_dropShadow"}] [:feblend {:mode "normal" :in "SourceGraphic" :in2 "effect2_dropShadow" :result "shape"}]] [:filter#filter1_d {:x "31.4525" :y "48.3909" :width "108.158" :height "85.86" :filterunits "userSpaceOnUse" :color-interpolation-filters "sRGB"} [:feflood {:flood-opacity "0" :result "BackgroundImageFix"}] [:fecolormatrix {:in "SourceAlpha" :type "matrix" :values "0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0"}] [:feoffset {:dy "4"}] [:fegaussianblur {:stddeviation "2"}] [:fecolormatrix {:type "matrix" :values "0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.25 0"}] [:feblend {:mode "normal" :in2 "BackgroundImageFix" :result "effect1_dropShadow"}] [:feblend {:mode "normal" :in "SourceGraphic" :in2 "effect1_dropShadow" :result "shape"}]]]])

(def discord-logo
  [:svg.inline {:style {:margin-top -3}
                :width "32" :height "24" :viewbox "0 0 71 55" :fill "none" :xmlns "http://www.w3.org/2000/svg"} [:g {:clip-path "url(#clip0)"} [:path {:d "M60.1045 4.8978C55.5792 2.8214 50.7265 1.2916 45.6527 0.41542C45.5603 0.39851 45.468 0.440769 45.4204 0.525289C44.7963 1.6353 44.105 3.0834 43.6209 4.2216C38.1637 3.4046 32.7345 3.4046 27.3892 4.2216C26.905 3.0581 26.1886 1.6353 25.5617 0.525289C25.5141 0.443589 25.4218 0.40133 25.3294 0.41542C20.2584 1.2888 15.4057 2.8186 10.8776 4.8978C10.8384 4.9147 10.8048 4.9429 10.7825 4.9795C1.57795 18.7309 -0.943561 32.1443 0.293408 45.3914C0.299005 45.4562 0.335386 45.5182 0.385761 45.5576C6.45866 50.0174 12.3413 52.7249 18.1147 54.5195C18.2071 54.5477 18.305 54.5139 18.3638 54.4378C19.7295 52.5728 20.9469 50.6063 21.9907 48.5383C22.0523 48.4172 21.9935 48.2735 21.8676 48.2256C19.9366 47.4931 18.0979 46.6 16.3292 45.5858C16.1893 45.5041 16.1781 45.304 16.3068 45.2082C16.679 44.9293 17.0513 44.6391 17.4067 44.3461C17.471 44.2926 17.5606 44.2813 17.6362 44.3151C29.2558 49.6202 41.8354 49.6202 53.3179 44.3151C53.3935 44.2785 53.4831 44.2898 53.5502 44.3433C53.9057 44.6363 54.2779 44.9293 54.6529 45.2082C54.7816 45.304 54.7732 45.5041 54.6333 45.5858C52.8646 46.6197 51.0259 47.4931 49.0921 48.2228C48.9662 48.2707 48.9102 48.4172 48.9718 48.5383C50.038 50.6034 51.2554 52.5699 52.5959 54.435C52.6519 54.5139 52.7526 54.5477 52.845 54.5195C58.6464 52.7249 64.529 50.0174 70.6019 45.5576C70.6551 45.5182 70.6887 45.459 70.6943 45.3942C72.1747 30.0791 68.2147 16.7757 60.1968 4.9823C60.1772 4.9429 60.1437 4.9147 60.1045 4.8978ZM23.7259 37.3253C20.2276 37.3253 17.3451 34.1136 17.3451 30.1693C17.3451 26.225 20.1717 23.0133 23.7259 23.0133C27.308 23.0133 30.1626 26.2532 30.1066 30.1693C30.1066 34.1136 27.28 37.3253 23.7259 37.3253ZM47.3178 37.3253C43.8196 37.3253 40.9371 34.1136 40.9371 30.1693C40.9371 26.225 43.7636 23.0133 47.3178 23.0133C50.9 23.0133 53.7545 26.2532 53.6986 30.1693C53.6986 34.1136 50.9 37.3253 47.3178 37.3253Z" :fill "#ffffff"}]] [:defs [:clippath#clip0 [:rect {:width "71" :height "55" :fill "white"}]]]])

(defn login
  [device]
  [:div.dropdown
   [:a.dropbtn.cursor-pointer
    {:onclick "login()"
     :class "text-base font-medium text-white hover:text-gray-300"} "Log in"]
   [:div.dropdown-content {:id (str device "-dropdown")}
    ;; [:a {:class "text-base text-white hover:text-gray-300"
    ;;      :href (format "%s/login/google" config/website-uri)} "Login with Google"]
    [:a {:class "text-base text-white hover:text-gray-300"
         :href (format "%s/login/github" config/website-uri)} "Login with GitHub"]]])

(defn app-download
  []
  ;; https://github.com/logseq/logseq/releases/latest/download/package.zip
  )

(defn head
  [git-branch-name db-exists?]
  (let [css-href (if db-exists?
                   (util/asset-uri git-branch-name "/static/css/style.css")
                   "https://unpkg.com/tailwindcss@^2/dist/tailwind.min.css")]
    [:head
     [:meta {:charset "utf-8"}]
     [:meta
      {:content
       "minimum-scale=1, initial-scale=1, width=device-width, shrink-to-fit=no",
       :name "viewport"}]
     [:link {:type "text/css", :href css-href, :rel "stylesheet"}]
     (when-not db-exists?
       [:style ".videoWrapper {
  position: relative;
  padding-bottom: 56.25%; /* 16:9 */
  height: 0;
}
.videoWrapper iframe {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.dropdown {
  position: relative;
  display: inline-block;
}

.dropdown-content {
  display: none;
  position: absolute;
  min-width: 160px;
  overflow: auto;
  z-index: 1;
  margin-top: 1rem;
}

.dropdown-content a {
  text-decoration: none;
  display: block;
  padding: 4px 0;
}

.show {display: block;}
"])
     [:link
      {:href (util/asset-uri git-branch-name "/static/img/logo.png"),
       :type "image/png",
       :rel "shortcut icon"}]
     [:link
      {:href (util/asset-uri git-branch-name "/static/img/logo.png"),
       :sizes "192x192",
       :rel "shortcut icon"}]
     [:link
      {:href (util/asset-uri git-branch-name "/static/img/logo.png"),
       :rel "apple-touch-icon"}]

     ;; suggested by @denvey
     [:meta {:name "apple-mobile-web-app-title" :content "Logseq"}]
     [:meta {:name "apple-mobile-web-app-capable" :content "yes"}]
     [:meta {:name "apple-touch-fullscreen" :content "yes"}]
     [:meta {:name "apple-mobile-web-app-status-bar-style" :content "black-translucent"}]
     [:meta {:name "mobile-web-app-capable" :content "yes"}]

     [:meta {:content "summary", :name "twitter:card"}]
     [:meta
      {:content
       "A privacy-first, open-source platform for knowledge management and collaboration.",
       :name "twitter:description"}]
     [:meta {:content "@logseq", :name "twitter:site"}]
     [:meta {:content "A privacy-first, open-source knowledge base", :name "twitter:title"}]
     [:meta
      {:content "https://asset.logseq.com/static/img/logo.png",
       :name "twitter:image:src"}]
     [:meta
      {:content "A privacy-first, open-source platform for knowledge management and collaboration.", :name "twitter:image:alt"}]
     [:meta {:content "A privacy-first, open-source knowledge base", :property "og:title"}]
     [:meta {:content "site", :property "og:type"}]
     [:meta {:content "https://logseq.com", :property "og:url"}]
     [:meta
      {:content "https://asset.logseq.com/static/img/logo.png",
       :property "og:image"}]
     [:meta
      {:content
       "A privacy-first, open-source platform for knowledge management and collaboration.",
       :property "og:description"}]
     [:title "Logseq: A privacy-first, open-source knowledge base"]
     [:meta {:content "logseq", :property "og:site_name"}]
     [:meta
      {:description
       "A privacy-first, open-source platform for knowledge management and collaboration."}]]))

(defn db-exists-page
  [user git-branch-name]
  (let [dev? config/dev?
        ks [:name :email :avatar :repos :projects :preferred_format :preferred_workflow :cors_proxy :github-authed?]
        user-json (-> (when user (select-keys user ks))
                      (utils/assoc-when :git-branch git-branch-name)
                      (generate-string))]
    (html/html5
     (head git-branch-name true)
     [:body
      [:div#root]
      ;; FIXME: safe?
      [:script (str "window.user=" user-json ";")]
      [:script {:src "/js/magic_portal.js"}]
      (let [s (format "let worker = new Worker(\"%s\");
const portal = new MagicPortal(worker);
;(async () => {
  const git = await portal.get('git');
  window.git = git;
  const fs = await portal.get('fs');
  window.fs = fs;
  const pfs = await portal.get('pfs');
  window.pfs = pfs;
  const gitHttp = await portal.get('gitHttp');
  window.gitHttp = gitHttp;
  const workerThread = await portal.get('workerThread');
  window.workerThread = workerThread;
})();
"
                      "/js/worker.js?v=3")]
        [:script s])
      ;; (when dev?
      ;;   ;; react devtools
      ;;   [:script {:src "http://localhost:8097"}])

      [:script {:src (util/asset-uri git-branch-name "/static/js/main.js")}]
      [:script {:src (util/asset-uri git-branch-name "/static/js/highlight.min.js")}]
      [:script {:src (util/asset-uri git-branch-name "/static/js/interact.min.js")}]])))

(defn screenshot
  []
  [:div {:class "py-16 bg-gray-50 overflow-hidden lg:py-32"}
   [:h2 {:class "text-center text-3xl leading-8 font-extrabold tracking-tight text-gray-900 sm:text-4xl"}
    [:span "Your data is yours, forever!"]]
   [:p {:class "mt-4 max-w-3xl mx-auto text-center text-xl text-gray-500"}
    "No data lock-in, no proprietary formats, you can edit the same Markdown/Org-mode file with any tools at the same time."]
   [:div {:class "pt-16 bg-gray-50 overflow-hidden lg:pt-24"}
    [:div {:class "relative max-w-xl mx-auto px-4 sm:px-6 lg:px-8 lg:max-w-7xl"}
     [:a {:href "https://logseq.github.io"
          :title "Logseq documentation"}
      [:img.shadow-2xl.drop-shadow-2xl {:src "https://logseq.github.io/screenshots/1.png"}]]]]])

(defn feature-1
  []
  [:div
   [:svg {:class "hidden lg:block absolute left-full transform -translate-x-1/2 -translate-y-1/4", :width "404", :height "784", :fill "none", :viewbox "0 0 404 784", :aria-hidden "true"}
    [:defs
     [:pattern {:id "b1e6e422-73f8-40a6-b5d9-c8586e37e0e7", :x "0", :y "0", :width "20", :height "20", :patternunits "userSpaceOnUse"}
      [:rect {:x "0", :y "0", :width "4", :height "4", :class "text-gray-200", :fill "currentColor"}]]]
    [:rect {:width "404", :height "784", :fill "url(#b1e6e422-73f8-40a6-b5d9-c8586e37e0e7)"}]]
   [:div {:class "relative lg:grid lg:grid-cols-2 lg:gap-8 lg:items-center"}
    [:div {:class "relative"}
     [:h3 {:class "text-2xl font-extrabold text-gray-900 tracking-tight sm:text-3xl"}
      "Connect your ideas like you do"]
     [:p {:class "mt-3 text-lg text-gray-500"} "Connect your [[ideas]] and [[thoughts]] with Logseq. Your knowledge graph grows just as your brain generates and connects neurons from new knowledge and ideas."]]
    [:div {:class "mt-10 -mx-4 relative lg:mt-0", :aria-hidden "true"}
     [:svg {:class "absolute left-1/2 transform -translate-x-1/2 translate-y-16 lg:hidden", :width "784", :height "404", :fill "none", :viewbox "0 0 784 404"}
      [:defs
       [:pattern {:id "ca9667ae-9f92-4be7-abcb-9e3d727f2941", :x "0", :y "0", :width "20", :height "20", :patternunits "userSpaceOnUse"}
        [:rect {:x "0", :y "0", :width "4", :height "4", :class "text-gray-200", :fill "currentColor"}]]]
      [:rect {:width "784", :height "404", :fill "url(#ca9667ae-9f92-4be7-abcb-9e3d727f2941)"}]]
     [:img {:class "relative mx-auto", :width "490", :src "https://logseq.github.io/gifs/connections.gif"}]]]])

(defn feature-2
  []
  [:div
   [:svg {:class "hidden lg:block absolute right-full transform translate-x-1/2 translate-y-12", :width "404", :height "600", :fill "none", :viewbox "0 0 404 600", :aria-hidden "true"}
    [:defs
     [:pattern {:id "64e643ad-2176-4f86-b3d7-f2c5da3b6a6d", :x "0", :y "0", :width "20", :height "20", :patternunits "userSpaceOnUse"}
      [:rect {:x "0", :y "0", :width "4", :height "4", :class "text-gray-200", :fill "currentColor"}]]]
    [:rect {:width "404", :height "600", :fill "url(#64e643ad-2176-4f86-b3d7-f2c5da3b6a6d)"}]]
   [:div {:class "relative mt-12 sm:mt-16 lg:mt-24"}
    [:div {:class "lg:grid lg:grid-flow-row-dense lg:grid-cols-2 lg:gap-8 lg:items-center"}
     [:div {:class "lg:col-start-2"}
      [:h3 {:class "text-2xl font-extrabold text-gray-900 tracking-tight sm:text-3xl"}
       "Task management made easy"]
      [:p {:class "mt-3 text-lg text-gray-500"} "Organize your tasks and projects with built-in workflow commands like NOW/LATER/DONE, A/B/C priorities and repeated Scheduled/Deadlines. Moreover, Logseq comes with powerful query system to help you get insights and build your own workflow."]]
     [:div {:class "mt-10 -mx-4 relative lg:mt-0 lg:col-start-1"}
      [:svg {:class "absolute left-1/2 transform -translate-x-1/2 translate-y-16 lg:hidden", :width "784", :height "404", :fill "none", :viewbox "0 0 784 404", :aria-hidden "true"}
       [:defs
        [:pattern {:id "e80155a9-dfde-425a-b5ea-1f6fadd20131", :x "0", :y "0", :width "20", :height "20", :patternunits "userSpaceOnUse"}
         [:rect {:x "0", :y "0", :width "4", :height "4", :class "text-gray-200", :fill "currentColor"}]]]
       [:rect {:width "784", :height "404", :fill "url(#e80155a9-dfde-425a-b5ea-1f6fadd20131)"}]]
      [:img {:class "relative mx-auto", :width "490", :src "https://logseq.github.io/gifs/tasks.gif"}]]]]])

(defn features
  []
  [:div {:class "pb-16 bg-gray-50 overflow-hidden lg:pb-24"}
   [:div {:class "relative max-w-xl mx-auto px-4 sm:px-6 lg:px-8 lg:max-w-7xl"}
    (feature-1)

    (feature-2)]])

(defn testimonials
  []
  [:section {:class "py-16 lg:py-24 overflow-hidden"}
   [:h3 {:class "text-center text-3xl leading-8 font-extrabold tracking-tight text-gray-900 sm:text-4xl"}
    [:span "What People Are Saying"]]

   [:div {:class "mx-auto gap-4 md:grid md:grid-cols-3 md:px-6 lg:px-8 mt-4"}
    [:blockquote.twitter-tweet [:p {:lang "en" :dir "ltr"} "I'm using " [:a {:href "https://twitter.com/logseq?ref_src=twsrc%5Etfw"} "@logseq"] " + " [:a {:href "https://twitter.com/obsdmd?ref_src=twsrc%5Etfw"} "@obsdmd"] "! Logseq is an outliner + task management that works well with my \"18,000 things at once\" daily way of thinking. It's also connected to Obsidian, where I keep my longer term research that benefits from structured documents."] "— Jessica is trying her best (@heyitsliore) " [:a {:href "https://twitter.com/heyitsliore/status/1398354993657221122?ref_src=twsrc%5Etfw"} "May 28, 2021"]]
    [:blockquote.twitter-tweet [:p {:lang "en" :dir "ltr"} "Freaking *loving* " [:a {:href "https://twitter.com/logseq?ref_src=twsrc%5Etfw"} "@logseq"] " since I started using it on the desktop. " [:br] [:br] "Mashup of roam and org-mode's task management is making me very happy so far, even in 0.0.13. Super impressed as giving me all the things I like about roam, emacs org-mode, and notion together. ♥️"] "— Daryl Manning (@awws) " [:a {:href "https://twitter.com/awws/status/1374771771610603527?ref_src=twsrc%5Etfw"} "March 24, 2021"]]
    [:blockquote.twitter-tweet [:p {:lang "en" :dir "ltr"} "Every time I use " [:a {:href "https://twitter.com/logseq?ref_src=twsrc%5Etfw"} "@logseq"] " I'm thinking, I needed this years ago. And the gleefully look at a future where I had this for years. " [:br] [:br] "Video in the works"] "— Tools on Tech (@ToolsonTech) " [:a {:href "https://twitter.com/ToolsonTech/status/1378640136141950981?ref_src=twsrc%5Etfw"} "April 4, 2021"]]

    [:blockquote.twitter-tweet [:p {:lang "en" :dir "ltr"} "shout out to " [:a {:href "https://twitter.com/logseq?ref_src=twsrc%5Etfw"} "@logseq"] " if you haven't tried it! It is the best of Roam and Obsidian, in a format you are probably very used to already. Great community, amazing approachable devs, lightning fast development. Free, local, privacy focussed, outliner."] "— Luke Whitehead (@luque_whitehead) " [:a {:href "https://twitter.com/luque_whitehead/status/1395391019282276353?ref_src=twsrc%5Etfw"} "May 20, 2021"]]

    [:blockquote.twitter-tweet [:p {:lang "en" :dir "ltr"} [:a {:href "https://twitter.com/logseq?ref_src=twsrc%5Etfw"} "@logseq"] ", you've stolen my heart (well, maybe neurons). a thread... 🧵"] "— daytura // ladon n. (@ArchLeucoryx) " [:a {:href "https://twitter.com/ArchLeucoryx/status/1394182088576749568?ref_src=twsrc%5Etfw"} "May 17, 2021"]]

    [:blockquote.twitter-tweet [:p {:lang "en" :dir "ltr"} "Ok, I'm having an absolute BLAST using " [:a {:href "https://twitter.com/logseq?ref_src=twsrc%5Etfw"} "@logseq"] " for my DevLog and Tech notes that need more structure and organization. " [:br] [:br] "the MVP for the project was just do new daily DevLog notes and record useful info, started some porting yesterday from " [:a {:href "https://twitter.com/obsdmd?ref_src=twsrc%5Etfw"} "@obsdmd"] [:a {:href "https://twitter.com/logseq?ref_src=twsrc%5Etfw"} "@logseq"] " is 🔥LIT🔥 " [:a {:href "https://t.co/F8WRLwmIs7"} "pic.twitter.com/F8WRLwmIs7"]] "— Bryan Jenks 🌱️ ⠕ (@tallguyjenks) " [:a {:href "https://twitter.com/tallguyjenks/status/1392943684329480192?ref_src=twsrc%5Etfw"} "May 13, 2021"]]]])

(defn join-community
  []
  [:div.bg-white
   [:div.max-w-7xl.mx-auto.text-center.py-12.px-4.sm:px-6.lg:py-16.lg:px-8
    [:h2.text-3xl.font-extrabold.tracking-tight.text-gray-900.sm:text-4xl
     [:span.block "Ready to dive in?"]]
    [:p {:class "mt-4 max-w-3xl mx-auto text-center text-xl text-gray-500"}
     "Join the discord group to chat with the makers and our helpful community members."]
    [:div.mt-8.flex.justify-center
     [:div.inline-flex.rounded-md.shadow [:a.inline-flex.items-center.justify-center.px-5.py-3.border.border-transparent.text-base.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700 {:href "https://discord.gg/KpN4eHY"}
                                          [:span
                                           discord-logo
                                           [:span.ml-2 "Join the community"]]]]
     [:div.ml-3.inline-flex [:a.inline-flex.items-center.justify-center.px-5.py-3.border.border-transparent.text-base.font-medium.rounded-md.text-blue-700.bg-blue-100.hover:bg-blue-200 {:href "https://logseq.github.io"} "Check documentation"]]]]])

(defn footer
  []
  [:footer.bg-gray-800 {:aria-labelledby "footerHeading"}
   [:h2#footerHeading.sr-only "Footer"]
   [:div.max-w-7xl.mx-auto.py-12.px-4.sm:px-6.lg:py-16.lg:px-8
    [:div.text-gray-400.mb-2
     [:a {:href "https://www.producthunt.com/posts/logseq"} "Leave your review on Product Hunt"]]
    [:div.md:flex.md:items-center.md:justify-between
     [:div.flex.space-x-6.md:order-2
      [:a.text-gray-400.hover:text-gray-300 {:href "https://twitter.com/logseq"} [:span.sr-only "Twitter"] [:svg.h-6.w-6 {:fill "currentColor" :viewbox "0 0 24 24" :aria-hidden "true"} [:path {:d "M8.29 20.251c7.547 0 11.675-6.253 11.675-11.675 0-.178 0-.355-.012-.53A8.348 8.348 0 0022 5.92a8.19 8.19 0 01-2.357.646 4.118 4.118 0 001.804-2.27 8.224 8.224 0 01-2.605.996 4.107 4.107 0 00-6.993 3.743 11.65 11.65 0 01-8.457-4.287 4.106 4.106 0 001.27 5.477A4.072 4.072 0 012.8 9.713v.052a4.105 4.105 0 003.292 4.022 4.095 4.095 0 01-1.853.07 4.108 4.108 0 003.834 2.85A8.233 8.233 0 012 18.407a11.616 11.616 0 006.29 1.84"}]]]
      [:a.text-gray-400.hover:text-gray-300 {:href "https://github.com/logseq/logseq"} [:span.sr-only "GitHub"] [:svg.h-6.w-6 {:fill "currentColor" :viewbox "0 0 24 24" :aria-hidden "true"} [:path {:fill-rule "evenodd" :d "M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" :clip-rule "evenodd"}]]]]
     [:p.mt-8.text-base.text-gray-400.md:mt-0.md:order-1 "© 2021 Logseq"
      [:span
       [:a.ml-2 {:href "https://logseq.github.io/#/page/Privacy%20Policy"} "Privacy"]
       [:a.ml-2 {:href "https://logseq.github.io/#/page/Terms"} "Terms"]
       [:a.ml-2 {:href "mailto:hi@logseq.com"} "Contact us"]]]]]])

(defn hero
  [release-assets]
  [:div {:class "pt-10 bg-gray-900 sm:pt-16 lg:pt-8 lg:pb-14 lg:overflow-hidden"}
   [:div {:class "mx-auto max-w-7xl lg:px-8"}
    [:div {:class "lg:grid lg:grid-cols-2 lg:gap-8"}
     [:div {:class "mx-auto max-w-md px-4 sm:max-w-2xl sm:px-6 sm:text-center lg:px-0 lg:text-left lg:flex lg:items-center"}
      [:div {:class "lg:py-24"}
       [:span.mt-2 {:class "inline-flex items-center text-white bg-black rounded-full p-1 pr-2 sm:text-base lg:text-sm xl:text-base hover:text-gray-200"}
        [:span {:class "px-3 py-0.5 text-white text-xs font-semibold leading-5 uppercase tracking-wide bg-blue-500 rounded-full"} "Beta testing"]
        [:a {:href "https://www.youtube.com/watch?v=Pji6_0pbHFw"}
         [:span {:class "ml-4 text-sm"} "Why logseq?"]]
        [:svg {:class "ml-2 w-5 h-5 text-gray-500", :xmlns "http://www.w3.org/2000/svg", :viewbox "0 0 20 20", :fill "currentColor", :aria-hidden "true"}
         [:path {:fill-rule "evenodd", :d "M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z", :clip-rule "evenodd"}]]]
       [:h1 {:class "mt-4 text-4xl tracking-tight font-extrabold text-white sm:mt-5 sm:text-6xl lg:mt-6 xl:text-6xl"}
        [:span "A privacy-first, open-source knowledge base"]]

       [:p {:class "mt-3 text-base text-gray-300 sm:mt-5 sm:text-xl lg:text-lg xl:text-xl"} "Logseq is a joyful, open-source outliner that works on top of local plain-text Markdown and Org-mode files. Use it to write, organize and share your thoughts, keep your to-do list, and build your own digital garden."]
       [:div.mt-10.sm:flex.sm:justify-center.lg:justify-start
        [:div.rounded-md.shadow
         [:a.btn-download-os.w-full.flex.items-center.justify-center.px-8.py-3.border.border-transparent.text-base.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700.md:py-4.md:text-lg.md:px-10 {:href "https://github.com/logseq/logseq/releases"}
          "Download desktop app"]]
        [:div.mt-3.rounded-md.shadow.sm:mt-0.sm:ml-3
         [:a.btn-open-local-folder.w-full.flex.items-center.justify-center.px-8.py-3.border.border-transparent.text-base.font-medium.rounded-md.text-blue-600.bg-white.hover:bg-gray-50.md:py-4.md:text-lg.md:px-10 {:href "https://logseq.com?nfs=true"}
          "Live Demo"]]]
       [:div.mt-2.text-white.text-sm
        [:p "For Macs with Apple Silicon chips, click "
         [:a.text-blue-300.hover:text-blue-500 {:href (get release-assets "Mac-M1")}
          [:span "here"]]
         " to download"]]]]
     [:div {:class "-mb-16 sm:-mb-48 lg:m-0 lg:relative"
            :style {:margin-top "12rem"}}
      [:div.videoWrapper
       [:iframe {:width "560", :height "315", :src "https://www.youtube.com/embed/SUOdfa3MucE", :title "YouTube video player", :frameborder "0", :allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture", :allowfullscreen true}]]]]]])

(defn db-not-exists-page
  [user git-branch-name]
  (let [dev? config/dev?
        release (github/get-logseq-latest-release)
        release-assets (generate-string release)]
    (html/html5
     (head git-branch-name false)
     [:body
      [:div#root
       [:div {:class "min-h-screen"}
        [:div {:class "relative overflow-hidden"}
         [:header {:class "relative"}
          [:div {:class "bg-gray-900 pt-6"}
           [:nav {:class "relative max-w-7xl mx-auto flex items-center justify-between px-4 sm:px-6", :aria-label "Global"}
            [:div {:class "flex items-center flex-1"}
             [:div {:class "flex items-center justify-between w-full md:w-auto"}
              [:a {:href "#"}
               [:span {:class "sr-only"} "Logseq"]
               logo]
              [:div {:class "-mr-2 flex items-center md:hidden"}
               (login "mobile")]]
             [:div {:class "hidden space-x-8 md:flex md:ml-10"}
              [:a {:href "https://opencollective.com/logseq", :class "text-base font-medium text-white hover:text-gray-300"} "Pricing"]
              [:a {:href "https://discord.gg/KpN4eHY", :class "text-base font-medium text-white hover:text-gray-300"} "Community"]
              [:a {:href "https://discuss.logseq.com/", :class "text-base font-medium text-white hover:text-gray-300"} "Forum"]
              [:a {:href "https://logseq.github.io", :class "text-base font-medium text-white hover:text-gray-300"} "Documentation"]
              [:a {:href "https://trello.com/b/8txSM12G/logseq-roadmap", :class "text-base font-medium text-white hover:text-gray-300"} "Roadmap"]]]
            [:div {:class "hidden md:flex md:items-center md:space-x-6"}
             [:div.pt-2
              [:a {:class "github-button", :href "https://github.com/logseq/logseq", :data-color-scheme "no-preference: dark; light: dark_dimmed; dark: dark_dimmed;", :data-show-count "true", :aria-label "Star logseq/logseq on GitHub"} "Star"]]
             (login "desktop")]]]]
         [:main
          (hero release)

          (screenshot)

          (features)

          (join-community)

          (testimonials)

          (footer)]

         [:script {:async true :defer true :src "https://buttons.github.io/buttons.js"}]
         [:script {:async true :defer true :src "https://platform.twitter.com/widgets.js" :charset "utf-8"}]
         [:script (str "window.releaseAssets=" release-assets ";")]
         [:script (format
                   "let userAgent = navigator.appVersion;
let osDetails = {
  name: 'Unknown OS',
  icon: 'fa-question-circle'
};

if (userAgent.includes('Macintosh')) {
  osDetails.name = 'Mac';
}

if (userAgent.includes('Windows')) {
  osDetails.name = 'Windows';
}

if (userAgent.includes('Linux')) {
  osDetails.name = 'Linux';
}

if (userAgent.includes('iPhone')) {
  osDetails.name = 'iPhone';
}

if (userAgent.includes('Android')) {
  osDetails.name = 'Android';
}
updateOsDownloadButton(osDetails);

function updateOsDownloadButton(osDetails) {
let releaseAssets = window.releaseAssets;
let button = document.querySelector('.btn-download-os');
button.href = releaseAssets[osDetails.name] || 'https://github.com/logseq/logseq/releases';
}

function nfsSupported() {
  if ('chooseFileSystemEntries' in self) {
    return 'chooseFileSystemEntries'
  } else if ('showOpenFilePicker' in self) {
    return 'showOpenFilePicker'
  }
  return false
}

let isnfssupported = nfsSupported();
if (isnfssupported) {
  let node = document.querySelector('.btn-open-local-folder');
  node.innerHTML = 'Open a local folder';
  node.href = '%s?spa=true';
} else {
  let node = document.querySelector('.btn-open-local-folder');
  node.innerHTML = 'Live Demo'
  node.href = '%s?spa=true'
}

function login() {
   document.getElementById(\"desktop-dropdown\").classList.toggle(\"show\");
   document.getElementById(\"mobile-dropdown\").classList.toggle(\"show\");
}

// Close the dropdown if the user clicks outside of it
window.onclick = function(event) {
  if (!event.target.matches('.dropbtn')) {
    var dropdowns = document.getElementsByClassName(\"dropdown-content\");
    var i;
    for (i = 0; i < dropdowns.length; i++) {
      var openDropdown = dropdowns[i];
      if (openDropdown.classList.contains('show')) {
        openDropdown.classList.remove('show');
      }
    }
  }
}
"
                   config/website-uri
                   config/website-uri)]]]]])))

(defn home
  [user db-exists? git-branch-name]
  (if db-exists?
    (db-exists-page user git-branch-name)
    (db-not-exists-page user git-branch-name)))
