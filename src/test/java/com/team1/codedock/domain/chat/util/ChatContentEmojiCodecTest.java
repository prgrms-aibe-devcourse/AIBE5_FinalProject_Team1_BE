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
    @DisplayName("리액션 팔레트 이모지는 같은 key 토큰으로 인코딩하고 원문으로 디코딩한다")
    void encodeAndDecodeEmojiContent() {
        String content = "배포 완료 👍🔥";

        String encoded = ChatContentEmojiCodec.encode(content);

        assertThat(encoded).isNotEqualTo(content);
        assertThat(encoded).isEqualTo("배포 완료 [[emoji:like]][[emoji:fire]]");
        assertThat(encoded).doesNotContain("👍", "🔥");
        assertThat(ChatContentEmojiCodec.decode(encoded)).isEqualTo(content);
    }

    @Test
    @DisplayName("리액션 팔레트 24개 이모지를 모두 같은 key 토큰으로 인코딩한다")
    void encodeAndDecodeAllReactionPaletteEmojis() {
        String content = "👍 👎 ❤️ 😂 😄 😮 😢 😭 😡 🤔 👏 🙏 👀 🔥 🚀 🎉 ✅ ❌ ⭐ 💡 🐛 🔧 📝 ☕";

        String encoded = ChatContentEmojiCodec.encode(content);

        assertThat(encoded).isEqualTo(
                "[[emoji:like]] [[emoji:dislike]] [[emoji:heart]] [[emoji:laugh]] [[emoji:smile]] "
                        + "[[emoji:surprised]] [[emoji:sad]] [[emoji:cry]] [[emoji:angry]] "
                        + "[[emoji:thinking]] [[emoji:clap]] [[emoji:pray]] [[emoji:eyes]] "
                        + "[[emoji:fire]] [[emoji:rocket]] [[emoji:party]] [[emoji:check]] "
                        + "[[emoji:cross]] [[emoji:star]] [[emoji:bulb]] [[emoji:bug]] "
                        + "[[emoji:fix]] [[emoji:memo]] [[emoji:coffee]]"
        );
        assertThat(ChatContentEmojiCodec.decode(encoded)).isEqualTo(content);
    }

    @Test
    @DisplayName("variation selector가 없는 heart와 coffee도 같은 key 토큰으로 인코딩한다")
    void encodeEmojiWithoutVariationSelector() {
        String content = "하트 ❤ 커피 ☕";

        String encoded = ChatContentEmojiCodec.encode(content);

        assertThat(encoded).isEqualTo("하트 [[emoji:heart]] 커피 [[emoji:coffee]]");
        assertThat(ChatContentEmojiCodec.decode(encoded)).isEqualTo("하트 ❤️ 커피 ☕");
    }

    @Test
    @DisplayName("지원하지 않는 토큰은 원문 그대로 반환한다")
    void decodeUnknownTokenAsIs() {
        String content = "[[emoji:unknown]]";

        assertThat(ChatContentEmojiCodec.decode(content)).isEqualTo(content);
    }

    @Test
    @DisplayName("null은 그대로 반환한다")
    void keepNullAsNull() {
        assertThat(ChatContentEmojiCodec.encode(null)).isNull();
        assertThat(ChatContentEmojiCodec.decode(null)).isNull();
    }
}
