package com.team1.codedock.domain.auth.service;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String githubId    = String.valueOf(attributes.get("id"));
        String githubLogin = (String) attributes.get("login");
        String email       = (String) attributes.get("email");
        String avatarUrl   = (String) attributes.get("avatar_url");
        String accessToken = userRequest.getAccessToken().getTokenValue();

        User user = userRepository.findByGithubId(githubId)
                .map(existing -> {
                    log.info("기존 유저 로그인 → userId={}, github={}", existing.getId(), githubLogin);
                    existing.updateOnGithubLogin(accessToken, avatarUrl);
                    return existing;
                })
                .orElseGet(() -> {
                    User newUser = userRepository.save(
                            User.createFromGithub(githubId, githubLogin, email, avatarUrl, accessToken)
                    );
                    log.info("신규 유저 DB 저장 완료 → userId={}, github={}, email={}", newUser.getId(), githubLogin, email);
                    return newUser;
                });

        // userId를 attributes에 추가해서 SuccessHandler로 전달
        return new GithubOAuth2User(oAuth2User, user.getId());
    }
}
