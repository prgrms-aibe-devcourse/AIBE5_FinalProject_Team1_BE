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
    @DisplayName("같은 이모지가 반복되어도 모두 같은 key 토큰으로 인코딩한다")
    void encodeRepeatedEmojiTokens() {
        String content = "🔥🔥 hot fix 🔧🔧";

        String encoded = ChatContentEmojiCodec.encode(content);

        assertThat(encoded).isEqualTo("[[emoji:fire]][[emoji:fire]] hot fix [[emoji:fix]][[emoji:fix]]");
        assertThat(ChatContentEmojiCodec.decode(encoded)).isEqualTo(content);
    }

    @Test
    @DisplayName("이미 저장 토큰인 내용은 다시 인코딩해도 중복 변환하지 않는다")
    void encodeExistingTokensWithoutDoubleEncoding() {
        String content = "이미 저장된 [[emoji:like]] [[emoji:fire]]";

        String encoded = ChatContentEmojiCodec.encode(content);

        assertThat(encoded).isEqualTo(content);
        assertThat(ChatContentEmojiCodec.decode(encoded)).isEqualTo("이미 저장된 👍 🔥");
    }

    @Test
    @DisplayName("본문에 reaction key 단어가 있어도 실제 이모지가 아니면 토큰으로 바꾸지 않는다")
    void doNotEncodeReactionKeyWords() {
        String content = "like fire coffee fix memo";

        String encoded = ChatContentEmojiCodec.encode(content);

        assertThat(encoded).isEqualTo(content);
        assertThat(ChatContentEmojiCodec.decode(encoded)).isEqualTo(content);
    }

    @Test
    @DisplayName("지원하지 않는 일반 이모지는 임의 key로 바꾸지 않는다")
    void keepUnsupportedEmojiAsIs() {
        String content = "개발자 👨‍💻 국기 🇰🇷 키캡 1️⃣";

        String encoded = ChatContentEmojiCodec.encode(content);

        assertThat(encoded).isEqualTo(content);
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
    @DisplayName("알려진 토큰과 모르는 토큰이 섞이면 알려진 토큰만 복원한다")
    void decodeKnownTokensOnly() {
        String content = "[[emoji:like]] [[emoji:unknown]] [[emoji:memo]]";

        String decoded = ChatContentEmojiCodec.decode(content);

        assertThat(decoded).isEqualTo("👍 [[emoji:unknown]] 📝");
    }

    @Test
    @DisplayName("깨진 토큰 형태는 원문 그대로 둔다")
    void keepMalformedTokenAsIs() {
        String content = "[[emoji:like] [[emoji:fire]";

        assertThat(ChatContentEmojiCodec.decode(content)).isEqualTo(content);
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
