import { Link, router } from '@inertiajs/react'

export default function Home({ appName, todos, serverTime }) {
  return (
    <main className="shell">
      <section className="hero">
        <p className="eyebrow">{appName}</p>
        <h1>Ring handlers, React pages, one Inertia protocol.</h1>
        <p className="lede">
          This page starts as HTML from Ring, then navigates through Inertia XHR
          responses without a client-side router.
        </p>
        <div className="actions">
          <Link className="button primary" href="/about">
            Open About
          </Link>
          <button
            className="button secondary"
            type="button"
            onClick={() => router.patch('/messages')}
          >
            PATCH then redirect
          </button>
          <Link className="button secondary" href="/external">
            External redirect
          </Link>
          <button
            className="button secondary"
            type="button"
            onClick={() => router.reload({ only: ['serverTime'] })}
          >
            Reload server time
          </button>
        </div>
      </section>

      <section className="panel">
        <div>
          <span className="label">Server time</span>
          <strong>{serverTime}</strong>
        </div>
        <ul className="todos">
          {todos.map((todo) => (
            <li key={todo.id} className={todo.done ? 'done' : ''}>
              <span>{todo.title}</span>
            </li>
          ))}
        </ul>
      </section>
    </main>
  )
}
