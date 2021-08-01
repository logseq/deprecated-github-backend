(defproject logseq "0.1.0-SNAPSHOT"
  :description "Knowledge base tool"
  :url "https://logseq.com"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [clj-social "0.1.9"]
                 [org.postgresql/postgresql "42.2.8"]
                 [org.clojure/java.jdbc "0.7.10"]
                 [honeysql "0.9.8"]
                 [hikari-cp "2.9.0"]
                 [toucan "1.15.0"]
                 [ragtime "0.8.0"]
                 [org.clojure/tools.namespace "0.3.1"]
                 [buddy/buddy-sign "3.1.0"]
                 [buddy/buddy-hashers "1.4.0"]
                 [io.pedestal/pedestal.service "0.5.5"]
                 [io.pedestal/pedestal.jetty "0.5.5"]
                 [metosin/reitit-pedestal "0.4.2"]
                 [metosin/reitit "0.4.2"]
                 [metosin/jsonista "0.2.5"]
                 [aero "1.1.6"]
                 [com.stuartsierra/component "0.4.0"]
                 [com.taoensso/nippy "2.14.0"]
                 [hiccup "2.0.0-alpha2"]
                 [hickory "0.7.1"]
                 [com.amazonaws/aws-java-sdk-cloudfront "1.11.744"]
                 [com.amazonaws/aws-java-sdk-s3 "1.11.744"]
                 [com.cognitect.aws/api "0.8.456"]
                 [com.cognitect.aws/endpoints "1.1.11.753"]
                 [com.cognitect.aws/s3 "784.2.593.0"]
                 [com.cognitect.aws/cloudfront "789.2.611.0"]
                 [lambdaisland/uri "1.2.1"]
                 [com.fzakaria/slf4j-timbre "0.3.19"]
                 [clj-http "3.10.1"]
                 [clj-rss "0.2.5"]
                 [org.clojure/core.cache "1.0.207"]
                 [clj-time "0.15.2"]]
  :plugins [[lein-cljfmt "0.7.0"]]
  :main app.core
  :source-paths ["src/main"]
  :profiles {:repl {:dependencies [[io.pedestal/pedestal.service-tools "0.5.7"]]
                    :source-paths ["src/main" "src/dev"]}
             :uberjar {:main app.core
                       :aot :all
                       :omit-source true
                       :uberjar-name "logseq.jar"}}
  :repl-options {:init-ns user}
  :jvm-opts ["-Duser.timezone=UTC" "-Dclojure.spec.check-asserts=true"]
  :aliases {"migrate"  ["run" "-m" "user/migrate"]
            "rollback" ["run" "-m" "user/rollback"]}
  :min-lein-version "2.0.0"
  :cljfmt {:paths ["src" "test" "web/src"]})
