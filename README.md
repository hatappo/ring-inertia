# inertia adapters

Monorepo for Inertia adapters and examples.

## Packages

- `ring-inertia`: Ring middleware for the Inertia v3 protocol.
- `replicant-inertia`: Replicant client adapter for Inertia.
- `examples/client-react`: React/Vite example client.
- `examples/client-replicant`: Replicant/shadow-cljs example client.
- `examples/server-ring`: Ring example server.

## Development

Run checks:

```sh
bb check
```

## Sample Application

All commands below are run from the repository root.

### React Client

Start the React/Vite client sample:

```sh
bb client-react:install
bb client-react:dev
```

In another terminal, start the Ring server sample:

```sh
bb server-ring:dev
```

Open <http://localhost:3000>. Do not open the Vite dev server URL
`http://localhost:5173` directly; it only serves the React assets, and the
Inertia page object is rendered by the Ring server.

### Replicant Client

Start the Replicant/shadow-cljs client sample:

```sh
bb client-replicant:install
bb client-replicant:dev
```

In another terminal, start the Ring server sample with Replicant assets:

```sh
bb server-ring:dev-replicant
```

Open <http://localhost:3000>. Do not open the shadow-cljs dev server URL
`http://localhost:5174` directly; it only serves Replicant assets, and the
Inertia page object is rendered by the Ring server.

Expected browser behavior:

- The initial `Home` page is served by Ring as HTML.
- Adding a todo sends an Inertia `POST` to the Ring server and redirects back to
  the todo list. The React page uses Inertia's `useForm`; the Replicant page
  uses the `replicant-inertia` navigation helpers. The Ring sample parses the
  JSON request body.
- Each todo can be completed, reopened, or deleted through Inertia `PATCH` and
  `DELETE` requests.
- `Open About` navigates through an Inertia XHR request.
- `Reload server time` performs a partial reload for `serverTime`.
- `PATCH then redirect` sends an Inertia `PATCH` to Ring. The sample route
  intentionally returns `302`, and the middleware normalizes it to `303`.
- `External redirect` returns `409` with `X-Inertia-Location`, then Inertia
  performs a browser-level visit to <https://inertiajs.com>.

Protocol-level checks:

```sh
curl -i http://localhost:3000/
curl -i -X POST -H 'X-Inertia: true' -H 'Content-Type: application/json' --data '{"title":"Write README"}' http://localhost:3000/todos
curl -i -X PATCH -H 'X-Inertia: true' http://localhost:3000/todos/3/toggle
curl -i -X DELETE -H 'X-Inertia: true' http://localhost:3000/todos/3
curl -i -H 'X-Inertia: true' -H 'X-Inertia-Version: dev' http://localhost:3000/about
curl -i -H 'X-Inertia: true' -H 'X-Inertia-Version: old' http://localhost:3000/about
curl -i -X PATCH -H 'X-Inertia: true' http://localhost:3000/messages
curl -i -H 'X-Inertia: true' http://localhost:3000/external
```

The first command should return the initial HTML page. The todo mutation
commands should return redirects back to `/`. The About command should return
`200` with `X-Inertia: true`. The stale-version command should return `409`
with `X-Inertia-Location`. The redirect sample should return `303` with
`Location: /`. The external redirect sample should return `409` with
`X-Inertia-Location: https://inertiajs.com`.

## References

- Inertia v3 protocol: <https://inertiajs.com/docs/v3/core-concepts/the-protocol>
- Inertia client setup: <https://inertiajs.com/docs/v3/installation/client-side-setup>
- Ring response helpers and response map conventions:
  <https://ring-clojure.github.io/ring/ring.util.response.html>
- Ring server adapter usage: [`ring-inertia/README.md`](ring-inertia/README.md)
- Replicant: <https://github.com/cjohansen/replicant>
- Replicant client adapter usage: [`replicant-inertia/README.md`](replicant-inertia/README.md)
