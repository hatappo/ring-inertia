(ns example.client-replicant.main
  (:require [clojure.string :as str]
            [replicant-inertia.core :as inertia]))

(defn- button [attrs & children]
  (into [:button (merge {:type "button"} attrs)] children))

(defn- link [attrs & children]
  (apply inertia/link attrs children))

(defn- submit-todo [event]
  (.preventDefault event)
  (let [form (.-currentTarget event)
        input (.querySelector form "input[name='title']")
        title (str/trim (.-value input))]
    (when-not (str/blank? title)
      (inertia/post! "/todos" {:title title} {:preserveScroll true
                                              :onSuccess #(set! (.-value input) "")}))))

(defn- todo-item [{:keys [id title done]}]
  [:li {:replicant/key id
        :class (when done [:done])}
   [:div {:class [:todo-title]}
    [:span {:class [:todo-badge]} (if done "done" "open")]
    [:span title]]
   [:div {:class [:todo-actions]}
    (button
     {:class [:text-button]
      :on {:click #(inertia/patch! (str "/todos/" id "/toggle")
                                   {}
                                   {:preserveScroll true})}}
     (if done "Reopen" "Complete"))
    (button
     {:class [:text-button :danger]
      :on {:click #(inertia/delete! (str "/todos/" id)
                                    {:preserveScroll true})}}
     "Delete")]])

(defn home [{:keys [appName todos todoStats serverTime]} _page]
  [:main {:class [:shell]}
   [:section {:class [:hero]}
    [:p {:class [:eyebrow]} appName]
    [:h1 "Todo workbench over the Inertia protocol."]
    [:p {:class [:lede]}
     "Create, complete, and delete todos with Ring handlers while keeping "
     "the server-time and redirect protocol examples close at hand."]
    [:div {:class [:actions]}
     (link {:class [:button :primary] :href "/about"} "Open About")
     (button
      {:class [:button :secondary]
       :on {:click #(inertia/patch! "/messages")}}
      "PATCH then redirect")
     (link {:class [:button :secondary] :href "/external"} "External redirect")
     (button
      {:class [:button :secondary]
       :on {:click #(inertia/reload! {:only ["serverTime"]})}}
      "Reload server time")]]
   [:div {:class [:side-stack]}
    [:section {:class [:panel :time-card]}
     [:span {:class [:label]} "Server time"]
     [:strong serverTime]]
    [:section {:class [:panel]}
     [:div {:class [:section-heading]}
      [:span {:class [:label]} "Todos"]]
     [:div {:class [:todo-stats] :aria-label "Todo stats"}
      [:span [:strong (:total todoStats)] "total"]
      [:span [:strong (:open todoStats)] "open"]
      [:span [:strong (:done todoStats)] "done"]]
     [:form {:class [:todo-form] :on {:submit submit-todo}}
      [:label {:class [:visually-hidden] :for "new-todo"} "New todo"]
      [:div
       [:input {:id "new-todo"
                :name "title"
                :placeholder "Write a task for the Ring server"}]
       [:button {:class [:button :primary] :type "submit"} "Add"]]]
     (into [:ul {:class [:todos]}] (map todo-item todos))]]])

(defn about [{:keys [appName description]} _page]
  [:main {:class [:shell :compact]}
   [:section {:class [:panel :statement]}
    [:p {:class [:eyebrow]} appName]
    [:h1 "About this adapter"]
    [:p description]
    (link {:class [:button :primary] :href "/"} "Back home")]])

(defn visits [{:keys [count]} _page]
  [:main {:class [:shell :compact]}
   [:section {:class [:panel :statement]}
    [:p {:class [:eyebrow]} "Partial page example"]
    [:h1 (str "Visit count: " count)]
    (link {:class [:button :primary] :href "/"} "Back home")]])

(def pages
  {"Home" home
   "About" about
   "Visits" visits})

(defn- resolve-page [name _page]
  (get pages name))

(defn init! []
  (inertia/create-inertia-app! {:id "app"
                                :resolve resolve-page}))
