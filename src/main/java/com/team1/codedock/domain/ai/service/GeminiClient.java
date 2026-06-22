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

    private static final String GROQ_API_URL =
            "https://api.groq.com/openai/v1/chat/completions";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public GeminiClient(
            RestClient.Builder builder,
            @Value("${groq.api-key:}") String apiKey,
            @Value("${groq.model:llama-3.3-70b-versatile}") String model,
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

    private String callGroq(String prompt) {
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "response_format", Map.of("type", "json_object")
        );

        GroqResponse response = restClient.post()
                .uri(GROQ_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GroqResponse.class);

        return response.choices().get(0).message().content();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {}

    public ErdGenerationResult generateErd(List<String> entitySources) {
        try {
            return objectMapper.readValue(callGroq(buildErdPrompt(entitySources)), ErdGenerationResult.class);
        } catch (Exception e) {
            throw new RuntimeException("AI 응답 파싱 실패: " + e.getMessage(), e);
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

    public DocumentGenerationResult generateDocument(List<String> sources, String category,
                                                      String topic, List<String> commits) {
        try {
            String prompt = switch (category) {
                case "release" -> buildReleasePrompt(commits);
                case "faq"     -> buildFaqPrompt(sources, topic);
                default        -> buildManualPrompt(sources, topic);
            };
            return objectMapper.readValue(callGroq(prompt), DocumentGenerationResult.class);
        } catch (Exception e) {
            throw new RuntimeException("AI 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String buildManualPrompt(List<String> sources, String topic) {
        String sourcesText = String.join("\n\n---\n\n", sources);
        String topicLine = (topic != null && !topic.isBlank()) ? "주제: " + topic + "\n\n" : "";
        return """
                반드시 한국어로만 작성해주세요.
                다음 소스코드를 분석하여 외부 사용자(운영팀, CS팀, 비개발자 등)가 이해할 수 있는 사용 설명서를 작성해주세요.
                %s기술적인 내용보다는 서비스 기능과 사용 방법을 쉽게 설명해주세요.

                [소스코드]
                %s

                다음 JSON 형식으로 응답해주세요:
                {
                  "title": "문서 제목",
                  "content": "마크다운 형식의 문서 내용",
                  "category": "manual"
                }
                """.formatted(topicLine, sourcesText);
    }

    private String buildFaqPrompt(List<String> sources, String topic) {
        String sourcesText = String.join("\n\n---\n\n", sources);
        String topicLine = (topic != null && !topic.isBlank()) ? "주제: " + topic + "\n\n" : "";
        return """
                반드시 한국어로만 작성해주세요.
                다음 소스코드를 분석하여 사용자들이 자주 묻는 질문(FAQ)을 작성해주세요.
                %s실제 사용자 관점에서 궁금해할 만한 질문과 명확한 답변을 작성해주세요.

                [소스코드]
                %s

                다음 JSON 형식으로 응답해주세요:
                {
                  "title": "문서 제목",
                  "content": "마크다운 형식의 문서 내용",
                  "category": "faq"
                }
                """.formatted(topicLine, sourcesText);
    }

    private String buildReleasePrompt(List<String> commits) {
        String commitsText = String.join("\n", commits);
        return """
                반드시 한국어로만 작성해주세요.
                다음 커밋 메시지들을 분석하여 사용자가 이해하기 쉬운 릴리즈 노트를 작성해주세요.
                개발 용어보다는 서비스 관점에서 변경된 기능을 설명해주세요.

                [커밋 메시지]
                %s

                다음 JSON 형식으로 응답해주세요:
                {
                  "title": "문서 제목",
                  "content": "마크다운 형식의 문서 내용",
                  "category": "release"
                }
                """.formatted(commitsText);
    }

    public ApiSpecChecklistResult generateApiSpecChecklist(String swaggerJson, List<String> entitySources) {
        try {
            return objectMapper.readValue(callGroq(buildApiSpecPrompt(swaggerJson, entitySources)), ApiSpecChecklistResult.class);
        } catch (Exception e) {
            throw new RuntimeException("AI 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String buildApiSpecPrompt(String swaggerJson, List<String> entitySources) {
        String sourcesText = String.join("\n\n---\n\n", entitySources);
        return """
                다음 Java Spring Boot @Entity 클래스들과 OpenAPI(Swagger) 명세를 함께 분석하여
                도메인 관점에서 누락되었거나 보강이 필요한 API 목록을 체크리스트 형식으로 제안해주세요.

                [엔티티 소스코드]
                %s

                [Swagger 명세]
                %s

                엔티티 구조를 기반으로 어떤 비즈니스 기능이 필요한지 파악하고,
                Swagger에 이미 구현된 API와 비교하여 누락된 API를 제안해주세요.

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
                """.formatted(sourcesText, swaggerJson);
    }

    public PrAnalysisResult generatePrAnalysis(String combinedDiff) {
        try {
            return objectMapper.readValue(callGroq(buildPrAnalysisPrompt(combinedDiff)), PrAnalysisResult.class);
        } catch (Exception e) {
            throw new RuntimeException("AI 응답 파싱 실패: " + e.getMessage(), e);
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
