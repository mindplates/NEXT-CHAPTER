import { useMutation } from '@tanstack/react-query'
import { useCallback, useState } from 'react'
import type { Chapter, RecordSignalRequest, SignalType } from '../api/chapterApi'
import { recordSignal } from '../api/chapterApi'

interface QuizResult {
  readonly correct: boolean
}

/**
 * 신호 기록. 챕터 하나에 대한 네 종류(퀴즈·질문·오류 신고·소비 진행)를 한 곳에서 보낸다.
 *
 * **소비한 버전을 그대로 실어 보낸다.** 서버가 최신 버전으로 채우면 읽는 사이에 수정이 반영된 사용자의
 * 반응이 새 버전의 실패로 집계된다.
 *
 * 퀴즈는 고른 답만 보내고 **채점 결과를 응답에서 받는다** — 정답이 클라이언트에 없으므로 그것이 결과를
 * 아는 유일한 경로다.
 */
export function useChapterSignals(chapter: Chapter | undefined) {
  const [quizResults, setQuizResults] = useState<Record<string, QuizResult>>({})

  const mutation = useMutation({
    mutationFn: (request: RecordSignalRequest) => recordSignal(chapter!.chapterId, request),
  })

  const send = useCallback(
    (type: SignalType, blockId: string | null, payload: Record<string, unknown>) => {
      if (!chapter) {
        return
      }
      mutation.mutate({
        chapterVersion: chapter.version,
        blockId,
        format: chapter.format,
        type,
        payload,
      })
    },
    [chapter, mutation],
  )

  const answerQuiz = useCallback(
    (blockId: string, choice: string) => {
      if (!chapter) {
        return
      }
      mutation.mutate(
        {
          chapterVersion: chapter.version,
          blockId,
          format: chapter.format,
          type: 'QUIZ_ANSWER',
          payload: { choice },
        },
        {
          onSuccess: (result) => {
            const graded = result as { readonly correct?: boolean } | undefined
            setQuizResults((current) => ({
              ...current,
              [blockId]: { correct: graded?.correct === true },
            }))
          },
        },
      )
    },
    [chapter, mutation],
  )

  const askQuestion = useCallback(
    (blockId: string, text: string) => send('QUESTION', blockId, { text }),
    [send],
  )

  const reportError = useCallback(
    (blockId: string, text: string) => send('ERROR_REPORT', blockId, { text }),
    [send],
  )

  /** 소비 진행. "끝까지 소비된 비율"(3개월 성공 기준 3번)이 이 신호에 의존한다. */
  const reportProgress = useCallback(
    (percent: number) => send('PROGRESS', null, { percent }),
    [send],
  )

  return {
    quizResults,
    answerQuiz,
    askQuestion,
    reportError,
    reportProgress,
    isFailed: mutation.isError,
    error: mutation.error,
  }
}
