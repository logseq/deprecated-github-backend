(ns app.config
  (:require [aero.core :refer (read-config)]
            [clojure.java.io :as io]))

(def config (read-config (io/resource "config.edn")))

(def production? (= "production" (:env config)))
(def dev? (= "dev" (:env config)))

(def test? (= "test" (:env config)))
(def staging? (= "staging" (:env config)))
(def website-uri (:website-uri config))
(def cookie-domain (:cookie-domain config))

(def cdn-uri (:cdn-uri config))
(def asset-uri (:asset-uri config))
