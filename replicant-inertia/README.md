# replicant-inertia

Replicant client adapter for Inertia.

This package provides a small fetch-based Inertia client runtime for Replicant:

- Reads the initial Inertia page object from the server-rendered HTML.
- Resolves page components and renders them with `replicant.dom/render`.
- Sends Inertia visits with the required protocol headers.
- Handles `GET`, `POST`, `PATCH`, `DELETE`, partial reloads, redirects, and
  browser history for the example app.

## Usage

```clojure
(ns app.client
  (:require [replicant-inertia.core :as inertia]))

(defn home [{:keys [message]} _page]
  [:main
   [:p message]
   (inertia/link {:href "/about"} "About")])

(def pages {"Home" home})

(defn init! []
  (inertia/create-inertia-app!
   {:id "app"
    :resolve (fn [name _page] (get pages name))}))
```

## Limitations

`replicant-inertia` is currently an MVP client adapter. It intentionally uses a
small fetch-based runtime instead of depending on `@inertiajs/core` so Replicant
projects do not need Axios.

Implemented:

- Initial page hydration from the server-rendered Inertia page object.
- Replicant rendering through `create-inertia-app!`.
- Basic `GET`, `POST`, `PATCH`, and `DELETE` visits.
- Partial reload headers for `:only` and `:except`.
- Inertia redirects, external redirects, scroll preservation, and browser
  history for the sample app.
- A small `link` helper for Inertia navigation.

Not implemented yet:

- Progress indicators.
- Request cancellation and cancel tokens.
- Upload progress.
- Form/useForm-style helpers.
- Remembered local state.
- Head/title management.
- Prefetch, deferred props, polling, infinite scroll, and advanced merge
  behaviors.
- SSR support and history encryption.
