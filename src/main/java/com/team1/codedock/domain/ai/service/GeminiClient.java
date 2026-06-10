package com.team1.codedock.domain.ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class GeminiClient {

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public GeminiClient(
            RestClient.Builder builder,
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model:gemini-2.0-flash}") String model,
            ObjectMapper objectMapper
    ) {
        this.restClient = builder.build();
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    public ErdGenerationResult generateErd(List<String> entitySources) {
        String prompt = buildErdPrompt(entitySources);

        Map<String, Object> request = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json")
        );

        GeminiResponse response = restClient.post()
                .uri(GEMINI_API_URL, model, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);

        String jsonText = response.candidates().get(0).content().parts().get(0).text();

        try {
            return objectMapper.readValue(jsonText, ErdGenerationResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Gemini 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String buildErdPrompt(List<String> entitySources) {
        String sourcesText = String.join("\n\n---\n\n", entitySources);
        return """
                다음 Java Spring Boot @Entity 클래스들을 분석하여 Mermaid ERD와 테이블 정보를 생성해주세요.

                [엔티티 소스코드]
                %s

                다음 JSON 형식으로 응답해주세요:
                {
                  "mermaidCode": "erDiagram\\n...",
                  "tables": [
                    {
                      "tableName": "테이블명",
                      "schemaDefinition": "컬럼 정보 JSON 문자열",
                      "description": "테이블 설명"
                    }
                  ]
                }
                """.formatted(sourcesText);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<Candidate> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErdGenerationResult(String mermaidCode, List<ErdTableInfo> tables) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErdTableInfo(String tableName, String schemaDefinition, String description) {}
}
