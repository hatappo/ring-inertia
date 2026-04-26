(ns ring-inertia.test-runner
  (:require [clojure.test :refer [run-tests]]
            [ring-inertia.core-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'ring-inertia.core-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
