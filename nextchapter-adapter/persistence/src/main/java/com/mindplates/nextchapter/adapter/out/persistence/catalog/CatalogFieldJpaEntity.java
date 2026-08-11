package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import com.mindplates.nextchapter.adapter.out.persistence.support.AuditTimestamps;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 부모를 {@code @ManyToOne} 이 아니라 FK 컬럼(Long)으로 든다.
 *
 * <p>도메인 모델이 부모를 ID 로 참조하므로 연관 매핑이 있어도 결국 ID 만 꺼내 쓰게 되고, 그
 * 과정에서 지연 로딩 프록시와 N+1 만 늘어난다. 카탈로그는 트리를 한 번에 읽어 메모리에서
 * 조립하는 접근이라 연관이 오히려 방해가 된다.
 */
@Entity
@Table(name = "catalog_fields")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CatalogFieldJpaEntity extends AuditTimestamps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "domain_id", nullable = false)
    private Long domainId;

    @Column(nullable = false, length = 80, updatable = false)
    private String slug;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
