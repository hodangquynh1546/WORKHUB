/** "An đang nhập…", "An và Bình đang nhập…" or "Nhiều người đang nhập…". */
export function typingLabel(names: string[]): string {
  return names.length === 1 ? `${names[0]} đang nhập…` : names.length === 2 ? `${names[0]} và ${names[1]} đang nhập…` : 'Nhiều người đang nhập…'
}
