import { useState } from 'react'

/**
 * 블록 옆에 붙는 신호 도구 — 질문과 오류 신고.
 *
 * **블록에 앵커링하는 것이 이 컴포넌트의 존재 이유다.** 질문이 챕터 전체에 붙으면 집단 루프가 "이 챕터가
 * 어렵다"까지만 알 수 있고, 어느 문단·어느 수식에서 막히는지가 나와야 수정안이 만들어진다.
 *
 * 오류 신고 버튼이 "별도 평가 UI 를 두지 않는다"와 충돌하지 않는 이유는, 그 원칙이 *품질 점수를 묻지
 * 않는다*는 뜻이지 사실 오류 제보 경로까지 막는다는 뜻이 아니기 때문이다.
 */
export function BlockSignalTools({
  blockId,
  onQuestion,
  onErrorReport,
}: {
  readonly blockId: string
  readonly onQuestion: (blockId: string, text: string) => void
  readonly onErrorReport: (blockId: string, text: string) => void
}) {
  const [mode, setMode] = useState<'idle' | 'question' | 'error'>('idle')
  const [text, setText] = useState('')

  if (mode === 'idle') {
    return (
      <div className="block-tools">
        <button className="button button--tiny" onClick={() => setMode('question')} aria-label="이 지점에 질문하기">
          질문
        </button>
        <button className="button button--tiny" onClick={() => setMode('error')} aria-label="이 지점의 오류 신고">
          오류
        </button>
      </div>
    )
  }

  const submit = () => {
    const trimmed = text.trim()
    if (!trimmed) {
      return
    }
    if (mode === 'question') {
      onQuestion(blockId, trimmed)
    } else {
      onErrorReport(blockId, trimmed)
    }
    setText('')
    setMode('idle')
  }

  return (
    <div className="block-tools block-tools--open">
      <textarea
        aria-label={mode === 'question' ? '질문 내용' : '오류 내용'}
        value={text}
        rows={2}
        onChange={(event) => setText(event.target.value)}
        placeholder={mode === 'question' ? '무엇이 이해되지 않나요?' : '무엇이 틀렸나요?'}
      />
      <button className="button button--tiny" onClick={submit} disabled={!text.trim()}>
        보내기
      </button>
      <button
        className="button button--tiny button--quiet"
        onClick={() => {
          setText('')
          setMode('idle')
        }}
      >
        취소
      </button>
    </div>
  )
}
