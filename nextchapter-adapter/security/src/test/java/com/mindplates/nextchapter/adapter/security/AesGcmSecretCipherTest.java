package com.mindplates.nextchapter.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("API 키 저장 암호")
class AesGcmSecretCipherTest {

    private static final String API_KEY = "sk-ant-api03-abcdefghijklmnop";

    @Test
    @DisplayName("암호화한 값을 다시 복호화하면 원문이다")
    void roundTrip() {
        AesGcmSecretCipher cipher = cipherWith("master-key-for-test");

        assertThat(cipher.decrypt(cipher.encrypt(API_KEY))).isEqualTo(API_KEY);
    }

    @Test
    @DisplayName("같은 평문을 두 번 암호화하면 암호문이 다르다 — IV 가 매번 새로 생성된다")
    void ciphertextIsNotDeterministic() {
        AesGcmSecretCipher cipher = cipherWith("master-key-for-test");

        assertThat(cipher.encrypt(API_KEY)).isNotEqualTo(cipher.encrypt(API_KEY));
    }

    @Test
    @DisplayName("암호문에 평문이 남지 않는다")
    void ciphertextDoesNotLeakPlaintext() {
        assertThat(cipherWith("master-key-for-test").encrypt(API_KEY)).doesNotContain("sk-ant");
    }

    @Test
    @DisplayName("암호문이 조작되면 복호화가 실패한다 — GCM 인증 태그를 쓰는 이유")
    void detectsTampering() {
        AesGcmSecretCipher cipher = cipherWith("master-key-for-test");
        String encrypted = cipher.encrypt(API_KEY);
        String tampered = encrypted.substring(0, encrypted.length() - 3) + "AAA";

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("마스터 키가 다르면 복호화되지 않는다")
    void wrongMasterKeyFails() {
        String encrypted = cipherWith("master-key-for-test").encrypt(API_KEY);

        assertThatThrownBy(() -> cipherWith("another-master-key").decrypt(encrypted))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("마스터 키");
    }

    @Test
    @DisplayName("마스터 키가 없으면 기동은 되고 사용 시점에 실패한다")
    void failsOnUseWhenNotConfigured() {
        AesGcmSecretCipher cipher = cipherWith("   ");

        assertThatThrownBy(() -> cipher.encrypt(API_KEY))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("마스터 키");
    }

    @Test
    @DisplayName("접두사가 없는 값은 암호문으로 받아들이지 않는다")
    void rejectsUnknownFormat() {
        assertThatThrownBy(() -> cipherWith("master-key-for-test").decrypt("plain-text-key"))
                .isInstanceOf(InvalidOperationException.class);
    }

    private static AesGcmSecretCipher cipherWith(String masterKey) {
        return new AesGcmSecretCipher(new SecretsProperties(masterKey));
    }
}
