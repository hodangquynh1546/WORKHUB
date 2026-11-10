import { useCallback, useEffect, useState } from 'react'
import { api } from './client'
import { errorMessage } from './errors'

/** Loads `path` with api(); `reload()` fetches again (old data stays on screen meanwhile). */
export function useResource<T>(path: string) {
  const [state, setState] = useState<{ data: T | null; error: string | null }>({ data: null, error: null })
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let cancelled = false
    api<T>(path)
      .then((data) => !cancelled && setState({ data, error: null }))
      .catch((err) => !cancelled && setState({ data: null, error: errorMessage(err) }))
    return () => {
      cancelled = true
    }
  }, [path, version])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  return { ...state, reload }
}
