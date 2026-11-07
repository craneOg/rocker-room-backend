package com.crane.bandwebbackend.domain.membership.service;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.repository.MembershipRepository;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MembershipService {
	private final MembershipRepository repository;
	private final CoreEntityLookupService entityLookup;

	@Transactional(readOnly = true)
	public List<Membership> membershipsOfUser(Long userId) {
		return repository.findByUser_Id(userId);
	}

	@Transactional(readOnly = true)
	public boolean exists(Long clubId, Long userId) {
		return repository.findByClub_IdAndUser_Id(clubId, userId).isPresent();
	}

	/** 소속 존재와 접근 권한을 확인한다 */
	@Transactional(readOnly = true)
	public Membership require(
		Long clubId,
		Long membershipId
	) {
		// 동아리 경계를 쿼리 조건에 포함해 다른 동아리의 소속 ID 접근을 숨긴다.
		return repository.findByIdAndClub_Id(membershipId, clubId)
				.orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "소속을 찾을 수 없습니다."));
	}

	/** 소속 소속 여부를 확인한다 */
	@Transactional(readOnly = true)
	public Membership requireMember(
		Long clubId,
		Long actorUserId
	) {
		// 로그인 사용자의 동아리 소속을 조회한다.
		Membership membership = repository.findByClub_IdAndUser_Id(clubId, actorUserId)
				.orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "동아리 소속이 없습니다."));
		// 실제 기능을 사용할 수 있는 활동·휴학·졸업 소속만 통과시킨다.
		if (membership.getStatus() != MembershipStatus.ACTIVE && membership.getStatus() != MembershipStatus.ON_LEAVE
				&& membership.getStatus() != MembershipStatus.GRADUATED) {
			throw BusinessException.of(ErrorCode.FORBIDDEN, "활동 가능한 동아리 소속이 아닙니다.");
		}
		return membership;
	}

	/** 소속 임원 권한을 확인한다 */
	@Transactional(readOnly = true)
	public Membership requireAdmin(
		Long clubId,
		Long actorUserId
	) {
		// 먼저 유효 소속인지 확인한 뒤 역할과 정회원 조건을 추가로 검사한다.
		Membership actor = requireMember(clubId, actorUserId);
		if (actor.getRole() != MembershipRole.CLUB_ADMIN || actor.getMemberType() != MemberType.REGULAR
				|| (actor.getStatus() != MembershipStatus.ACTIVE && actor.getStatus() != MembershipStatus.ON_LEAVE)) {
			throw BusinessException.of(ErrorCode.FORBIDDEN, "동아리 관리자 권한이 필요합니다.");
		}
		return actor;
	}

	/** MEM-03: 비유효 소속도 본인은 조회할 수 있지만 타인의 상세는 임원만 조회한다. */
	@Transactional(readOnly = true)
	public Membership detail(
		Long clubId,
		Long membershipId,
		Long actorUserId
	) {
		// 본인에게는 비활성 이력도 보여 주고, 타인 상세는 관리자 또는 정회원에게만 허용한다.
		Membership target = require(clubId, membershipId);
		if (!target.getUser().getId().equals(actorUserId)) {
			Membership actor = requireMember(clubId, actorUserId);
			boolean admin = actor.getRole() == MembershipRole.CLUB_ADMIN
					&& actor.getMemberType() == MemberType.REGULAR
					&& (actor.getStatus() == MembershipStatus.ACTIVE
							|| actor.getStatus() == MembershipStatus.ON_LEAVE);
			if (!admin && actor.getMemberType() != MemberType.REGULAR) {
				throw BusinessException.of(ErrorCode.NOT_FOUND, "소속을 찾을 수 없습니다.");
			}
			if (!admin && target.getStatus() != MembershipStatus.ACTIVE
					&& target.getStatus() != MembershipStatus.ON_LEAVE
					&& target.getStatus() != MembershipStatus.GRADUATED) {
				throw BusinessException.of(ErrorCode.NOT_FOUND, "소속을 찾을 수 없습니다.");
			}
		}
		return target;
	}

	/** 소속 임원용 목록을 조회한다 */
	@Transactional(readOnly = true)
	public List<Membership> listAdmin(
		Long clubId,
		Long actorUserId
	) {
		// 관리자 권한 확인 후 상태와 무관한 전체 운영 목록을 반환한다.
		requireAdmin(clubId, actorUserId);
		return repository.findByClub_Id(clubId);
	}

	/** 소속 공개 목록을 조회한다 */
	@Transactional(readOnly = true)
	public List<Membership> listPublic(
		Long clubId,
		Long actorUserId
	) {
		// 공개 목록은 정회원에게만 허용해 객원의 동아리 전체 명단 접근을 막는다.
		Membership actor = requireMember(clubId, actorUserId);
		if (actor.getMemberType() != MemberType.REGULAR) {
			throw BusinessException.of(ErrorCode.NOT_FOUND, "부원 목록을 찾을 수 없습니다.");
		}
		// 가입 완료 상태만 남겨 대기·거절·탈퇴 기록을 공개하지 않는다.
		return repository.findByClub_Id(clubId).stream()
				.filter(member -> member.getStatus() == MembershipStatus.ACTIVE
						|| member.getStatus() == MembershipStatus.ON_LEAVE
						|| member.getStatus() == MembershipStatus.GRADUATED)
				.toList();
	}

	/** 소속 초대한다 */
	@Transactional
	public Membership invite(
		Long clubId,
		Long actorUserId,
		String handle,
		MemberType type,
		String displayName
	) {
		// 초대 권한과 요청 필드를 먼저 검증한다.
		requireAdmin(clubId, actorUserId);
		validateDisplayName(displayName);
		if (type == null) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "소속 유형이 필요합니다.");
		}
		Club club = requireClub(clubId);
		if (club.getStatus() != ClubStatus.ACTIVE) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "비활성 동아리에는 직접 추가할 수 없습니다.",
					"CLUB_INACTIVE");
		}
		// 정확한 핸들로 활성 사용자를 찾고 기존 소속 이력을 확인한다.
		User user = entityLookup.byHandle(handle);
		Membership existing = repository.findByClub_IdAndUser_Id(clubId, user.getId()).orElse(null);
		// 재초대 가능한 종료 상태는 기존 행을 재사용해 동아리-사용자 유일성을 보존한다.
		if (existing != null) {
			LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
			if (existing.getStatus() == MembershipStatus.INVITED
					&& now.isBefore(invitationExpiresAt(existing.getInvitedAt()))) {
				throw BusinessException.of(ErrorCode.DUPLICATE, "유효한 초대가 이미 있습니다.");
			}
			try {
				existing.restartInvitation(type, displayName, now);
				repository.flush();
				return existing;
			} catch (IllegalStateException exception) {
				throw BusinessException.of(ErrorCode.DUPLICATE, "이미 유효한 소속이 있습니다.");
			}
		}
		return repository.saveAndFlush(Membership.invite(club, user, type, displayName, LocalDateTime.now(ZoneOffset.UTC)));
	}

	/** 선택 표시명의 공백 여부와 최대 길이를 검증한다. */
	private static void validateDisplayName(String displayName) {
		if (displayName != null && (displayName.isBlank() || displayName.length() > 50)) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "표시명은 1~50자여야 합니다.");
		}
	}

	/** 연결된 동아리 조회 포트로 동아리 존재를 확인한다. */
	private Club requireClub(Long clubId) {
		return entityLookup.require(clubId);
	}

	/** 초대 시각에서 한국 달력 기준 한 달 뒤의 UTC 만료 시각을 계산한다. */
	private static LocalDateTime invitationExpiresAt(LocalDateTime invitedAt) {
		return invitedAt.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.of("Asia/Seoul"))
				.plusMonths(1).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
	}

	/** 소속 초대 만료 시각을 계산한다 */
	public LocalDateTime invitationExpiresAt(Membership membership) {
		if (membership.getInvitedAt() == null) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "초대 기록이 없습니다.");
		}
		return invitationExpiresAt(membership.getInvitedAt());
	}
}
