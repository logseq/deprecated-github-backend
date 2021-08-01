(ns app.result)

(defrecord Result [success? data])
(defn make-result [success? data] (Result. success? data))
(defn result? [result] (instance? Result result))

(defn success
  ([]
   (make-result true nil))
  ([data]
   (make-result true data)))
(defn failed [data] (make-result false data))

(defn failed? [result] (and (result? result) (not (:success? result))))
(def success? (complement failed?))

(defmacro check-r
  "Return Result when encounter a failed Result or continue executing the next clause.
  Usage:

  (check-r
    (success :clause-one)
    (failed :clause-two)
    (success :clause-three))

   => #app.result.Result{:success? false, :data :clause-two}

   Also see app.result-test/test-check-r
   "
  [f & r]
  (let [s (gensym)]
    `(let [~s ~f]
       (if (failed? ~s)
         ~s
         ~(if (seq r)
            `(check-r ~@r)
            s)))))

(defmacro let-r
  "Return Result when encounter a failed Result or continue executing the next clause.
  Usage:


  (let-r [a (success :clause-one)
          b (failed :clause-two)]
    (success :clause-three))

  => #app.result.Result{:success? false, :data :clause-two}

  Also see app.result-test/test-rlet
  "
  [bindings & body]
  (assert (even? (count bindings)) "Binding element numbers should match even?.")
  (let [[f s & r] bindings]
    `(let [v# ~s]
       (if (failed? v#)
         v#
         (let [~f (if (result? v#) (:data v#) v#)]
           ~(if (seq r)
              `(let-r ~(vec r) ~@body)
              `(do ~@body)))))))
