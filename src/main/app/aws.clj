(ns app.aws
  (:require [cognitect.aws.client.api :as aws]
            [cognitect.aws.util :as util]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [app.config :as config])
  (:import [com.amazonaws.services.cloudfront CloudFrontUrlSigner]
           [java.io File]
           [java.util Date]
           com.amazonaws.services.cloudfront.util.SignerUtils))

(def cloudfront (aws/client {:api :cloudfront}))

(def ops (aws/ops cloudfront))

(defn iso8601-date
  ([] (iso8601-date (Date.)))
  ([d] (->> (util/format-date util/iso8601-date-format d)
            (util/parse-date util/iso8601-date-format))))

(defn get-signed-url-with-canned-policy
  (^java.lang.String [protocol
                      ^java.lang.String distribution-domain
                      ^java.io.File private-key-file
                      ^java.lang.String s-3-object-key
                      ^java.lang.String key-pair-id
                      ^java.util.Date date-less-than]
   (let [protocol (case protocol
                    :http
                    com.amazonaws.services.cloudfront.util.SignerUtils$Protocol/http
                    :https
                    com.amazonaws.services.cloudfront.util.SignerUtils$Protocol/https
                    ;; rtmp
                    com.amazonaws.services.cloudfront.util.SignerUtils$Protocol/rtmp)]
     (CloudFrontUrlSigner/getSignedURLWithCannedPolicy
      protocol distribution-domain private-key-file s-3-object-key key-pair-id date-less-than))))

(defn get-signed-url
  [s3-object-key expire-minutes]
  (let [{:keys [pk-path key-pair-id]} (:aws config/config)
        domain config/cdn-uri
        private-key-file-path pk-path
        private-key-file (java.io.File. private-key-file-path)
        s3-object-key s3-object-key
        key-pair-id key-pair-id
        date-less-than (->
                        (tc/to-date (t/plus (t/now) (t/minutes expire-minutes)))
                        (iso8601-date))]
    (get-signed-url-with-canned-policy
     :https domain private-key-file s3-object-key
     key-pair-id date-less-than)))
