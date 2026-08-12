import type { Block } from '../api/chapterApi'
import { FigureBlock } from './FigureBlock'
import { QuizBlock } from './QuizBlock'

/**
 * 블록 하나를 그린다. **타입별 분기가 여기 한 곳에만 있다** — 흩어지면 블록 타입이 늘 때 빠뜨리는 곳이
 * 생기고, 그 블록은 조용히 화면에서 사라진다.
 *
 * 모르는 타입은 <b>표시할 수 없다고 알린다.</b> 조용히 건너뛰면 사용자는 콘텐츠가 빠진 것을 알 수 없다 —
 * 클라이언트가 서버보다 오래된 상태에서 그렇게 된다.
 */
export function BlockRenderer({
  block,
  onAnswer,
  quizResult,
  children,
}: {
  readonly block: Block
  readonly onAnswer: (blockId: string, choice: string) => void
  readonly quizResult?: { readonly correct: boolean }
  /** 블록마다 붙는 신호 도구(질문·오류 신고). 블록 옆에 두는 것이 신호 정밀도의 전제다. */
  readonly children?: React.ReactNode
}) {
  return (
    <div className="block-row" data-block-id={block.id}>
      <div className="block-row__content">{content(block, onAnswer, quizResult)}</div>
      <div className="block-row__tools">{children}</div>
    </div>
  )
}

function content(
  block: Block,
  onAnswer: (blockId: string, choice: string) => void,
  quizResult?: { readonly correct: boolean },
) {
  switch (block.type) {
    case 'HEADING':
      return <h2 className="block block--heading">{block.text}</h2>
    case 'PARAGRAPH':
      return <p className="block block--paragraph">{block.text}</p>
    case 'FORMULA':
      return <FormulaBlock block={block} />
    case 'FIGURE':
      return <FigureBlock block={block} />
    case 'QUIZ':
      return <QuizBlock block={block} onAnswer={onAnswer} result={quizResult} />
    case 'NARRATION':
      // 웹 응답에는 오지 않는다(서버가 형태별로 걸러 낸다). 안전망으로 두는 이유는 같은 엔드포인트로
      // 다른 형태를 요청할 수 있기 때문이다 — 그때 대본이 본문에 섞이면 같은 내용이 두 번 보인다.
      return null
    default:
      return <p role="alert">이 버전의 앱에서는 표시할 수 없는 블록입니다 ({block.type}).</p>
  }
}

/**
 * 수식. `text` 가 LaTeX 소스다.
 *
 * 지금은 소스를 그대로 보여 준다. 수식 렌더 라이브러리(KaTeX 등)를 넣는 것은 되돌리기 어려운 선택이라
 * (번들 크기·SSR·PPT 재사용) 형태 확장(M6)에서 함께 정한다 — 그때까지 사용자는 원문을 본다.
 */
function FormulaBlock({ block }: { readonly block: Block }) {
  return (
    <pre className="block block--formula" aria-label="수식">
      <code>{block.text}</code>
    </pre>
  )
}
