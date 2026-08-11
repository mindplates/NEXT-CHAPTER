package com.mindplates.nextchapter.application.admin.port.out;

/**
 * 저장용 대칭 암호. AI 프로바이더 API 키가 평문으로 DB 에 들어가지 않게 한다.
 *
 * <p>애플리케이션이 알고리즘을 모르는 이유는 교체 가능성 때문이다 — 마스터 키 회전이나 알고리즘
 * 상향이 필요할 때 유스케이스가 함께 흔들리면 안 된다.
 */
public interface SecretCipherPort {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
