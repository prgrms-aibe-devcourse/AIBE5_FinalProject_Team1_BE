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

    public String getModel() {
        return model;
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

    public DocumentGenerationResult generateDocument(List<String> entitySources) {
        String prompt = buildDocumentPrompt(entitySources);

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
            return objectMapper.readValue(jsonText, DocumentGenerationResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Gemini 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String buildDocumentPrompt(List<String> entitySources) {
        String sourcesText = String.join("\n\n---\n\n", entitySources);
        return """
                다음 Java Spring Boot @Entity 클래스들을 분석하여 도메인 모델 설명 문서 초안을 작성해주세요.

                [엔티티 소스코드]
                %s

                다음 JSON 형식으로 응답해주세요:
                {
                  "title": "문서 제목",
                  "content": "마크다운 형식의 문서 내용",
                  "category": "manual"
                }
                """.formatted(sourcesText);
    }

    public ApiSpecChecklistResult generateApiSpecChecklist(String swaggerJson) {
        String prompt = buildApiSpecPrompt(swaggerJson);

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
            return objectMapper.readValue(jsonText, ApiSpecChecklistResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Gemini 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String buildApiSpecPrompt(String swaggerJson) {
        return """
                다음 OpenAPI(Swagger) 명세를 분석하여 누락되었거나 보강이 필요한 API 목록을 체크리스트 형식으로 제안해주세요.

                [Swagger 명세]
                %s

                다음 JSON 형식으로 응답해주세요:
                {
                  "checklist": [
                    {
                      "title": "API 제목",
                      "method": "GET",
                      "endpoint": "/경로",
                      "groupName": "그룹명",
                      "summary": "간단한 설명",
                      "description": "상세 설명"
                    }
                  ]
                }
                """.formatted(swaggerJson);
    }

    public PrAnalysisResult generatePrAnalysis(String combinedDiff) {
        String prompt = buildPrAnalysisPrompt(combinedDiff);

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
            return objectMapper.readValue(jsonText, PrAnalysisResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Gemini 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String buildPrAnalysisPrompt(String combinedDiff) {
        return """
                다음 PR의 파일 diff를 분석하여 요약과 보안 취약점 피드백을 생성해주세요.

                [PR Diff]
                %s

                다음 JSON 형식으로 응답해주세요:
                {
                  "summaryText": "PR 전체 요약 1~2문장",
                  "cautionItems": ["주의사항1", "주의사항2"],
                  "positiveItems": ["긍정적인 점1", "긍정적인 점2"],
                  "riskLevel": "High | Medium | Low",
                  "fileFeedbacks": [
                    {
                      "name": "파일명.java",
                      "path": "src/.../파일명.java",
                      "risk": "높음 | 중간 | 낮음",
                      "vulnerability": "취약점 설명",
                      "fix": "수정 방향 설명",
                      "currentCode": ["현재 코드 라인1", "현재 코드 라인2"],
                      "recommendedCode": ["추천 코드 라인1", "추천 코드 라인2"],
                      "findings": ["23번째 줄: CSRF 전역 비활성화"]
                    }
                  ]
                }
                """.formatted(combinedDiff);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErdGenerationResult(String mermaidCode, List<ErdTableInfo> tables) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErdTableInfo(String tableName, String schemaDefinition, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocumentGenerationResult(String title, String content, String category) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiSpecChecklistResult(List<ApiSpecChecklistItem> checklist) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiSpecChecklistItem(
            String title, String method, String endpoint,
            String groupName, String summary, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrAnalysisResult(
            String summaryText,
            List<String> cautionItems,
            List<String> positiveItems,
            String riskLevel,
            List<PrFileFeedback> fileFeedbacks) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrFileFeedback(
            String name,
            String path,
            String risk,
            String vulnerability,
            String fix,
            List<String> currentCode,
            List<String> recommendedCode,
            List<String> findings) {}
}
