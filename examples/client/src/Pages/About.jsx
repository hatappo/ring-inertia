import { Link } from '@inertiajs/react'

export default function About({ appName, description }) {
  return (
    <main className="shell compact">
      <section className="panel statement">
        <p className="eyebrow">{appName}</p>
        <h1>About this adapter</h1>
        <p>{description}</p>
        <Link className="button primary" href="/">
          Back home
        </Link>
      </section>
    </main>
  )
}
