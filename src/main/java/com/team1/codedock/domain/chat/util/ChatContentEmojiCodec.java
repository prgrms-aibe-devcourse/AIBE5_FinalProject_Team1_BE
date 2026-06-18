package com.team1.codedock.domain.chat.util;

import java.util.List;
import java.util.Map;

public final class ChatContentEmojiCodec {

    private static final String TOKEN_PREFIX = "[[emoji:";
    private static final String TOKEN_SUFFIX = "]]";

    private static final List<EmojiMapping> EMOJI_MAPPINGS = List.of(
            new EmojiMapping("like", "👍"),
            new EmojiMapping("dislike", "👎"),
            new EmojiMapping("heart", "❤️"),
            new EmojiMapping("heart", "❤"),
            new EmojiMapping("laugh", "😂"),
            new EmojiMapping("smile", "😄"),
            new EmojiMapping("surprised", "😮"),
            new EmojiMapping("sad", "😢"),
            new EmojiMapping("cry", "😭"),
            new EmojiMapping("angry", "😡"),
            new EmojiMapping("thinking", "🤔"),
            new EmojiMapping("clap", "👏"),
            new EmojiMapping("pray", "🙏"),
            new EmojiMapping("eyes", "👀"),
            new EmojiMapping("fire", "🔥"),
            new EmojiMapping("rocket", "🚀"),
            new EmojiMapping("party", "🎉"),
            new EmojiMapping("check", "✅"),
            new EmojiMapping("cross", "❌"),
            new EmojiMapping("star", "⭐"),
            new EmojiMapping("bulb", "💡"),
            new EmojiMapping("bug", "🐛"),
            new EmojiMapping("fix", "🔧"),
            new EmojiMapping("memo", "📝"),
            new EmojiMapping("coffee", "☕️"),
            new EmojiMapping("coffee", "☕")
    );

    private static final Map<String, String> EMOJI_BY_KEY = Map.ofEntries(
            Map.entry("like", "👍"),
            Map.entry("dislike", "👎"),
            Map.entry("heart", "❤️"),
            Map.entry("laugh", "😂"),
            Map.entry("smile", "😄"),
            Map.entry("surprised", "😮"),
            Map.entry("sad", "😢"),
            Map.entry("cry", "😭"),
            Map.entry("angry", "😡"),
            Map.entry("thinking", "🤔"),
            Map.entry("clap", "👏"),
            Map.entry("pray", "🙏"),
            Map.entry("eyes", "👀"),
            Map.entry("fire", "🔥"),
            Map.entry("rocket", "🚀"),
            Map.entry("party", "🎉"),
            Map.entry("check", "✅"),
            Map.entry("cross", "❌"),
            Map.entry("star", "⭐"),
            Map.entry("bulb", "💡"),
            Map.entry("bug", "🐛"),
            Map.entry("fix", "🔧"),
            Map.entry("memo", "📝"),
            Map.entry("coffee", "☕")
    );

    private ChatContentEmojiCodec() {
    }

    public static String encode(String content) {
        if (content == null) {
            return null;
        }

        String encoded = content;
        for (EmojiMapping mapping : EMOJI_MAPPINGS) {
            encoded = encoded.replace(mapping.emoji(), token(mapping.key()));
        }
        return encoded;
    }

    public static String decode(String content) {
        if (content == null) {
            return null;
        }

        String decoded = content;
        for (Map.Entry<String, String> entry : EMOJI_BY_KEY.entrySet()) {
            decoded = decoded.replace(token(entry.getKey()), entry.getValue());
        }
        return decoded;
    }

    private static String token(String key) {
        return TOKEN_PREFIX + key + TOKEN_SUFFIX;
    }

    private record EmojiMapping(String key, String emoji) {
    }
}
