(ns example.server
  (:require [clojure.string :as str]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.inertia :as inertia]))

(defonce todos*
  (atom [{:id 1 :title "Implement the Ring middleware" :done true}
         {:id 2 :title "Wire it to a React Inertia client" :done true}
         {:id 3 :title "Try the redirect protocol examples" :done false}]))

(defonce next-todo-id* (atom 3))

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
  (let [todos @todos*
        open-count (count (remove :done todos))]
    (inertia/render request "Home" {:todos todos
                                    :todoStats {:total (count todos)
                                                :open open-count
                                                :done (- (count todos) open-count)}
                                    :serverTime (str (java.time.Instant/now))})))

(defn- about [request]
  (inertia/render request "About" {:description "This page was rendered by a Clojure Ring handler."}))

(defn- query-param [request key]
  (or (get-in request [:params (keyword key)])
      (get-in request [:params key])))

(defn- visit-counter [request]
  (inertia/render request "Visits" {:count (parse-long (or (query-param request "count") "0"))}))

(defn- redirect-home
  ([]
   (redirect-home 302))
  ([status]
   {:status status
    :headers {"Location" "/"}
    :body ""}))

(defn- create-todo [request]
  (let [title (str/trim (or (query-param request "title") ""))]
    (when-not (str/blank? title)
      (swap! todos* conj {:id (swap! next-todo-id* inc)
                          :title title
                          :done false}))
    (redirect-home 303)))

(defn- todo-id-route [uri suffix]
  (some->> uri
           (re-matches (re-pattern (str "^/todos/(\\d+)" suffix "$")))
           second
           parse-long))

(defn- toggle-todo [id]
  (swap! todos*
         (fn [todos]
           (mapv (fn [todo]
                   (if (= id (:id todo))
                     (update todo :done not)
                     todo))
                 todos)))
  (redirect-home))

(defn- delete-todo [id]
  (swap! todos* (fn [todos] (vec (remove #(= id (:id %)) todos))))
  (redirect-home))

(defn routes [request]
  (let [{:keys [request-method uri]} request]
    (or
     (case [request-method uri]
       [:get "/"] (home request)
       [:get "/about"] (about request)
       [:get "/visits"] (visit-counter request)
       [:get "/external"] (inertia/external-redirect "https://inertiajs.com")
       [:post "/todos"] (create-todo request)
       [:patch "/messages"] {:status 302
                             :headers {"Location" "/"}
                             :body ""}
       nil)
     (when (= request-method :patch)
       (when-let [id (todo-id-route uri "/toggle")]
         (toggle-todo id)))
     (when (= request-method :delete)
       (when-let [id (todo-id-route uri "")]
         (delete-todo id)))
     {:status 404
      :headers {"Content-Type" "text/plain; charset=utf-8"}
      :body "Not found"})))

(def app
  (-> routes
      (wrap-json-params {:keywords? true})
      wrap-keyword-params
      wrap-params
      (inertia/wrap-inertia
       {:version "dev"
        :title "Ring Inertia Example"
        :asset-tags asset-tags
        :shared-props (fn [_]
                        {:appName "Ring Inertia"})})))

(defn -main [& _]
  (jetty/run-jetty #'app {:port 3000 :join? true}))
