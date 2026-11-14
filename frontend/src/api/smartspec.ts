/** Where SmartSpec's web app lives (links back from content that SmartSpec owns). */
export const SMARTSPEC_URL = ((import.meta.env.VITE_SMARTSPEC_URL as string | undefined) ?? 'http://localhost:5174').replace(/\/$/, '')

/** smartspec:<usecase|module|testcase|design>/<project>/<key> → the SmartSpec project it came from. */
export function smartspecProjectOf(externalRef: string | null | undefined): string | null {
  const match = externalRef?.match(/^smartspec:[a-z]+\/([^/]+)\//)
  return match ? match[1] : null
}

export const smartspecProjectUrl = (projectId: string, tab = 'editor') => `${SMARTSPEC_URL}/project/${projectId}/${tab}`
