import { useState } from 'react'
import type { Block } from '../api/chapterApi'

/**
 * 퀴즈. **정답이 여기 없다** — 서버가 내려 주지 않는다.
 *
 * 정답을 클라이언트에 두면 채점을 클라이언트가 하게 되고 그 결과는 위조할 수 있다. 오답률이 집단 루프의
 * 핵심 신호이므로, 위조 가능성 자체가 임계치 판정의 근거를 없앤다. 그래서 고른 답을 서버에 보내고
 * **채점 결과를 받아** 표시한다.
 */
export function QuizBlock({
  block,
  onAnswer,
  result,
}: {
  readonly block: Block
  readonly onAnswer: (blockId: string, choice: string) => void
  readonly result?: { readonly correct: boolean }
}) {
  const [selected, setSelected] = useState<string | null>(null)
  const question = String(block.attributes.question ?? block.text ?? '')
  const choices = readChoices(block.attributes.choices)
  const answered = result !== undefined

  return (
    <section className="block block--quiz" aria-label="퀴즈">
      <p className="quiz-question">{question}</p>
      <ul className="quiz-choices">
        {choices.map((choice) => (
          <li key={choice}>
            <label>
              <input
                type="radio"
                name={`quiz-${block.id}`}
                value={choice}
                checked={selected === choice}
                disabled={answered}
                onChange={() => setSelected(choice)}
              />
              {choice}
            </label>
          </li>
        ))}
      </ul>

      {!answered && (
        <button
          className="button"
          disabled={selected === null}
          onClick={() => selected !== null && onAnswer(block.id, selected)}
        >
          답 확인
        </button>
      )}

      {answered && (
        <p role="status" className={result.correct ? 'quiz-result--correct' : 'quiz-result--wrong'}>
          {result.correct ? '맞았습니다.' : '틀렸습니다. 이 지점은 기록됩니다 — 같은 곳에서 여럿이 틀리면 이 챕터가 개선됩니다.'}
        </p>
      )}
    </section>
  )
}

function readChoices(raw: unknown): readonly string[] {
  return Array.isArray(raw) ? raw.map((choice) => String(choice)) : []
}
