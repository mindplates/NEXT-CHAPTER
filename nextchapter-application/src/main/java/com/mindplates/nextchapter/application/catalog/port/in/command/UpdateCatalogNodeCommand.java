package com.mindplates.nextchapter.application.catalog.port.in.command;

/**
 * 세 계층이 같은 수정 커맨드를 쓴다. 수정 대상이 표시 정보(이름·설명·정렬)뿐이고 계층마다
 * 다르지 않기 때문이다.
 *
 * <p>slug 와 부모(domainId·fieldId)가 빠져 있는 것은 누락이 아니다. slug 는 안정 키라 바뀌지
 * 않고, 부모 이동은 이미 쌓인 신호의 소속을 바꾸는 일이라 별도 조작으로 다뤄야 한다.
 */
public record UpdateCatalogNodeCommand(String name, String description, int sortOrder) {}
