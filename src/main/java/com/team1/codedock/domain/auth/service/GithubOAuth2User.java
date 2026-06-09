package com.team1.codedock.domain.auth.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GithubOAuth2User implements OAuth2User {

    private final OAuth2User delegate;
    private final Map<String, Object> attributes;

    public GithubOAuth2User(OAuth2User delegate, Long userId) {
        this.delegate = delegate;
        this.attributes = new HashMap<>(delegate.getAttributes());
        this.attributes.put("_userId", userId);
    }

    public Long getUserId() {
        return (Long) attributes.get("_userId");
    }

    @Override public Map<String, Object> getAttributes() { return attributes; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return delegate.getAuthorities(); }
    @Override public String getName() { return delegate.getName(); }
}
