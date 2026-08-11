package com.mindplates.nextchapter.adapter.out.persistence.admin;

import com.mindplates.nextchapter.application.admin.port.out.DeleteAiCredentialPort;
import com.mindplates.nextchapter.application.admin.port.out.LoadAiCredentialPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAiCredentialPort;
import com.mindplates.nextchapter.domain.admin.model.AiCredential;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AiCredentialPersistenceAdapter
        implements LoadAiCredentialPort, SaveAiCredentialPort, DeleteAiCredentialPort {

    private final AiCredentialJpaRepository credentialRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AiCredential> findAll() {
        return credentialRepository.findAllByOrderByVendorAsc().stream()
                .map(AiCredentialPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiCredential> findByVendor(AiVendor vendor) {
        return credentialRepository.findByVendor(vendor).map(AiCredentialPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public AiCredential save(AiCredential credential) {
        return toDomain(credentialRepository.save(AiCredentialJpaEntity.builder()
                .id(credential.id())
                .vendor(credential.vendor())
                .apiKeyCipher(credential.apiKeyCipher())
                .keyHint(credential.keyHint())
                .build()));
    }

    @Override
    @Transactional
    public void deleteByVendor(AiVendor vendor) {
        credentialRepository.deleteByVendor(vendor);
    }

    private static AiCredential toDomain(AiCredentialJpaEntity entity) {
        return new AiCredential(
                entity.getId(),
                entity.getVendor(),
                entity.getApiKeyCipher(),
                entity.getKeyHint(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
