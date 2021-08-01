(ns app.result-test
  (:require [clojure.test :refer :all])
  (:require [app.result :refer :all]))

(deftest test-check-r
  (testing "test normal usage."
    (let [r (check-r
              (success :clause-one)
              (success :clause-two)
              (success :clause-three))
          expect (success :clause-three)]
      (is (= expect r))))

  (testing "return when encounter a false-result"
    (let [r (check-r
              (success :clause-one)
              (failed :clause-two)
              (success :clause-three))
          expect (failed :clause-two)]
      (is (= expect r))))

  (testing "non-false-result value is treated as true."
    (let [r (check-r
              (success :clause-one)
              3.14
              (success :clause-three))
          expect (success :clause-three)]
      (is (= expect r)))))

(deftest test-let-r
  (testing "all bindings are true-result"
    (let [r (let-r [a (success :clause-one)
                    b (success :clause-two)]
                   [a b])
          expect [:clause-one :clause-two]]
      (is (= expect r))))

  (testing "return when encounter a false-result"
    (let [r (let-r [a (success :clause-one)
                    b (failed :clause-two)]
              (success :clause-three))
          expect (failed :clause-two)]
      (is (= expect r))))

  (testing "non-false-result value is treated as true."
    (let [r (let-r [a (success :clause-one)
                    b 3.14
                    c (success :clause-three)]
                   [a b c])
          expect [:clause-one
                  3.14
                  :clause-three]]
      (is (= expect r)))))


