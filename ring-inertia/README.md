# ring-inertia

Ring middleware for the Inertia v3 protocol.

`ring-inertia` currently implements the protocol basics needed for a Clojure
Ring server and an Inertia client to communicate:

- Initial full-page HTML responses with a JSON page object in
  `<script type="application/json" data-page="app">`
- XHR Inertia responses using `X-Inertia: true`, JSON bodies, and
  `Vary: X-Inertia`
- Asset version mismatch handling with `409 Conflict` and
  `X-Inertia-Location`
- Partial reload filtering through `X-Inertia-Partial-*` headers
- `302` to `303` normalization for Inertia `PUT`, `PATCH`, and `DELETE`
  redirects

## Usage

```clojure
(ns app
  (:require [ring.inertia :as inertia]))

(defn routes [request]
  (case [(:request-method request) (:uri request)]
    [:get "/"] (inertia/render request "Home" {:message "Hello from Ring"})
    {:status 404 :headers {} :body "Not found"}))

(def app
  (inertia/wrap-inertia
   routes
   {:version "app-v1"
    :asset-tags ["<script type=\"module\" src=\"http://localhost:5173/src/main.jsx\"></script>"]}))
```

## Limitations

`ring-inertia` is currently an MVP server adapter. It covers the protocol paths
needed by the examples, but it is not yet a complete implementation of every
Inertia server-side feature.

Implemented:

- Initial HTML responses with embedded Inertia page data.
- JSON Inertia responses for XHR visits.
- Shared props.
- Lazy prop resolution via functions and delays.
- Partial reload filtering with `X-Inertia-Partial-Data` and
  `X-Inertia-Partial-Except`.
- Asset version mismatch responses.
- External redirects and fragment redirects.
- `302` to `303` redirect normalization for Inertia `PUT`, `PATCH`, and
  `DELETE` requests.

Not implemented yet:

- Dedicated helpers for optional, always, deferred, mergeable, prepend, append,
  and deep-merge props.
- Validation error/session helpers.
- CSRF integration helpers.
- SSR integration.
- History encryption support.
- A configurable JSON encoder/decoder hook.
- Package metadata and release automation for publishing the adapter.
