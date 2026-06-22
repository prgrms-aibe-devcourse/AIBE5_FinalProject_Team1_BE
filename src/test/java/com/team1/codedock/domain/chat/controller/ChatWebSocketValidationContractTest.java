package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChannelMessageCreateRequest;
import com.team1.codedock.domain.chat.dto.ThreadReplyWebSocketCreateRequest;
import com.team1.codedock.domain.chat.dto.TypingEventRequest;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

class ChatWebSocketValidationContractTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("채널 메시지 WebSocket 요청 DTO는 blank와 4000자 초과 content를 거부한다")
    void channelMessageCreateRequestValidation() {
        assertThat(validator.validate(new ChannelMessageCreateRequest(" ")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("content");

        assertThat(validator.validate(new ChannelMessageCreateRequest("a".repeat(4001))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("content");

        assertThat(validator.validate(new ChannelMessageCreateRequest("a".repeat(4000))))
                .isEmpty();
    }

    @Test
    @DisplayName("스레드 답글 WebSocket 요청 DTO는 blank와 4000자 초과 content를 거부한다")
    void threadReplyWebSocketCreateRequestValidation() {
        assertThat(validator.validate(new ThreadReplyWebSocketCreateRequest("")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("content");

        assertThat(validator.validate(new ThreadReplyWebSocketCreateRequest("a".repeat(4001))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("content");

        assertThat(validator.validate(new ThreadReplyWebSocketCreateRequest("a".repeat(4000))))
                .isEmpty();
    }

    @Test
    @DisplayName("typing WebSocket 요청 DTO는 typing 값이 null이면 거부한다")
    void typingEventRequestValidation() {
        assertThat(validator.validate(new TypingEventRequest(null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("typing");

        assertThat(validator.validate(new TypingEventRequest(true))).isEmpty();
        assertThat(validator.validate(new TypingEventRequest(false))).isEmpty();
    }

    @Test
    @DisplayName("ChatWebSocketController의 모든 WebSocket payload 파라미터에는 @Valid가 유지된다")
    void chatWebSocketPayloadParametersKeepValidAnnotation() throws NoSuchMethodException {
        assertPayloadParameterHasValid(
                "createChannelMessage",
                ChannelMessageCreateRequest.class
        );
        assertPayloadParameterHasValid(
                "createThreadReply",
                ThreadReplyWebSocketCreateRequest.class
        );
        assertPayloadParameterHasValid(
                "sendTypingEvent",
                TypingEventRequest.class
        );
    }

    private static void assertPayloadParameterHasValid(String methodName, Class<?> payloadType)
            throws NoSuchMethodException {
        Method method = findMethod(methodName, payloadType);
        Parameter payloadParameter = findParameter(method, payloadType);

        assertThat(payloadParameter.getAnnotations())
                .extracting(Annotation::annotationType)
                .contains(Valid.class);
    }

    private static Method findMethod(String methodName, Class<?> payloadType) throws NoSuchMethodException {
        for (Method method : ChatWebSocketController.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)
                    && hasParameterType(method, payloadType)) {
                return method;
            }
        }
        throw new NoSuchMethodException(methodName + "(" + payloadType.getSimpleName() + ")");
    }

    private static boolean hasParameterType(Method method, Class<?> payloadType) {
        for (Parameter parameter : method.getParameters()) {
            if (parameter.getType().equals(payloadType)) {
                return true;
            }
        }
        return false;
    }

    private static Parameter findParameter(Method method, Class<?> payloadType) {
        for (Parameter parameter : method.getParameters()) {
            if (parameter.getType().equals(payloadType)) {
                return parameter;
            }
        }
        throw new IllegalArgumentException("payload parameter not found: " + payloadType.getSimpleName());
    }
}
