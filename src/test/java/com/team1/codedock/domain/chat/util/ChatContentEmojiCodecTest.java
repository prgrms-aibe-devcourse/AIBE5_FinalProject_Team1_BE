package com.team1.codedock.domain.chat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatContentEmojiCodecTest {

    @Test
    @DisplayName("이모지가 없으면 원문 그대로 반환한다")
    void encodePlainTextAsIs() {
        String content = "한글과 english 123";

        assertThat(ChatContentEmojiCodec.encode(content)).isEqualTo(content);
        assertThat(ChatContentEmojiCodec.decode(content)).isEqualTo(content);
    }

    @Test
    @DisplayName("이모지가 있으면 DB 안전 토큰으로 인코딩하고 원문으로 디코딩한다")
    void encodeAndDecodeEmojiContent() {
        String content = "배포 완료 👍🔥";

        String encoded = ChatContentEmojiCodec.encode(content);

        assertThat(encoded).isNotEqualTo(content);
        assertThat(encoded).startsWith("[[CODEDOCK_EMOJI_CONTENT_V1:");
        assertThat(encoded).endsWith("]]");
        assertThat(encoded).doesNotContain("👍", "🔥");
        assertThat(ChatContentEmojiCodec.decode(encoded)).isEqualTo(content);
    }

    @Test
    @DisplayName("조합형 이모지도 통째로 보존한다")
    void encodeAndDecodeComposedEmojiContent() {
        String content = "개발자 👨‍💻 손 👍🏽 국기 🇰🇷 키캡 1️⃣";

        String encoded = ChatContentEmojiCodec.encode(content);

        assertThat(encoded).isNotEqualTo(content);
        assertThat(ChatContentEmojiCodec.decode(encoded)).isEqualTo(content);
    }

    @Test
    @DisplayName("기존 토큰 prefix로 시작하는 일반 메시지는 한 번 더 감싸서 오인 디코딩을 막는다")
    void encodeTextStartingWithTokenPrefix() {
        String content = "[[CODEDOCK_EMOJI_CONTENT_V1:not-base64]]";

        String encoded = ChatContentEmojiCodec.encode(content);

        assertThat(encoded).isNotEqualTo(content);
        assertThat(ChatContentEmojiCodec.decode(encoded)).isEqualTo(content);
    }

    @Test
    @DisplayName("잘못된 토큰은 원문 그대로 반환한다")
    void decodeInvalidTokenAsIs() {
        String content = "[[CODEDOCK_EMOJI_CONTENT_V1:not-base64]]";

        assertThat(ChatContentEmojiCodec.decode(content)).isEqualTo(content);
    }

    @Test
    @DisplayName("null은 그대로 반환한다")
    void keepNullAsNull() {
        assertThat(ChatContentEmojiCodec.encode(null)).isNull();
        assertThat(ChatContentEmojiCodec.decode(null)).isNull();
    }
}
