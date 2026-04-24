(ns ring.inertia
  (:require [clojure.string :as str]))

(def ^:private page-key ::page)

(defn inertia-request?
  "Returns true when a Ring request is an Inertia XHR visit."
  [request]
  (= "true" (get-in request [:headers "x-inertia"])))

(defn render
  "Creates a Ring response that wrap-inertia serializes as an Inertia page.

  Options are merged into the page object. Clojure-style option keys such as
  :encrypt-history and :merge-props are rendered as Inertia camelCase keys."
  ([request component props]
   (render request component props nil))
  ([request component props options]
   {:status 200
    :headers {}
    :body {page-key {:component component
                     :props (or props {})
                     :url (str (:uri request)
                               (when-let [query (:query-string request)]
                                 (str "?" query)))
                     :options (or options {})}}}))

(defn external-redirect
  "Creates an Inertia external redirect response."
  [url]
  {:status 409
   :headers {"X-Inertia-Location" url}
   :body ""})

(defn fragment-redirect
  "Creates an Inertia redirect response for URLs with fragments."
  [url]
  {:status 409
   :headers {"X-Inertia-Redirect" url}
   :body ""})

(defn- header-value [request header-name]
  (get-in request [:headers (str/lower-case header-name)]))

(defn- request-target [request]
  (str (:uri request)
       (when-let [query (:query-string request)]
         (str "?" query))))

(defn- absolute-request-url [request]
  (let [host (or (header-value request "host")
                 (str (:server-name request)
                      (when-let [port (:server-port request)]
                        (str ":" port))))]
    (str (name (or (:scheme request) :http)) "://" host (request-target request))))

(defn- current-version [request version]
  (let [value (if (fn? version) (version request) version)]
    (some-> value str)))

(defn- version-mismatch? [request version]
  (and (inertia-request? request)
       (= :get (:request-method request))
       (some? (header-value request "x-inertia-version"))
       (not= (header-value request "x-inertia-version")
             (current-version request version))))

