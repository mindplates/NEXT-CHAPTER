package com.mindplates.nextchapter.adapter.security;

import com.mindplates.nextchapter.application.admin.port.out.SecretCipherPort;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 프로바이더 API 키를 저장하기 위한 AES-GCM 암호.
 *
 * <p>GCM 을 쓰는 이유는 인증 태그가 붙기 때문이다. CBC 같은 모드는 암호문이 조작돼도 복호화가
 * 그럴듯한 쓰레기를 내놓고, 그 값이 그대로 API 키로 쓰인다 — 실패가 "인증 오류"로 나타나 원인을
 * 찾기 어렵다. GCM 은 조작을 복호화 시점에 예외로 만든다.
 *
 * <p>암호문에 {@code v1:} 접두사를 붙인다. 알고리즘을 올릴 때 기존 값을 읽을 수 있어야 하고,
 * 접두사가 없으면 전체 키 재등록이 유일한 이행 경로가 된다.
 *
 * <p>마스터 키가 설정돼 있지 않으면 <b>기동은 하고 사용 시점에 실패</b>한다. 기동을 막으면 AI 키와
 * 무관한 기능(카탈로그·인증)까지 못 쓰게 되고, 임의 키로 대체하면 재기동 후 저장된 암호문을 전부
 * 복호화할 수 없게 된다 — 후자는 조용히 데이터를 버리는 쪽이라 더 나쁘다.
 */
@Component
public class AesGcmSecretCipher implements SecretCipherPort {

    private static final Logger log = LoggerFactory.getLogger(AesGcmSecretCipher.class);

    private static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String NOT_CONFIGURED =
            "암호화 마스터 키(nextchapter.security.secret-key)가 설정되지 않아 API 키를 저장·조회할 수 없습니다.";

    private final SecretKey masterKey;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretCipher(SecretsProperties properties) {
        this.masterKey = toKey(properties.secretKey());
        if (masterKey == null) {
            log.warn("[보안] {} AI 프로바이더 키 관리를 쓰려면 SECRET_KEY 를 설정할 것.", NOT_CONFIGURED);
        }
    }

    @Override
    public String encrypt(String plaintext) {
        SecretKey key = requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // 예외 메시지에 평문이 섞이지 않게 원인을 그대로 올리지 않는다.
            throw new InvalidOperationException(
                    "API 키를 암호화하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        SecretKey key = requireKey();
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            throw new InvalidOperationException("알 수 없는 암호문 형식입니다.");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] decrypted = cipher.doFinal(combined, IV_BYTES, combined.length - IV_BYTES);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new InvalidOperationException(
                    "API 키를 복호화하지 못했습니다. 마스터 키가 바뀌었을 수 있습니다: " + e.getClass().getSimpleName());
        }
    }

    private SecretKey requireKey() {
        if (masterKey == null) {
            throw new InvalidOperationException(NOT_CONFIGURED);
        }
        return masterKey;
    }

    /**
     * 설정 문자열을 256비트 키로 만든다.
     *
     * <p>임의 길이 문자열을 SHA-256 으로 접는 이유는 운영자가 base64 32바이트를 정확히 맞추도록
     * 강요하지 않기 위해서다. 이 값은 사람이 기억하는 비밀번호가 아니라 환경변수로 주입되는 KEK 라
     * 반복 해싱(PBKDF2 등)이 주는 이득이 없다.
     */
    private static SecretKey toKey(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(configured.trim().getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
        }
    }
}
