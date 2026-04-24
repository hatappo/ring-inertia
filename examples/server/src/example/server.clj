(ns example.server
  (:require [ring.adapter.jetty :as jetty]
            [ring.inertia :as inertia]))

(def todos
  [{:id 1 :title "Ring middleware implements the Inertia protocol" :done true}
   {:id 2 :title "React page receives server props" :done true}
   {:id 3 :title "Navigate without a full browser reload" :done false}])

(def asset-tags
  ["<script type=\"module\">
     import RefreshRuntime from 'http://localhost:5173/@react-refresh'
     RefreshRuntime.injectIntoGlobalHook(window)
     window.$RefreshReg$ = () => {}
     window.$RefreshSig$ = () => (type) => type
     window.__vite_plugin_react_preamble_installed__ = true
   </script>"
   "<script type=\"module\" src=\"http://localhost:5173/@vite/client\"></script>"
   "<script type=\"module\" src=\"http://localhost:5173/src/main.jsx\"></script>"])

(defn- home [request]
  (inertia/render request "Home" {:todos todos
                                  :serverTime (str (java.time.Instant/now))}))

(defn- about [request]
  (inertia/render request "About" {:description "This page was rendered by a Clojure Ring handler."}))

(defn- query-param [request key]
  (some->> (:query-string request)
           (re-find (re-pattern (str "(?:^|&)" key "=([^&]+)")))
           second))

(defn- visit-counter [request]
  (inertia/render request "Visits" {:count (parse-long (or (query-param request "count") "0"))}))

(defn routes [request]
  (case [(:request-method request) (:uri request)]
    [:get "/"] (home request)
    [:get "/about"] (about request)
    [:get "/visits"] (visit-counter request)
    [:get "/external"] (inertia/external-redirect "https://inertiajs.com")
    [:patch "/messages"] {:status 302
                          :headers {"Location" "/"}
                          :body ""}
    {:status 404
     :headers {"Content-Type" "text/plain; charset=utf-8"}
     :body "Not found"}))

(def app
  (inertia/wrap-inertia
   routes
   {:version "dev"
    :title "Ring Inertia Example"
    :asset-tags asset-tags
    :shared-props (fn [_]
                    {:appName "Ring Inertia"})}))

(defn -main [& _]
  (jetty/run-jetty #'app {:port 3000 :join? true}))
