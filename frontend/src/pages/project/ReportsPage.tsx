import { useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useResource } from '../../api/useResource'
import type {
  BugTrend,
  ChartDay,
  LeadTime,
  Sprint,
  SprintCharts,
  Velocity,
  Workload,
} from '../../api/types'
import { Avatar } from '../../components/Avatar'
import { Alert } from '../../components/Form'
import { PageSkeleton } from '../../components/Skeleton'
import { ChartShell } from '../../components/charts/ChartShell'
import { ColumnChart } from '../../components/charts/ColumnChart'
import { ScatterChart, type ScatterPoint } from '../../components/charts/ScatterChart'
import { TimeSeriesChart } from '../../components/charts/TimeSeriesChart'
import { fmt } from '../../components/charts/chartUtils'
import { useProject } from './projectContext'

const VIEWS = [
  { id: 'sprint', label: 'Sprint', scrum: true },
  { id: 'velocity', label: 'Velocity', scrum: true },
  { id: 'flow', label: 'Thời gian xử lý', scrum: false },
  { id: 'workload', label: 'Khối lượng việc', scrum: false },
  { id: 'bugs', label: 'Xu hướng bug', scrum: false },
]

export function ReportsPage() {
  const { project } = useProject()
  const [params] = useSearchParams()
  const scrum = project.methodology === 'SCRUM'
  const views = VIEWS.filter((v) => scrum || !v.scrum)
  const view = views.find((v) => v.id === params.get('view'))?.id ?? views[0].id

  return (
    <div className="stack-lg">
      <nav className="tabs" aria-label="Loại báo cáo">
        {views.map((v) => (
          <Link key={v.id} to={`?view=${v.id}`} className={v.id === view ? 'active' : ''} replace>
            {v.label}
          </Link>
        ))}
      </nav>
      {view === 'sprint' && <SprintReports projectId={project.id} />}
      {view === 'velocity' && <VelocityReport projectId={project.id} />}
      {view === 'flow' && <FlowReport projectId={project.id} />}
      {view === 'workload' && <WorkloadReport projectId={project.id} />}
      {view === 'bugs' && <BugReport projectId={project.id} />}
    </div>
  )
}

// ---------- shared bits ----------

