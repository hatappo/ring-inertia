import { Link, router } from '@inertiajs/react'
import { useState } from 'react'

export default function Home({ appName, todos, todoStats, serverTime }) {
  const [title, setTitle] = useState('')

  function createTodo(event) {
    event.preventDefault()

    const trimmedTitle = title.trim()

    if (!trimmedTitle) {
      return
    }

    router.post(`/todos?title=${encodeURIComponent(trimmedTitle)}`, {}, {
      preserveScroll: true,
      onSuccess: () => setTitle(''),
    })
  }

  return (
    <main className="shell">
      <section className="hero">
        <p className="eyebrow">{appName}</p>
        <h1>Todo workbench over the Inertia protocol.</h1>
        <p className="lede">
          Create, complete, and delete todos with Ring handlers while keeping
          the server-time and redirect protocol examples close at hand.
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

      <div className="side-stack">
        <section className="panel time-card">
          <span className="label">Server time</span>
          <strong>{serverTime}</strong>
        </section>

        <section className="panel">
          <div className="section-heading">
            <span className="label">Todos</span>
          </div>

          <div className="todo-stats" aria-label="Todo stats">
            <span>
              <strong>{todoStats.total}</strong>
              total
            </span>
            <span>
              <strong>{todoStats.open}</strong>
              open
            </span>
            <span>
              <strong>{todoStats.done}</strong>
              done
            </span>
          </div>

          <form className="todo-form" onSubmit={createTodo}>
            <label className="visually-hidden" htmlFor="new-todo">
              New todo
            </label>
            <div>
              <input
                id="new-todo"
                name="title"
                placeholder="Write a task for the Ring server"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
              />
              <button className="button primary" type="submit">
                Add
              </button>
            </div>
          </form>

          <ul className="todos">
            {todos.map((todo) => (
              <li key={todo.id} className={todo.done ? 'done' : ''}>
                <div className="todo-title">
                  <span className="todo-badge">{todo.done ? 'done' : 'open'}</span>
                  <span>{todo.title}</span>
                </div>
                <div className="todo-actions">
                  <button
                    className="text-button"
                    type="button"
                    onClick={() => router.patch(`/todos/${todo.id}/toggle`, {}, { preserveScroll: true })}
                  >
                    {todo.done ? 'Reopen' : 'Complete'}
                  </button>
                  <button
                    className="text-button danger"
                    type="button"
                    onClick={() => router.delete(`/todos/${todo.id}`, { preserveScroll: true })}
                  >
                    Delete
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </main>
  )
}
