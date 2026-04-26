import { createInertiaApp } from '@inertiajs/react'
import { createRoot } from 'react-dom/client'
import './styles.css'

const pages = import.meta.glob('./Pages/**/*.jsx')

if (document.querySelector('script[data-page="app"]')) {
  createInertiaApp({
    resolve: (name) => pages[`./Pages/${name}.jsx`](),
    setup({ el, App, props }) {
      createRoot(el).render(<App {...props} />)
    },
  })
} else {
  document.getElementById('app').innerHTML = `
    <main class="shell compact">
      <section class="panel statement">
        <p class="eyebrow">Vite dev server is running</p>
        <h1>Open the Ring server URL</h1>
        <p>
          This React entry expects an Inertia page object rendered by Ring.
          Start <code>bb server-ring:dev</code>, then open
          <a href="http://localhost:3000">http://localhost:3000</a>.
        </p>
      </section>
    </main>
  `
}
