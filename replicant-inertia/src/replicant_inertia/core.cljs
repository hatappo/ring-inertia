(ns replicant-inertia.core
  (:require [clojure.string :as str]
            [replicant.dom :as r]))

(defonce app* (atom nil))

(defn- page->clj [page]
  (js->clj page :keywordize-keys true))

(defn- clj->page [page]
  (clj->js page))

(defn- option [options kebab camel]
  (or (get options kebab) (get options camel)))

(defn- normalize-component [component]
  (if (and (some? component) (some? (.-default component)))
    (.-default component)
    component))

(defn- component-error [name]
  (js/Error. (str "Could not resolve Inertia component: " name)))

(defn- resolve-component! [resolve name page]
  (-> (js/Promise.resolve (resolve name (page->clj page)))
      (.then (fn [component]
               (let [component (normalize-component component)]
                 (when-not (fn? component)
                   (throw (component-error name)))
                 component)))))

(defn- render-component! [el component page]
  (let [page-data (page->clj page)]
    (r/render el (component (:props page-data) page-data))))

(defn- page-script [id]
  (.querySelector js/document (str "script[data-page=\"" id "\"][type=\"application/json\"]")))

(defn- initial-page [id]
  (some-> (page-script id)
          .-textContent
          js/JSON.parse))

(defn- current-url []
  (str (.-pathname js/location) (.-search js/location) (.-hash js/location)))

(defn- same-url? [url]
  (= url (current-url)))

