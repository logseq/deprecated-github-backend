(ns app.reserved-routes)

(defonce reserved
  #{"changelog" "help" "docs" "apps" "downloads" "membership"
    "about" "careers" "new" "logseq" "privacy" "terms"
    "upload" "add"

    ;; frontend routes
    "repos" "all-files" "file" "new-page" "page" "all-pages"
    "graph" "diff" "draw"})

(defn reserved?
  [s]
  (contains? reserved s))
