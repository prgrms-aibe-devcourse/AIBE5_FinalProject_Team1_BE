package com.team1.codedock.domain.chat.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ChatContentEmojiCodec {

    private static final String PREFIX = "[[CODEDOCK_EMOJI_CONTENT_V1:";
    private static final String SUFFIX = "]]";

    private ChatContentEmojiCodec() {
    }

    public static String encode(String content) {
        if (content == null) {
            return null;
        }

        if (!shouldEncode(content)) {
            return content;
        }

        String encoded = Base64.getEncoder()
                .encodeToString(content.getBytes(StandardCharsets.UTF_8));
        return PREFIX + encoded + SUFFIX;
    }

    public static String decode(String content) {
        if (content == null || !content.startsWith(PREFIX) || !content.endsWith(SUFFIX)) {
            return content;
        }

        String encoded = content.substring(PREFIX.length(), content.length() - SUFFIX.length());
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return content;
        }
    }

    private static boolean shouldEncode(String content) {
        if (content.startsWith(PREFIX)) {
            return true;
        }

        return content.codePoints().anyMatch(ChatContentEmojiCodec::isEmojiCodePoint);
    }

    private static boolean isEmojiCodePoint(int codePoint) {
        return isInRange(codePoint, 0x1F000, 0x1FAFF)
                || isInRange(codePoint, 0x2600, 0x27BF)
                || isInRange(codePoint, 0x2300, 0x23FF)
                || isInRange(codePoint, 0x2B00, 0x2BFF)
                || isInRange(codePoint, 0x1F1E6, 0x1F1FF)
                || codePoint == 0x00A9
                || codePoint == 0x00AE
                || codePoint == 0x200D
                || codePoint == 0x20E3
                || codePoint == 0x2122
                || codePoint == 0x2139
                || codePoint == 0xFE0F;
    }

    private static boolean isInRange(int codePoint, int startInclusive, int endInclusive) {
        return codePoint >= startInclusive && codePoint <= endInclusive;
    }
}
