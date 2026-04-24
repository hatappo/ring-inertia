(ns ring-inertia.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [ring.inertia :as inertia]))

(def base-request
  {:request-method :get
   :scheme :http
   :server-name "localhost"
   :server-port 3000
   :uri "/users"
   :headers {"host" "localhost:3000"}})

(defn handler [request]
  (inertia/render request "Users/Index" {:users [{:id 1 :name "Ada"}]}))

(deftest html-response-test
  (let [app (inertia/wrap-inertia handler {:version "test-version"})
        response (app base-request)]
    (is (= 200 (:status response)))
    (is (= "text/html; charset=utf-8" (get-in response [:headers "Content-Type"])))
    (is (= "X-Inertia" (get-in response [:headers "Vary"])))
    (is (re-find #"<script data-page=\"app\" type=\"application/json\">" (:body response)))
    (is (re-find #"\"component\":\"Users/Index\"" (:body response)))
    (is (re-find #"\"version\":\"test-version\"" (:body response)))))

(deftest inertia-json-response-test
  (let [app (inertia/wrap-inertia handler {:version "test-version"})
        response (app (assoc-in base-request [:headers "x-inertia"] "true"))]
    (is (= 200 (:status response)))
    (is (= "application/json; charset=utf-8" (get-in response [:headers "Content-Type"])))
    (is (= "true" (get-in response [:headers "X-Inertia"])))
    (is (= "X-Inertia" (get-in response [:headers "Vary"])))
    (is (= "{\"component\":\"Users/Index\",\"props\":{\"users\":[{\"id\":1,\"name\":\"Ada\"}],\"errors\":{}},\"url\":\"/users\",\"version\":\"test-version\"}"
           (:body response)))))

(deftest partial-reload-test
  (let [expensive-called? (atom false)
        app (inertia/wrap-inertia
             (fn [request]
               (inertia/render request "Users/Index"
                               {:users [{:id 1}]
                                :expensive (fn [_]
                                             (reset! expensive-called? true)
                                             "computed")}))
             {:version "v"})
        response (app (assoc base-request
                             :headers {"x-inertia" "true"
                                       "x-inertia-partial-component" "Users/Index"
                                       "x-inertia-partial-data" "users"}))]
    (is (= "{\"component\":\"Users/Index\",\"props\":{\"users\":[{\"id\":1}],\"errors\":{}},\"url\":\"/users\",\"version\":\"v\"}"
           (:body response)))
    (is (false? @expensive-called?))))

(deftest asset-version-mismatch-test
  (let [app (inertia/wrap-inertia handler {:version "new-version"})
        response (app (assoc base-request
                             :headers {"host" "example.test"
                                       "x-inertia" "true"
                                       "x-inertia-version" "old-version"}))]
    (is (= 409 (:status response)))
    (is (= "http://example.test/users" (get-in response [:headers "X-Inertia-Location"])))))

(deftest redirect-status-test
  (let [app (inertia/wrap-inertia
             (fn [_]
               {:status 302
                :headers {"Location" "/users"}
                :body ""}))]
    (testing "Inertia PUT/PATCH/DELETE redirects become 303"
      (is (= 303 (:status (app (assoc base-request
                                      :request-method :patch
                                      :headers {"x-inertia" "true"}))))))
    (testing "normal GET redirects stay 302"
      (is (= 302 (:status (app base-request)))))))

(deftest html-json-is-safe-test
  (let [app (inertia/wrap-inertia
             (fn [request]
               (inertia/render request "Unsafe" {:message "</script><script>alert(1)</script>"})))
        response (app base-request)]
    (is (not (str/includes? (:body response) "</script><script>")))
    (is (str/includes? (:body response) "\\u003c/script\\u003e"))))

(deftest json-null-array-items-test
  (let [app (inertia/wrap-inertia
             (fn [request]
               (inertia/render request "Nullable" {:items [1 nil 3]})))
        response (app (assoc-in base-request [:headers "x-inertia"] "true"))]
    (is (str/includes? (:body response) "\"items\":[1,null,3]"))))
