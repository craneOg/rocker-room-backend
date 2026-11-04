package com.crane.bandwebbackend.domain.membership.service;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.entity.JoinSource;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.repository.MembershipRepository;
import com.crane.bandwebbackend.domain.participant.repository.PerformanceMemberRepository;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import com.crane.bandwebbackend.global.audit.AuditLog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-160~163. 상태와 유형은 API-029의 프로필 PATCH와 분리한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class MembershipStatusService {
	private final MembershipService membershipService;
	private final MembershipRepository repository;
	private final PerformanceMemberRepository performanceMemberRepository;
	private final EntityManager entityManager;

	/** 소속 상태를 전환한다 */
	@Transactional
	public Membership transition(
		Long clubId,
		Long membershipId,
		Long actorUserId,
		MembershipStatus target,
		String reason
	) {
		// 관리자 권한과 활성 동아리를 확인하고 동시 관리자 변경을 직렬화한다.
		membershipService.requireAdmin(clubId, actorUserId);
		// C-09: 임원 수 검사는 같은 club 행을 잠근 상태에서 한다.
		lockClub(clubId);
		Membership member = membershipService.require(clubId, membershipId);
		// 활동·휴학·졸업 사이에서만 허용되는 상태 전이를 계산한다.
		boolean allowed = switch (target) {
			case ON_LEAVE, GRADUATED -> member.getStatus() == MembershipStatus.ACTIVE
					|| (target == MembershipStatus.GRADUATED && member.getStatus() == MembershipStatus.ON_LEAVE)
					|| (target == MembershipStatus.ON_LEAVE && member.getStatus() == MembershipStatus.GRADUATED);
			case ACTIVE -> member.getStatus() == MembershipStatus.ON_LEAVE
					|| member.getStatus() == MembershipStatus.GRADUATED;
			default -> false;
		};
		if (!allowed) {
			throw new ApiException(ApiErrorCode.STATE_CONFLICT, "허용되지 않은 소속 상태 전이입니다.",
					"INVALID_MEMBERSHIP_STATUS");
		}
		// 마지막 관리자를 졸업 처리해 관리자가 사라지는 상황을 방지한다.
		if (target == MembershipStatus.GRADUATED && member.getRole() == MembershipRole.CLUB_ADMIN) {
			long otherAdmins = repository.findByClub_Id(clubId).stream()
					.filter(other -> !other.getId().equals(member.getId()))
					.filter(other -> other.getRole() == MembershipRole.CLUB_ADMIN)
					.filter(other -> other.getStatus() == MembershipStatus.ACTIVE
							|| other.getStatus() == MembershipStatus.ON_LEAVE)
					.count();
			if (otherAdmins == 0) {
				throw new ApiException(ApiErrorCode.RULE_VIOLATION, "마지막 임원은 졸업 처리할 수 없습니다.",
						"LAST_ADMIN");
			}
		}
		// 변경 전후 값을 감사 로그에 남기기 위해 이전 상태를 보존한다.
		MembershipStatus previous = member.getStatus();
		member.transitionTo(target, LocalDateTime.now(ZoneOffset.UTC));
		audit(clubId, actorUserId, "MEMBERSHIP_STATUS_CHANGED", member, reason,
				"{\"from\":\"" + previous + "\",\"to\":\"" + target + "\"}");
		return member;
	}

	/** 소속 유형을 변경한다 */
	@Transactional
	public Membership changeType(
		Long clubId,
		Long membershipId,
		Long actorUserId,
		MemberType target,
		String reason
	) {
		// 관리자 권한과 동아리 잠금을 확보한 뒤 대상 소속을 조회한다.
		membershipService.requireAdmin(clubId, actorUserId);
		lockClub(clubId);
		Membership member = membershipService.require(clubId, membershipId);
		if (target == null) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "memberType이 필요합니다.");
		}
		if (!EnumSet.of(MembershipStatus.ACTIVE, MembershipStatus.ON_LEAVE, MembershipStatus.GRADUATED)
				.contains(member.getStatus()) || target == member.getMemberType()) {
			throw new ApiException(ApiErrorCode.STATE_CONFLICT, "변경할 수 없는 멤버 유형입니다.",
					target == member.getMemberType() ? "SAME_MEMBER_TYPE" : "INVALID_MEMBERSHIP_STATUS");
		}
		// 객원 전환은 관리자·진행 공연·가입 경로 제약을 추가로 검사한다.
		if (target == MemberType.GUEST) {
			if (member.getRole() == MembershipRole.CLUB_ADMIN) {
				throw new ApiException(ApiErrorCode.RULE_VIOLATION, "임원은 객원이 될 수 없습니다.",
						"ADMIN_CANNOT_BE_GUEST");
			}
			boolean connected = performanceMemberRepository.findByMembership_Id(membershipId)
					.stream().anyMatch(pm -> EnumSet.of(PerformanceStatus.DRAFT, PerformanceStatus.ACTIVE,
							PerformanceStatus.LOCKED).contains(pm.getPerformance().getStatus()));
			if (connected) {
				throw new ApiException(ApiErrorCode.RULE_VIOLATION, "진행 중 공연 연결이 있습니다.",
						"PERFORMANCE_LINKED");
			}
			if (member.getJoinSource() != JoinSource.ADMIN_ADDED) {
				throw new ApiException(ApiErrorCode.RULE_VIOLATION, "직접 추가된 소속만 객원이 될 수 있습니다.",
						"NOT_DIRECTLY_ADDED");
			}
		}
		// 유형을 변경하고 이전·이후 값을 감사 기록으로 남긴다.
		MemberType previous = member.getMemberType();
		member.changeMemberType(target);
		audit(clubId, actorUserId, "MEMBERSHIP_TYPE_CHANGED", member, reason,
				"{\"from\":\"" + previous + "\",\"to\":\"" + target + "\"}");
		return member;
	}

	/** 소속 표시 이름을 수정한다 */
	@Transactional
	public Membership updateDisplayName(
		Long clubId,
		Long membershipId,
		Long actorUserId,
		String displayName
	) {
		// 동아리 상태를 잠근 뒤 대상 소속과 요청자의 본인 여부를 확인한다.
		lockClub(clubId);
		Membership member = membershipService.require(clubId, membershipId);
		if (!member.getUser().getId().equals(actorUserId)) {
			throw new ApiException(ApiErrorCode.FORBIDDEN, "본인의 표시 이름만 바꿀 수 있습니다.");
		}
		if (displayName != null && (displayName.isBlank() || displayName.length() > 50)) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "표시 이름은 1~50자여야 합니다.");
		}
		// null은 사용자 기본 이름 사용을 의미하며 엔티티가 공백 정규화를 담당한다.
		member.changeDisplayName(displayName);
		return member;
	}

	/** 최소 관리자 수를 안전하게 검사하도록 동아리 행을 잠근다. */
	private void lockClub(Long clubId) {
		Club club = entityManager.find(Club.class, clubId, LockModeType.PESSIMISTIC_WRITE);
		if (club == null) {
			throw new ApiException(ApiErrorCode.NOT_FOUND, "동아리를 찾을 수 없습니다.");
		}
		if (club.getStatus() != ClubStatus.ACTIVE) {
			throw new ApiException(ApiErrorCode.STATE_CONFLICT, "비활성 동아리에서는 변경할 수 없습니다.",
					"CLUB_INACTIVE");
		}
	}

	/** 소속 상태 또는 유형 변경 내역을 감사 로그로 남긴다. */
	private void audit(Long clubId, Long actorUserId, String action, Membership member,
					   String reason, String detail) {
		entityManager.persist(AuditLog.record(clubId, actorUserId, action,
				"MEMBERSHIP", member.getId(), reason, detail));
	}
}