(defn- history-state [page]
  #js {:page page})

(defn- replace-history! [page]
  (.replaceState js/history (history-state page) "" (.-url page)))

(defn- push-history! [page]
  (.pushState js/history (history-state page) "" (.-url page)))

(defn- merge-partial-props [old-page new-page options]
  (if (or (seq (option options :only :only))
          (seq (option options :except :except)))
    (let [old-data (page->clj old-page)
          new-data (page->clj new-page)]
      (clj->page (assoc new-data :props (merge (:props old-data)
                                                (:props new-data)))))
    new-page))

(defn- set-page! [page options]
  (let [{:keys [el resolve render]} @app*
        page (merge-partial-props (:page @app*) page options)
        replace? (or (option options :replace :replace)
                     (same-url? (.-url page)))
        preserve-scroll? (option options :preserve-scroll :preserveScroll)
        render! (or render render-component!)]
    (-> (resolve-component! resolve (.-component page) page)
        (.then
         (fn [component]
           (swap! app* assoc :page page :component component)
           (render! el component page)
           (if replace?
             (replace-history! page)
             (push-history! page))
           (when-not preserve-scroll?
             (.scrollTo js/window 0 0))
           page)))))

(defn create-inertia-app!
  "Mounts an Inertia app and renders resolved page components with Replicant.

  Options:
  - `:id` DOM element id and Inertia page script id. Defaults to `\"app\"`.
  - `:resolve` function of component name and page data. Must return a
    Replicant component function or a promise resolving to one.
  - `:render` optional custom renderer of element, component, and JS page.
  - `:on-ready` optional callback with `:el`, `:component`, and `:page`."
  [{:keys [id resolve render on-ready]
    :or {id "app"}}]
  (when-not resolve
    (throw (js/Error. "create-inertia-app! requires a :resolve function")))
  (let [el (.getElementById js/document id)
        initial-page (initial-page id)
        render! (or render render-component!)]
    (when-not el
      (throw (js/Error. (str "Could not find Inertia root element: #" id))))
    (when-not initial-page
      (throw (js/Error. (str "Could not find Inertia initial page data for: " id))))
    (reset! app* {:el el
                  :page initial-page
                  :resolve resolve
                  :render render})
    (set! (.-onpopstate js/window)
          (fn [event]
            (when-let [page (some-> event .-state .-page)]
              (set-page! page {:replace true
                               :preserveScroll true}))))
    (replace-history! initial-page)
    (-> (resolve-component! resolve (.-component initial-page) initial-page)
        (.then
         (fn [component]
           (swap! app* assoc :component component)
           (render! el component initial-page)
           (when on-ready
             (on-ready {:el el
                        :component component
                        :page (page->clj initial-page)})))))))

(defn- add-query-param! [url key value]
  (if (sequential? value)
    (doseq [item value]
      (.append (.-searchParams url) (name key) item))
    (.append (.-searchParams url) (name key) value)))

(defn- url-with-query [url data]
  (let [url (js/URL. url (.-href js/location))]
    (doseq [[key value] data]
      (when (some? value)
        (add-query-param! url key value)))
    (str (.-pathname url) (.-search url) (.-hash url))))

(defn- request-method [options fallback]
  (-> (or (option options :method :method) fallback) name str/upper-case))

(defn- prop-names [props]
  (->> props
       (map name)
       (str/join ",")))

(defn- request-headers [options]
  (let [page (:page @app*)
        only (option options :only :only)
        except (option options :except :except)
        headers (cond-> {"Accept" "application/json, text/html"
                         "X-Requested-With" "XMLHttpRequest"
                         "X-Inertia" "true"}
                  (some? (.-version page))
                  (assoc "X-Inertia-Version" (.-version page))
                  (seq only)
                  (assoc "X-Inertia-Partial-Component" (.-component page)
                         "X-Inertia-Partial-Data" (prop-names only))
                  (seq except)
                  (assoc "X-Inertia-Partial-Component" (.-component page)
                         "X-Inertia-Partial-Except" (prop-names except)))]
    headers))

(defn- fetch-options [method data options]
  (let [json-body? (and (not= method "GET") (seq data))]
    (clj->js
     (cond-> {:method method
              :credentials "same-origin"
              :headers (cond-> (request-headers options)
                         json-body?
                         (assoc "Content-Type" "application/json"))}
       json-body?
       (assoc :body (js/JSON.stringify (clj->js data)))))))

(defn- response-header [response name]
  (.get (.-headers response) name))

(defn- redirect-browser! [url]
  (set! (.-href js/location) url))

(defn- handle-response! [response options]
  (let [location (or (response-header response "X-Inertia-Location")
                     (response-header response "X-Inertia-Redirect"))]
    (cond
      (some? location)
      (redirect-browser! location)

      (= "true" (response-header response "X-Inertia"))
      (-> (.json response)
          (.then #(set-page! % options)))

      :else
      (redirect-browser! (.-url response)))))

(defn visit!
  ([url] (visit! url nil))
  ([url options]
   (let [options (or options {})
         method (request-method options :get)
         data (or (option options :data :data) {})
         url (if (= method "GET") (url-with-query url data) url)
         on-start (option options :on-start :onStart)
         on-success (option options :on-success :onSuccess)
         on-error (option options :on-error :onError)
         on-finish (option options :on-finish :onFinish)]
     (when on-start
       (on-start))
     (-> (js/fetch url (fetch-options method data options))
         (.then #(handle-response! % options))
         (.then (fn [page]
                  (when on-success
                    (on-success page))
                  page))
         (.catch (fn [error]
                   (when on-error
                     (on-error error))
                   (throw error)))
         (.finally (fn []
                     (when on-finish
                       (on-finish))))))))

(defn get!
  ([url] (get! url nil nil))
  ([url data] (get! url data nil))
  ([url data options]
   (visit! url (merge (or options {}) {:method :get :data (or data {})}))))

(defn post!
  ([url] (post! url nil nil))
  ([url data] (post! url data nil))
  ([url data options]
   (visit! url (merge (or options {}) {:method :post :data (or data {})}))))

(defn patch!
  ([url] (patch! url nil nil))
  ([url data] (patch! url data nil))
  ([url data options]
   (visit! url (merge (or options {}) {:method :patch :data (or data {})}))))

(defn delete!
  ([url] (delete! url nil))
  ([url options]
   (visit! url (merge (or options {}) {:method :delete}))))

(defn reload!
  ([] (reload! nil))
  ([options]
   (visit! (current-url) (merge {:method :get
                                 :replace true
                                 :preserveScroll true}
                                (or options {})))))

(defn- plain-left-click? [event]
  (and (= 0 (.-button event))
       (not (.-altKey event))
       (not (.-ctrlKey event))
       (not (.-metaKey event))
       (not (.-shiftKey event))))

(defn- method-name [method]
  (-> (or method :get) name str/lower-case))

(defn link
  "Returns a Replicant hiccup anchor that performs an Inertia visit.

  `:href` is required. Optional Inertia keys are `:method`, `:data`, and
  `:options`. Remaining keys are emitted as anchor attributes."
  [attrs & children]
  (let [{:keys [href method data options]} attrs
        anchor-attrs (dissoc attrs :method :data :options)
        existing-on (:on anchor-attrs)
        existing-click (:click existing-on)
        on-click (fn [event]
                   (when existing-click
                     (existing-click event))
                   (when (and (not (.-defaultPrevented event))
                              (plain-left-click? event))
                     (.preventDefault event)
                     (visit! href (merge {:method (method-name method)}
                                         (when (some? data) {:data data})
                                         options))))]
    (into [:a (assoc anchor-attrs :on (assoc existing-on :click on-click))]
          children)))
