package com.crane.bandwebbackend.domain.audition.service;

import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 곡 상세의 오디션 기록 요약용. ponytail: audition 도메인 service가 생기면 그쪽으로 옮김
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditionQueryService {

    private final EntityManager em;

    public List<AuditionEntry> entriesOf(Long songId) {
        return em.createQuery(
                        "select a from AuditionEntry a left join fetch a.part where a.song.id = :songId order by a.id",
                        AuditionEntry.class)
                .setParameter("songId", songId)
                .getResultList();
    }
}
