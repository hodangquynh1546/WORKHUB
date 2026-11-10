import { useEffect, useState } from 'react'
import './App.css'

function App() {
  const [message, setMessage] = useState('Loading…')

  useEffect(() => {
    fetch('/api/hello')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json() as Promise<{ message: string }>
      })
      .then((data) => setMessage(data.message))
      .catch((err: Error) => setMessage(`Cannot reach backend: ${err.message}`))
  }, [])

  return (
    <main>
      <h1>ProjectInvidial</h1>
      <p>{message}</p>
    </main>
  )
}

export default App
