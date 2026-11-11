package com.crane.bandwebbackend.domain.part.service;

import com.crane.bandwebbackend.domain.part.dto.PartResponse;
import com.crane.bandwebbackend.domain.part.repository.MembershipPartRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소속 응답의 보유 파트만 읽는다. 파트 변경 규칙은 파트 담당 구현과 연결한다. */
@Service
@RequiredArgsConstructor
public class MembershipPartReader {
    private final MembershipPartRepository repository;

    /** 소속 한 건이 보유한 파트 목록을 조회한다. */
    @Transactional(readOnly = true)
    public List<PartResponse> partsOf(Long membershipId) {
        // 연결 엔티티를 조회한 뒤 외부 응답에는 파트 정보만 노출한다.
        return repository.findByMembership_Id(membershipId).stream()
                .map(row -> PartResponse.from(row.getPart()))
                .toList();
    }

    /** AUTH-10 한 페이지의 파트를 한 번에 읽는다. */
    @Transactional(readOnly = true)
    public Map<Long, List<PartResponse>> partsOfAll(List<Long> membershipIds) {
        // 빈 페이지에서는 불필요한 IN 쿼리를 실행하지 않는다.
        if (membershipIds.isEmpty()) {
            return Map.of();
        }
        // 한 번의 조회 결과를 소속 ID별 파트 응답 목록으로 그룹화한다.
        return repository.findByMembership_IdIn(membershipIds).stream()
                .collect(Collectors.groupingBy(
                        row -> row.getMembership().getId(),
                        Collectors.mapping(row -> PartResponse.from(row.getPart()), Collectors.toList())));
    }
}