const shortDay = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`
const longDay = (iso: string) =>
  new Date(`${iso}T00:00:00`).toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit' })

function Tile({ label, value, hint, warn }: { label: string; value: string; hint?: string; warn?: boolean }) {
  return (
    <div className={`stat ${warn ? 'stat-warn' : ''}`}>
      <strong>{value}</strong>
      <span>{label}</span>
      {hint && <span className="muted small">{hint}</span>}
    </div>
  )
}

function Empty({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="card empty-state">
      <h2>{title}</h2>
      <p className="muted">{children}</p>
    </section>
  )
}

function Loading({ error, data }: { error: string | null; data: unknown }) {
  if (error) return <Alert>{error}</Alert>
  return data ? null : <PageSkeleton />
}

// ---------- Sprint: burndown, burnup, cumulative flow ----------

function SprintReports({ projectId }: { projectId: string }) {
  const list = useResource<Sprint[]>(`/api/projects/${projectId}/sprints?status=ACTIVE&status=COMPLETED`)
  const [picked, setPicked] = useState<string | null>(null)
  const [unit, setUnit] = useState<'points' | 'issues'>('points')
  const sprints = list.data ?? []
  const selected = picked ?? sprints.find((s) => s.status === 'ACTIVE')?.id ?? sprints[0]?.id ?? null

  if (list.error) return <Alert>{list.error}</Alert>
  if (!list.data) return <PageSkeleton />
  if (!selected) {
    return (
      <Empty title="Chưa có sprint nào để báo cáo">
        Biểu đồ xuất hiện khi bạn bắt đầu sprint đầu tiên. <Link to="../sprints">Đi tới Sprint</Link>
      </Empty>
    )
  }

  return (
    <>
      <div className="filters">
        <select value={selected} onChange={(e) => setPicked(e.target.value)} aria-label="Chọn sprint">
          {sprints.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name} ({s.status === 'ACTIVE' ? 'đang chạy' : 'đã xong'})
            </option>
          ))}
        </select>
        <div className="segmented-control" role="group" aria-label="Đơn vị">
          <button type="button" className={unit === 'points' ? 'on' : ''} onClick={() => setUnit('points')}>
            Story Points
          </button>
          <button type="button" className={unit === 'issues' ? 'on' : ''} onClick={() => setUnit('issues')}>
            Số công việc
          </button>
        </div>
      </div>
      <SprintChartsLoader key={selected} sprintId={selected} unit={unit} />
    </>
  )
}

function SprintChartsLoader({ sprintId, unit }: { sprintId: string; unit: 'points' | 'issues' }) {
  const { data, error } = useResource<SprintCharts>(`/api/sprints/${sprintId}/charts`)
  if (!data) return <Loading error={error} data={data} />
  if (!data.started) {
    return (
      <Empty title="Sprint chưa bắt đầu">
        Biểu đồ được vẽ từ dữ liệu ghi lại mỗi ngày kể từ lúc bắt đầu sprint, nên chưa có gì để hiển thị.
      </Empty>
    )
  }
  return <SprintChartsView data={data} unit={unit} />
}

function SprintChartsView({ data, unit }: { data: SprintCharts; unit: 'points' | 'issues' }) {
  const days = data.days
  const labels = days.map((d) => shortDay(d.date))
  const titles = days.map((d) => longDay(d.date))
  const pts = unit === 'points'
  const word = pts ? 'điểm' : 'việc'
  const ideal = (d: ChartDay) => (pts ? d.idealPoints : d.idealIssues)
  const scope = (d: ChartDay) => (pts ? d.scopePoints : d.scopeIssues)
  const done = (d: ChartDay) => (pts ? d.donePoints : d.doneIssues)
  const remaining = (d: ChartDay) => (pts ? d.remainingPoints : d.remainingIssues)

  const latest = days.filter((d) => d.actual).at(-1)
  const committed = pts ? data.committedPoints : data.committedIssues
  const behind = latest ? (remaining(latest) ?? 0) - ideal(latest) : 0
  const added = latest && committed != null ? (scope(latest) ?? 0) - committed : 0

  return (
    <>
      {latest && (
        <div className="stat-grid">
          <Tile label="Cam kết lúc bắt đầu" value={committed != null ? fmt(committed) : '–'} hint={word} />
          <Tile label="Đã hoàn thành" value={fmt(done(latest) ?? 0)} hint={word} />
          <Tile label="Còn lại" value={fmt(remaining(latest) ?? 0)} hint={word} />
          <Tile
            label="Phạm vi thay đổi"
            value={`${added > 0 ? '+' : ''}${fmt(added)}`}
            hint="so với cam kết"
            warn={added > 0}
          />
          <Tile
            label={behind > 0.5 ? 'Chậm hơn kế hoạch' : behind < -0.5 ? 'Nhanh hơn kế hoạch' : 'Theo kế hoạch'}
            value={Math.abs(behind) < 0.5 ? '0' : fmt(Math.abs(Math.round(behind * 10) / 10))}
            hint={word}
            warn={behind > 0.5}
          />
        </div>
      )}
      <div className="chart-grid-2">
        <ChartShell
          title="Burndown"
          note="Công việc còn lại theo ngày. Đường thực tế nằm trên đường lý tưởng là chậm hơn kế hoạch, nằm dưới là nhanh hơn."
          legend={[
            { name: 'Lý tưởng', tone: 'mid', dash: true },
            { name: 'Thực tế còn lại', tone: 'ink' },
          ]}
          head={['Ngày', 'Lý tưởng', 'Còn lại']}
          rows={days.map((d) => [longDay(d.date), fmt(ideal(d)), remaining(d) == null ? '–' : fmt(remaining(d) as number)])}
        >
          <TimeSeriesChart
            label={`Burndown ${data.name}`}
            labels={labels}
            titles={titles}
            series={[
              { id: 'ideal', name: 'Lý tưởng', values: days.map(ideal), tone: 'mid', dash: true },
              { id: 'remaining', name: 'Thực tế còn lại', values: days.map(remaining), tone: 'ink' },
            ]}
          />
        </ChartShell>
        <ChartShell
          title="Burnup"
          note="Khoảng cách giữa hai đường là việc còn lại. Đường phạm vi đi lên nghĩa là phạm vi sprint đã tăng."
          legend={[
            { name: 'Phạm vi', tone: 'mid' },
            { name: 'Đã hoàn thành', tone: 'ink' },
          ]}
          head={['Ngày', 'Phạm vi', 'Đã hoàn thành']}
          rows={days.map((d) => [
            longDay(d.date),
            scope(d) == null ? '–' : fmt(scope(d) as number),
            done(d) == null ? '–' : fmt(done(d) as number),
          ])}
        >
          <TimeSeriesChart
            label={`Burnup ${data.name}`}
            labels={labels}
            titles={titles}
            series={[
              { id: 'scope', name: 'Phạm vi', values: days.map(scope), tone: 'mid' },
              { id: 'done', name: 'Đã hoàn thành', values: days.map(done), tone: 'ink' },
            ]}
          />
        </ChartShell>
      </div>
      <ChartShell
        title="Cumulative flow"
        note="Số công việc ở từng trạng thái theo ngày. Dải nào phình rộng ra là nơi công việc đang dồn lại."
        legend={[
          { name: 'Hoàn thành', tone: 'ink', area: true },
          { name: 'Đang làm', tone: 'mid', area: true },
          { name: 'Cần làm', tone: 'soft', area: true },
        ]}
        head={['Ngày', 'Cần làm', 'Đang làm', 'Hoàn thành']}
        rows={days.map((d) => [longDay(d.date), d.todoIssues ?? '–', d.inProgressIssues ?? '–', d.doneIssues ?? '–'])}
      >
        <TimeSeriesChart
          label={`Cumulative flow ${data.name}`}
          labels={labels}
          titles={titles}
          series={[
            { id: 'done', name: 'Hoàn thành', values: days.map((d) => d.doneIssues), tone: 'ink', area: true },
            { id: 'doing', name: 'Đang làm', values: days.map((d) => d.inProgressIssues), tone: 'mid', area: true },
            { id: 'todo', name: 'Cần làm', values: days.map((d) => d.todoIssues), tone: 'soft', area: true },
          ]}
        />
      </ChartShell>
    </>
  )
}

// ---------- Velocity ----------

function VelocityReport({ projectId }: { projectId: string }) {
  const { data, error } = useResource<Velocity>(`/api/projects/${projectId}/reports/velocity`)
  if (!data) return <Loading error={error} data={data} />
  if (data.sprints.length === 0) {
    return (
      <Empty title="Chưa có sprint nào hoàn thành">
        Velocity xuất hiện sau khi bạn hoàn thành sprint đầu tiên. <Link to="../sprints">Đi tới Sprint</Link>
      </Empty>
    )
  }
  const last = data.sprints.at(-1)!
  return (
    <>
      <div className="stat-grid">
        <Tile label="Trung bình 3 sprint gần nhất" value={data.average != null ? fmt(data.average) : '–'} hint="điểm mỗi sprint" />
        <Tile label={`Sprint gần nhất: ${last.name}`} value={fmt(last.completedPoints)} hint={`trên ${last.committedPoints} điểm cam kết`} />
        <Tile label="Số sprint đã hoàn thành" value={String(data.sprints.length)} hint="hiển thị tối đa 10 gần nhất" />
      </div>
      <ChartShell
        title="Velocity"
        note="Story Points hoàn thành qua từng sprint. Chỉ dùng để dự báo năng lực của chính team này, không để so sánh với team khác."
        legend={[
          { name: 'Cam kết', tone: 'soft', area: true },
          { name: 'Hoàn thành', tone: 'ink', area: true },
        ]}
        head={['Sprint', 'Cam kết (điểm)', 'Hoàn thành (điểm)', 'Cam kết (việc)', 'Hoàn thành (việc)']}
        rows={data.sprints.map((s) => [s.name, s.committedPoints, s.completedPoints, s.committedIssues, s.completedIssues])}
      >
        <ColumnChart
          label="Velocity qua các sprint"
          categories={data.sprints.map((s) => s.name)}
          series={[
            { id: 'committed', name: 'Cam kết', values: data.sprints.map((s) => s.committedPoints), tone: 'soft' },
            { id: 'completed', name: 'Hoàn thành', values: data.sprints.map((s) => s.completedPoints), tone: 'ink' },
          ]}
          reference={data.average != null ? { value: data.average, name: 'Trung bình' } : undefined}
          extra={(i) => (
            <div className="muted small">
              {data.sprints[i].completedIssues}/{data.sprints[i].committedIssues} công việc
            </div>
          )}
        />
      </ChartShell>
    </>
  )
}

// ---------- Lead time and cycle time ----------

const epochDay = (iso: string) => Math.floor(Date.parse(`${iso}T00:00:00Z`) / 86_400_000)
const fromEpochDay = (day: number) => {
  const d = new Date(day * 86_400_000)
  return `${String(d.getUTCDate()).padStart(2, '0')}/${String(d.getUTCMonth() + 1).padStart(2, '0')}`
}

function FlowReport({ projectId }: { projectId: string }) {
  const [days, setDays] = useState(90)
  const { data, error } = useResource<LeadTime>(`/api/projects/${projectId}/reports/lead-time?days=${days}`)
  const filter = (
    <div className="filters">
      <select value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label="Khoảng thời gian">
        <option value={30}>30 ngày qua</option>
        <option value={90}>90 ngày qua</option>
        <option value={180}>180 ngày qua</option>
        <option value={365}>1 năm qua</option>
      </select>
    </div>
  )
  if (!data) return <Loading error={error} data={data} />
  const s = data.summary
  if (s.count === 0) {
    return (
      <>
        {filter}
        <Empty title="Chưa có công việc nào hoàn thành trong khoảng này">
          Lead time và cycle time được tính từ lúc tạo hoặc bắt đầu làm đến lúc chuyển sang Done.
        </Empty>
      </>
    )
  }
  const xs = data.points.map((p) => epochDay(p.doneDate))
  const xMax = Math.max(...xs)
  const points: ScatterPoint[] = data.points.flatMap((p) => {
    const x = epochDay(p.doneDate)
    const detail = `Lead ${fmt(p.leadDays)} ngày${p.cycleDays != null ? ` · Cycle ${fmt(p.cycleDays)} ngày` : ''}`
    const out: ScatterPoint[] = [{ id: `${p.issueId}-lead`, x, y: p.leadDays, tone: 'soft', title: `${p.key} ${p.title}`, detail }]
    if (p.cycleDays != null) out.push({ id: `${p.issueId}-cycle`, x, y: p.cycleDays, tone: 'ink', title: `${p.key} ${p.title}`, detail })
    return out
  })
  const day = (v: number | null) => (v == null ? '–' : `${fmt(v)} ngày`)
  return (
    <>
      {filter}
      <div className="stat-grid">
        <Tile label="Công việc đã hoàn thành" value={String(s.count)} hint={`trong ${data.days} ngày`} />
        <Tile label="Cycle time trung vị" value={day(s.medianCycle)} hint="từ lúc bắt đầu làm" />
        <Tile label="Cycle time p85" value={day(s.p85Cycle)} hint="85% xong trong thời gian này" />
        <Tile label="Lead time trung vị" value={day(s.medianLead)} hint="từ lúc tạo" />
      </div>
      <ChartShell
        title="Lead time và Cycle time"
        note="Mỗi chấm là một công việc, đặt tại ngày hoàn thành. Lead time tính từ lúc tạo, cycle time tính từ lúc bắt đầu làm."
        legend={[
          { name: 'Cycle time', tone: 'ink', dot: true },
          { name: 'Lead time', tone: 'soft', dot: true },
        ]}
        head={['Công việc', 'Ngày xong', 'Lead (ngày)', 'Cycle (ngày)']}
        rows={data.points.map((p) => [`${p.key} ${p.title}`, p.doneDate, fmt(p.leadDays), p.cycleDays == null ? '–' : fmt(p.cycleDays)])}
      >
        <ScatterChart
          label="Lead time và cycle time"
          points={points}
          xMin={xMax - data.days}
          xMax={xMax}
          formatX={fromEpochDay}
          lines={s.p85Cycle != null ? [{ value: s.p85Cycle, name: 'Cycle p85', tone: 'ink' }] : []}
        />
      </ChartShell>
    </>
  )
}

// ---------- Workload ----------

function WorkloadReport({ projectId }: { projectId: string }) {
  const { data, error } = useResource<Workload>(`/api/projects/${projectId}/reports/workload`)
  if (!data) return <Loading error={error} data={data} />
  if (data.rows.length === 0) {
    return <Empty title="Không còn việc nào đang mở">Tất cả công việc đã hoàn thành, hoặc dự án chưa có công việc.</Empty>
  }
  const max = Math.max(...data.rows.map((r) => r.todoIssues + r.inProgressIssues))
  const name = (r: Workload['rows'][number]) => r.fullName ?? 'Chưa giao'
  return (
    <ChartShell
      title="Khối lượng việc theo người"
      note="Công việc chưa hoàn thành, không gồm Epic. Người nhiều việc nhất ở trên cùng."
      legend={[
        { name: 'Đang làm', tone: 'ink', area: true },
        { name: 'Cần làm', tone: 'soft', area: true },
      ]}
      head={['Người', 'Cần làm', 'Đang làm', 'Điểm đang mở']}
      rows={data.rows.map((r) => [name(r), r.todoIssues, r.inProgressIssues, r.openPoints])}
    >
      <ul className="workload">
        {data.rows.map((r) => (
          <li key={r.userId ?? 'none'} className="workload-row">
            <span className="workload-person">
              <Avatar name={name(r)} url={r.avatarUrl} />
              <span className={r.fullName ? '' : 'muted'}>{name(r)}</span>
            </span>
            <span
              className="workload-bar"
              role="img"
              aria-label={`${name(r)}: ${r.inProgressIssues} đang làm, ${r.todoIssues} cần làm`}
            >
              <span className="workload-seg workload-doing" style={{ flexGrow: r.inProgressIssues }} />
              <span className="workload-seg workload-todo" style={{ flexGrow: r.todoIssues }} />
              <span style={{ flexGrow: max - r.todoIssues - r.inProgressIssues }} />
            </span>
            <span className="workload-count">
              <b>{r.todoIssues + r.inProgressIssues}</b> việc · {r.openPoints} điểm
            </span>
          </li>
        ))}
      </ul>
    </ChartShell>
  )
}

// ---------- Bug trend ----------

function BugReport({ projectId }: { projectId: string }) {
  const [weeks, setWeeks] = useState(8)
  const { data, error } = useResource<BugTrend>(`/api/projects/${projectId}/reports/bug-trend?weeks=${weeks}`)
  if (!data) return <Loading error={error} data={data} />
  const now = data.weeks.at(-1)!
  const total = data.weeks.reduce((sum, w) => sum + w.created, 0)
  return (
    <>
      <div className="filters">
        <select value={weeks} onChange={(e) => setWeeks(Number(e.target.value))} aria-label="Số tuần">
          <option value={8}>8 tuần</option>
          <option value={12}>12 tuần</option>
          <option value={26}>26 tuần</option>
        </select>
      </div>
      <div className="stat-grid">
        <Tile label="Bug đang mở" value={String(now.open)} hint="cuối tuần này" warn={now.open > 0 && now.created > now.resolved} />
        <Tile label="Phát sinh tuần này" value={String(now.created)} />
        <Tile label="Đã xử lý tuần này" value={String(now.resolved)} />
        <Tile label={`Phát sinh trong ${weeks} tuần`} value={String(total)} />
      </div>
      <ChartShell
        title="Xu hướng bug"
        note="Bug mới phát sinh và bug đã xử lý theo tuần (tuần bắt đầu từ thứ Hai). Số bug còn mở cuối mỗi tuần nằm trong chú giải khi rê chuột."
        legend={[
          { name: 'Phát sinh', tone: 'soft', area: true },
          { name: 'Đã xử lý', tone: 'ink', area: true },
        ]}
        head={['Tuần bắt đầu', 'Phát sinh', 'Đã xử lý', 'Còn mở cuối tuần']}
        rows={data.weeks.map((w) => [w.weekStart, w.created, w.resolved, w.open])}
      >
        <ColumnChart
          label="Bug phát sinh và đã xử lý theo tuần"
          categories={data.weeks.map((w) => shortDay(w.weekStart))}
          series={[
            { id: 'created', name: 'Phát sinh', values: data.weeks.map((w) => w.created), tone: 'soft' },
            { id: 'resolved', name: 'Đã xử lý', values: data.weeks.map((w) => w.resolved), tone: 'ink' },
          ]}
          extra={(i) => <div className="muted small">Còn mở cuối tuần: {data.weeks[i].open}</div>}
        />
      </ChartShell>
    </>
  )
}
