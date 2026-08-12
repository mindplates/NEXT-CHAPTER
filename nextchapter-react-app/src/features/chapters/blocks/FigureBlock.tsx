import type { Block } from '../api/chapterApi'

/**
 * 도표. `spec` 은 **이미지 바이너리가 아니라 명세**다 — 그래서 같은 명세를 웹은 SVG 로, PPT 는 슬라이드
 * 도형으로 그릴 수 있다.
 *
 * 모르는 명세는 <b>표로 떨어진다.</b> 아무것도 그리지 않으면 사용자는 콘텐츠가 빠진 것을 알 수 없고,
 * 예외를 던지면 챕터 전체가 안 보인다.
 */
export function FigureBlock({ block }: { readonly block: Block }) {
  const spec = block.attributes.spec as FigureSpec | undefined
  const points = readPoints(spec)

  if (!spec) {
    return <p role="alert">이 도표를 표시할 수 없습니다.</p>
  }

  return (
    <figure className="block block--figure">
      {points.length > 0 ? <BarChart points={points} /> : <SpecTable spec={spec} />}
      {spec.caption && <figcaption>{spec.caption}</figcaption>}
    </figure>
  )
}

interface FigureSpec {
  readonly type?: string
  readonly caption?: string
  readonly points?: unknown
  readonly series?: unknown
  readonly [key: string]: unknown
}

interface Point {
  readonly label: string
  readonly value: number
}

/**
 * `points`(라벨·값 쌍) 또는 `series`(값 배열) 중 있는 것을 읽는다. 생성기가 어느 쪽으로 줄지 확정되기
 * 전이라 둘 다 받는다 — 확정되면 하나로 줄인다.
 */
function readPoints(spec: FigureSpec | undefined): readonly Point[] {
  if (!spec) {
    return []
  }
  if (Array.isArray(spec.points)) {
    return spec.points
      .map((raw) => {
        const point = raw as { label?: unknown; value?: unknown }
        return { label: String(point.label ?? ''), value: Number(point.value) }
      })
      .filter((point) => Number.isFinite(point.value))
  }
  if (Array.isArray(spec.series)) {
    return spec.series
      .map((value, index) => ({ label: String(index + 1), value: Number(value) }))
      .filter((point) => Number.isFinite(point.value))
  }
  return []
}

/**
 * 막대 그래프를 SVG 로 직접 그린다. 차트 라이브러리를 도입하지 않은 이유는 그 선택이 되돌리기 어렵고
 * (번들 크기·렌더 방식·PPT 재사용 가능성), 지금 필요한 것은 명세가 화면에 닿는다는 확인이기 때문이다.
 */
function BarChart({ points }: { readonly points: readonly Point[] }) {
  const max = Math.max(...points.map((point) => point.value), 0)
  const width = 100
  const barHeight = 18
  const gap = 6

  return (
    <svg
      className="figure-chart"
      viewBox={`0 0 ${width} ${points.length * (barHeight + gap)}`}
      role="img"
      aria-label="막대 그래프"
    >
      {points.map((point, index) => (
        <g key={`${point.label}-${index}`} transform={`translate(0 ${index * (barHeight + gap)})`}>
          <rect
            x="22"
            y="0"
            height={barHeight}
            width={max === 0 ? 0 : ((width - 26) * point.value) / max}
            className="figure-bar"
          />
          <text x="0" y={barHeight * 0.75} className="figure-label">
            {point.label}
          </text>
        </g>
      ))}
    </svg>
  )
}

/** 그릴 수 없는 명세는 값이라도 보여 준다. 조용히 비우는 것보다 낫다. */
function SpecTable({ spec }: { readonly spec: FigureSpec }) {
  return (
    <table className="figure-table">
      <tbody>
        {Object.entries(spec)
          .filter(([key]) => key !== 'caption')
          .map(([key, value]) => (
            <tr key={key}>
              <th scope="row">{key}</th>
              <td>{typeof value === 'object' ? JSON.stringify(value) : String(value)}</td>
            </tr>
          ))}
      </tbody>
    </table>
  )
}
