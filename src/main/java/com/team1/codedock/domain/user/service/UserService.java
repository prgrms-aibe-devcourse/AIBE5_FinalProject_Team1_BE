package com.team1.codedock.domain.user.service;

import com.team1.codedock.domain.auth.repository.RefreshTokenRepository;
import com.team1.codedock.domain.user.dto.UpdateProfileRequest;
import com.team1.codedock.domain.user.dto.UpdateSkillsRequest;
import com.team1.codedock.domain.user.dto.UserResponse;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.entity.UserSkill;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.user.repository.UserSkillRepository;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public UserResponse updateProfile(Long currentUserId, UpdateProfileRequest req) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.updateProfile(
                req.getDisplayName(),
                req.getNickname(),
                req.getDeveloperType(),
                req.getBio(),
                req.getAvatarUrl()
        );
        return UserResponse.from(user);
    }

    public List<String> updateSkills(Long currentUserId, UpdateSkillsRequest req) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<String> deduped = req.getSkills().stream().distinct().toList();
        userSkillRepository.deleteAllByUser(user);
        userSkillRepository.saveAll(
                deduped.stream().map(name -> UserSkill.create(user, name)).toList()
        );
        return deduped;
    }

    public List<String> getSkills(Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return userSkillRepository.findAllByUser(user).stream()
                .map(UserSkill::getSkillName)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    public UserResponse disconnectGithub(Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getPasswordHash() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (user.isGithubConnected()) {
            user.disconnectGithub();
        }
        return UserResponse.from(user);
    }

    public void withdraw(Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 실제 삭제 대신 참조 무결성을 유지하는 soft-delete 처리함.
        refreshTokenRepository.revokeAllByUser(user);
        userSkillRepository.deleteAllByUser(user);
        workspaceMemberRepository.findAllByUser(user).stream()
                .filter(WorkspaceMember::isActive)
                .forEach(member -> member.deactivate("회원탈퇴"));
        user.deactivateAccount(
                "deleted-user-" + user.getId() + "@codedock.local",
                "deleted-user-" + user.getId()
        );
    }
}
