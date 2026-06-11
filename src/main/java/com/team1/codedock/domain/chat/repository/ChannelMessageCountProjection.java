package com.team1.codedock.domain.chat.repository;

public interface ChannelMessageCountProjection {

    Long getChannelId();

    long getMessageCount();
}
