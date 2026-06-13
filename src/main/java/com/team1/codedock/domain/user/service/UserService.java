package com.team1.codedock.domain.user.service;

import com.team1.codedock.domain.user.dto.UpdateProfileRequest;
import com.team1.codedock.domain.user.dto.UpdateSkillsRequest;
import com.team1.codedock.domain.user.dto.UserResponse;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.entity.UserSkill;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.user.repository.UserSkillRepository;
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
}