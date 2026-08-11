package com.mindplates.nextchapter.application.admin.view;

import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.time.LocalDateTime;

/** 등록된 키의 존재와 뒤 4자만 노출한다. 복호화 결과가 API 로 나가는 경로는 없다. */
public record AiCredentialView(AiVendor vendor, String keyHint, LocalDateTime updatedAt) {}