(defn- html-escape [value]
  (-> (str value)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- camel-case [value]
  (let [[head & tail] (str/split value #"-")]
    (apply str head (map str/capitalize tail))))

(defn- json-key [key]
  (cond
    (keyword? key) (camel-case (name key))
    (string? key) key
    :else (str key)))

(defn- write-json-string [^StringBuilder out value]
  (.append out \")
  (doseq [ch (str value)]
    (case ch
      \" (.append out "\\\"")
      \\ (.append out "\\\\")
      \backspace (.append out "\\b")
      \formfeed (.append out "\\f")
      \newline (.append out "\\n")
      \return (.append out "\\r")
      \tab (.append out "\\t")
      (let [code (int ch)]
        (if (or (< code 32)
                (= code 0x2028)
                (= code 0x2029))
          (.append out (format "\\u%04x" code))
          (.append out ch)))))
  (.append out \"))

(declare write-json)

(defn- write-json-map [^StringBuilder out value]
  (.append out \{)
  (loop [[[k v] & more] (seq value)
         first? true]
    (when k
      (when-not first?
        (.append out \,))
      (write-json-string out (json-key k))
      (.append out \:)
      (write-json out v)
      (recur more false)))
  (.append out \}))

(defn- write-json-seq [^StringBuilder out value]
  (.append out \[)
  (loop [items (seq value)
         first? true]
    (when (seq items)
      (when-not first?
        (.append out \,))
      (write-json out (first items))
      (recur (next items) false)))
  (.append out \]))

(defn- write-json [^StringBuilder out value]
  (cond
    (nil? value) (.append out "null")
    (true? value) (.append out "true")
    (false? value) (.append out "false")
    (string? value) (write-json-string out value)
    (keyword? value) (write-json-string out (name value))
    (number? value) (.append out (str value))
    (map? value) (write-json-map out value)
    (sequential? value) (write-json-seq out value)
    :else (throw (ex-info "Unsupported JSON value" {:value value
                                                     :type (type value)}))))

(defn- json [value]
  (str (doto (StringBuilder.)
         (write-json value))))

(defn- safe-json-for-html [value]
  (-> (json value)
      (str/replace "<" "\\u003c")
      (str/replace ">" "\\u003e")
      (str/replace "&" "\\u0026")
      (str/replace "\u2028" "\\u2028")
      (str/replace "\u2029" "\\u2029")))

(defn- call-value [request value]
  (cond
    (delay? value) @value
    (fn? value) (value request)
    :else value))

(defn- resolve-props [request props]
  (into {}
        (map (fn [[k v]] [k (call-value request v)]))
        props))

(defn- split-header [value]
  (->> (str/split (or value "") #",")
       (map str/trim)
       (remove str/blank?)
       set))

(defn- prop-key-name [key]
  (json-key key))

(defn- selected-prop? [names [key _]]
  (contains? names (prop-key-name key)))

(defn- apply-partial-reload [request component props]
  (if (and (inertia-request? request)
           (= component (header-value request "x-inertia-partial-component")))
    (let [only (split-header (header-value request "x-inertia-partial-data"))
          except (split-header (header-value request "x-inertia-partial-except"))]
      (cond
        (seq except) (into {} (remove (partial selected-prop? except)) props)
        (seq only) (into {} (filter (partial selected-prop? only)) props)
        :else props))
    props))

(defn- apply-once-props [request page-options props]
  (let [loaded (split-header (header-value request "x-inertia-except-once-props"))
        once-props (:once-props page-options)]
    (if (and (inertia-request? request) (seq loaded) (seq once-props))
      (reduce-kv
       (fn [remaining key config]
         (if (contains? loaded (prop-key-name key))
           (dissoc remaining (keyword (or (:prop config) key)) (or (:prop config) key))
           remaining))
       props
       once-props)
      props)))

(defn- ensure-errors [props]
  (if (or (contains? props :errors) (contains? props "errors"))
    props
    (assoc props :errors {})))

(defn- page-option-entries [options]
  (dissoc options :status :headers :props))

(defn- build-page [request raw-page shared-props version]
  (let [page-options (:options raw-page)
        shared (call-value request shared-props)
        raw-props (merge (or shared {}) (:props raw-page))
        partial-props (apply-partial-reload request (:component raw-page) raw-props)
        once-filtered-props (apply-once-props request page-options partial-props)
        props (->> once-filtered-props
                   (resolve-props request)
                   ensure-errors)
        page (merge {:component (:component raw-page)
                     :props props
                     :url (:url raw-page)
                     :version (current-version request version)}
                    (page-option-entries page-options))]
    (into {} (remove (comp nil? val)) page)))

(defn- vary-with [response value]
  (update response :headers
          (fn [headers]
            (let [headers (or headers {})
                  existing (or (get headers "Vary") (get headers "vary"))]
              (assoc headers "Vary"
                     (if (seq existing)
                       (if (some #{value} (map str/trim (str/split existing #",")))
                         existing
                         (str existing ", " value))
                       value))))))

(defn- inertia-json-response [response page]
  (-> response
      (assoc :body (json page))
      (assoc-in [:headers "Content-Type"] "application/json; charset=utf-8")
      (assoc-in [:headers "X-Inertia"] "true")
      (vary-with "X-Inertia")))

(defn- default-template [{:keys [page root-id title asset-tags]}]
  (str "<!doctype html>\n"
       "<html lang=\"en\">\n"
       "<head>\n"
       "  <meta charset=\"utf-8\">\n"
       "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
       "  <title>" (html-escape (or title "Inertia Ring")) "</title>\n"
       (apply str (map #(str "  " % "\n") asset-tags))
       "</head>\n"
       "<body>\n"
       "  <script data-page=\"" (html-escape root-id)
       "\" type=\"application/json\">" (safe-json-for-html page) "</script>\n"
       "  <div id=\"" (html-escape root-id) "\"></div>\n"
       "</body>\n"
       "</html>\n"))

(defn- html-response [response template template-data]
  (-> response
      (assoc :body (template template-data))
      (assoc-in [:headers "Content-Type"] "text/html; charset=utf-8")
      (vary-with "X-Inertia")))

(defn- inertia-page-response? [response]
  (and (map? (:body response))
       (contains? (:body response) page-key)))

(defn- normalize-redirect-status [request response]
  (if (and (inertia-request? request)
           (#{:put :patch :delete} (:request-method request))
           (= 302 (:status response)))
    (assoc response :status 303)
    response))

(defn wrap-inertia
  "Ring middleware for the Inertia protocol.

  Supported options:
  :version       string/number or (fn [request]) for asset versioning
  :shared-props  map or (fn [request]) merged into every page props
  :root-id       root element id, defaults to \"app\"
  :title         HTML title for the default template
  :asset-tags    HTML tags inserted into the default template head
  :template      fn receiving {:page :root-id :title :asset-tags :request}"
  ([handler]
   (wrap-inertia handler nil))
  ([handler {:keys [version shared-props root-id title asset-tags template]
             :or {root-id "app"
                  shared-props {}
                  template default-template}}]
   (fn [request]
     (if (version-mismatch? request version)
       (external-redirect (absolute-request-url request))
       (let [response (normalize-redirect-status request (handler request))]
         (if (inertia-page-response? response)
           (let [raw-page (get (:body response) page-key)
                 page (build-page request raw-page shared-props version)]
             (if (inertia-request? request)
               (inertia-json-response response page)
               (html-response response template {:page page
                                                 :root-id root-id
                                                 :title title
                                                 :asset-tags asset-tags
                                                 :request request})))
           response))))))
