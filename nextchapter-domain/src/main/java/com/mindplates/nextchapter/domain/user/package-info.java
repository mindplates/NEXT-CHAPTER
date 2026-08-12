/**
 * user 도메인. 하위에 model, exception 을 둔다.
 *
 * <p>도메인 축에 {@code user} 를 더한 이유는 사용자 <b>정체성</b>이 {@code layer}(그 사람의 학습
 * 레이어)와 다른 것이기 때문이다. 레이어에 밀어 넣으면 "레이어 없는 사용자"(가입만 하고 아직 아무것도
 * 읽지 않은 상태)를 표현할 수 없다.
 */
package com.mindplates.nextchapter.domain.user;
