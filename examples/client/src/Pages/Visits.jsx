import { Link } from '@inertiajs/react'

export default function Visits({ count }) {
  return (
    <main className="shell compact">
      <section className="panel statement">
        <p className="eyebrow">Partial page example</p>
        <h1>Visit count: {count}</h1>
        <Link className="button primary" href="/">
          Back home
        </Link>
      </section>
    </main>
  )
}
