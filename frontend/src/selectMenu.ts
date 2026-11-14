// @ts-nocheck
/**
 * Thay menu xổ xuống GỐC của <select> (rất to, lệch cỡ, do hệ điều hành vẽ) bằng một menu gọn do ta vẽ — cho MỌI select
 * trong ứng dụng mà không cần sửa từng component. Chọn xong sẽ gán `select.value` và bắn sự kiện input/change nên
 * v-model / @change / onChange hoạt động như cũ. Bỏ qua select nhiều lựa chọn (multiple/size>1).
 */
let menu = null
let owner = null

function close() {
  if (menu) { menu.remove(); menu = null }
  owner = null
  document.removeEventListener('mousedown', onOutside, true)
  window.removeEventListener('scroll', onScroll, true)
  window.removeEventListener('resize', close)
}
function onOutside(e) { if (menu && !menu.contains(e.target) && e.target !== owner) close() }
function onScroll(e) { if (menu && !menu.contains(e.target)) close() }

function open(select) {
  close()
  owner = select
  const opts = Array.from(select.options).filter((o) => !o.hidden)
  menu = document.createElement('ul')
  menu.className = 'app-select-menu'
  menu.setAttribute('role', 'listbox')
  let focusIdx = Math.max(0, opts.findIndex((o) => o.selected))
  const items = opts.map((o, i) => {
    const li = document.createElement('li')
    li.setAttribute('role', 'option')
    li.className = (o.selected ? 'on ' : '') + (o.disabled ? 'off' : '')
    const mark = document.createElement('span'); mark.className = 'mark'; mark.textContent = o.selected ? '✓' : ''
    li.append(mark, document.createTextNode(o.textContent.trim() || ' '))
    if (!o.disabled) li.addEventListener('mousedown', (e) => { e.preventDefault(); e.stopPropagation(); pick(o) })
    menu.appendChild(li)
    return li
  })
  const pick = (o) => {
    if (select.value !== o.value) {
      select.value = o.value
      select.dispatchEvent(new Event('input', { bubbles: true }))
      select.dispatchEvent(new Event('change', { bubbles: true }))
    }
    close()
    select.focus()
  }
  document.body.appendChild(menu)
  const r = select.getBoundingClientRect()
  const h = Math.min(menu.offsetHeight, 320)
  menu.style.maxHeight = '320px'
  menu.style.minWidth = Math.max(r.width, 120) + 'px'
  const up = r.bottom + h + 8 > window.innerHeight && r.top > h + 8
  menu.style.left = Math.min(r.left, window.innerWidth - menu.offsetWidth - 8) + 'px'
  menu.style.top = (up ? r.top - h - 4 : r.bottom + 4) + 'px'
  const mark = () => items.forEach((li, i) => li.classList.toggle('hot', i === focusIdx))
  mark()
  items[focusIdx]?.scrollIntoView({ block: 'nearest' })
  menu.addEventListener('keydown', () => {})
  const key = (e) => {
    if (!menu) return select.removeEventListener('keydown', key, true)
    if (e.key === 'Escape') { e.preventDefault(); close() }
    else if (e.key === 'ArrowDown') { e.preventDefault(); focusIdx = Math.min(items.length - 1, focusIdx + 1); mark(); items[focusIdx].scrollIntoView({ block: 'nearest' }) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); focusIdx = Math.max(0, focusIdx - 1); mark(); items[focusIdx].scrollIntoView({ block: 'nearest' }) }
    else if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); if (!opts[focusIdx].disabled) pick(opts[focusIdx]) }
    else if (e.key === 'Tab') close()
  }
  select.addEventListener('keydown', key, true)
  document.addEventListener('mousedown', onOutside, true)
  window.addEventListener('scroll', onScroll, true)
  window.addEventListener('resize', close)
}

const eligible = (el) => el instanceof HTMLSelectElement && !el.disabled && !el.multiple && !(el.size > 1)

export function installSelectMenu() {
  document.addEventListener('mousedown', (e) => {
    const el = e.target
    if (!eligible(el) || e.button !== 0) return
    e.preventDefault() // chặn menu gốc
    el.focus()
    if (menu && owner === el) close(); else open(el)
  }, true)
  document.addEventListener('keydown', (e) => {
    const el = document.activeElement
    if (!eligible(el) || menu) return
    if (e.key === 'ArrowDown' && e.altKey || e.key === ' ' || e.key === 'Enter' && el.tagName === 'SELECT') { e.preventDefault(); open(el) }
  }, true)
}
