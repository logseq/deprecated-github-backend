(ns app.s3
  (:require [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.coerce :as coerce])
  (:import
   (com.amazonaws AmazonServiceException HttpMethod SdkClientException)
   (com.amazonaws.auth AWSStaticCredentialsProvider BasicAWSCredentials)
   (com.amazonaws.client.builder AwsClientBuilder$EndpointConfiguration)
   (com.amazonaws.services.s3 AmazonS3ClientBuilder)
   (com.amazonaws.services.s3.model CannedAccessControlList CreateBucketRequest
                                    DeleteBucketRequest DeleteObjectRequest
                                    GeneratePresignedUrlRequest HeadBucketRequest)))

(defn s3-client
  [key secret]
  (let [endpoint    (AwsClientBuilder$EndpointConfiguration. "s3.amazonaws.com" "us-east-1")
        credentials (AWSStaticCredentialsProvider. (BasicAWSCredentials. key secret))]
    (-> (AmazonS3ClientBuilder/standard)
        (.withEndpointConfiguration endpoint)
        (.withPathStyleAccessEnabled true)
        (.withCredentials credentials)
        .build)))

(defn- http-method [method]
  (-> method name str/upper-case HttpMethod/valueOf))

(defn generate-presigned-url
  [access-key access-secret bucket key]
  (let [method (HttpMethod/valueOf "PUT")
        request (-> (GeneratePresignedUrlRequest. bucket key)
                    (.withExpiration (coerce/to-date (-> 10 t/minutes t/from-now)))
                    (.withMethod method))]
    (.generatePresignedUrl (s3-client access-key access-secret) request)))

(System/setProperty "SDKGlobalConfiguration.ENABLE_S3_SIGV4_SYSTEM_PROPERTY" "true")
