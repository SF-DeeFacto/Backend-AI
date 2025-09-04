package com.deefacto.ai_service.Recommendation.Service;

import com.deefacto.ai_service.Recommendation.domain.RecommendThresholdDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThresholdBedrockService {
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private static final String BEDROCK_ENDPOINT = "https://bedrock-agent-runtime.%s.amazonaws.com/agents/%s/agentAliases/%s/sessions/%s/text";

    @Value("${aws.bedrock.model-id:anthropic.claude-3-sonnet-20240229-v1:0}")
    private String modelId;

    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Value("${aws.bedrock.threshold-agent-id}")
    private String ThresholdAgentId;

    @Value("${aws.bedrock.threshold-agent-alias-id}")
    private String ThresholdAgentAliasId;

    /**
     * AWS Bedrock 에이전트 호출 (JSON 형식)
     */
    public String invokeBedrockAgent(String inputText, Map<String, Object> sessionAttributes) {
        if (ThresholdAgentId.isEmpty() || ThresholdAgentAliasId.isEmpty()) {
            log.warn("Bedrock Agent ID 또는 Alias ID가 설정되지 않았습니다. 모델 직접 호출로 대체합니다.");
            return invokeBedrockModel(inputText);
        }

        try {
            // Bedrock Agent 요청 JSON 구성
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("inputText", inputText);

            if (sessionAttributes != null && !sessionAttributes.isEmpty()) {
                requestBody.put("sessionAttributes", sessionAttributes);
            }
            sessionAttributes.put("timestamp", LocalDateTime.now().toString());

            // 세션 ID 생성 (실제 환경에서는 사용자별로 관리)
            requestBody.put("sessionId", "session-" + System.currentTimeMillis());

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.info("Bedrock Agent 요청: {}", jsonBody);

            // AWS API 호출
            String response = callBedrockAgentAPI(jsonBody);
            log.info("Bedrock Agent 응답: {}", response);

            // SSE + Base64 디코딩 포함 파싱
            String parsedText = parseAgentResponse(response);
            log.info("파싱된 응답 텍스트: {}", parsedText);

            return parsedText;

        } catch (Exception e) {
            log.error("Bedrock Agent 호출 실패", e);
            throw new RuntimeException("Bedrock 호출 실패", e);
        }
    }

    /**
     * Bedrock Runtime 모델 직접 호출 (JSON 형식)
     */
    public String invokeBedrockModel(String prompt) {
        try {
            // Claude 3 요청 JSON 구성
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
            requestBody.put("max_tokens", 1000);
            requestBody.put("temperature", 0.7);

            // messages 배열 구성
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);
            requestBody.put("messages", new Map[]{message});

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.info("Bedrock Model 요청: {}", jsonBody);

            // AWS API 호출
            String response = callBedrockModelAPI(jsonBody);
            log.info("Bedrock Model 응답: {}", response);

            return parseModelResponse(response);

        } catch (Exception e) {
            log.error("Bedrock Model 호출 중 오류 발생", e);
            return "AI 모델 호출 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    /**
     * 통합 텍스트 생성 메서드 (모델 직접 호출 우선)
     */
    public String generateText(String prompt) {
        // Agent 설정이 완전하지 않으면 모델 직접 호출 사용
        if (ThresholdAgentId.isEmpty() || ThresholdAgentAliasId.isEmpty()) {
            log.info("Agent 설정이 없어 모델 직접 호출을 사용합니다.");
            return invokeBedrockModel(prompt);
        }

        // Agent가 설정되어 있으면 Agent 호출 시도, 실패하면 모델 직접 호출로 fallback
        try {
            Map<String, Object> sessionAttributes = new HashMap<>();
            sessionAttributes.put("reportType", "automated");
            sessionAttributes.put("timestamp", LocalDateTime.now().toString());

            return invokeBedrockAgent(prompt, sessionAttributes);
        } catch (Exception e) {
            log.warn("Bedrock Agent 호출 실패, 모델 직접 호출로 대체합니다: {}", e.getMessage());
            return invokeBedrockModel(prompt);
        }
    }


    // Bedrockagent 호출 API
    private String callBedrockAgentAPI(String jsonBody) throws IOException {
        // Bedrock Agent의 올바른 엔드포인트 형식
        String endpoint = String.format(
                BEDROCK_ENDPOINT, region, ThresholdAgentId, ThresholdAgentAliasId, "session-" + System.currentTimeMillis()
        );

        HttpPost request = new HttpPost(endpoint);
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Accept", "application/json");

        // Bedrock Agent 전용 요청 body 구조
        Map<String, Object> agentRequestBody = new HashMap<>();

        // JSON에서 inputText 추출
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonBody);
            String inputText = jsonNode.path("inputText").asText();

            agentRequestBody.put("inputText", inputText);
            agentRequestBody.put("enableTrace", false);
            agentRequestBody.put("endSession", false);

            String agentJsonBody = objectMapper.writeValueAsString(agentRequestBody);
            log.info("Bedrock Agent 실제 요청 Body: {}", agentJsonBody);

            // 페이로드 설정 (서명 전에 설정해야 함)
            request.setEntity(new StringEntity(agentJsonBody, StandardCharsets.UTF_8));

            // AWS 서명 추가
            addAwsSignature(request, agentJsonBody);

        } catch (JsonProcessingException e) {
            log.error("JSON 파싱 오류", e);
            throw new IOException("요청 JSON 파싱 실패", e);
        }

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());

            if (statusCode >= 200 && statusCode < 300) {
                return responseBody;
            } else {
                log.error("Bedrock Agent API 호출 실패: {} - {}", statusCode, responseBody);
                throw new IOException("API 호출 실패: " + statusCode + " - " + responseBody);
            }
        }
    }

    /**
     * Bedrock Model API 호출
     */
    private String callBedrockModelAPI(String jsonBody) throws IOException {
        // 모델 ID URL 인코딩 (콜론 등 특수문자 처리)
        String encodedModelId = URLEncoder.encode(modelId, StandardCharsets.UTF_8);

        String endpoint = String.format(
                "https://bedrock-runtime.%s.amazonaws.com/model/%s/invoke",
                region, encodedModelId
        );

        HttpPost request = new HttpPost(endpoint);
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Accept", "application/json");

        // 페이로드 설정 (서명 전에 설정해야 함)
        request.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

        // AWS 서명 추가
        addAwsSignature(request, jsonBody);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());

            if (statusCode >= 200 && statusCode < 300) {
                return responseBody;
            } else {
                log.error("Bedrock Model API 호출 실패: {} - {}", statusCode, responseBody);
                throw new IOException("API 호출 실패: " + statusCode);
            }
        }
    }

    /**
     * AWS 서명 추가 (실제 AWS4 서명 알고리즘 구현)
     */
    private void addAwsSignature(HttpPost request, String payload) {
        try {
            // 현재 시간 (UTC)
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
            String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 서비스 이름 - AWS 공식 문서에 따라 모든 Bedrock 서비스는 'bedrock' 사용
            String service = "bedrock";

            // Step 1: 정규화된 요청 문자열 생성
            String canonicalRequest = createCanonicalRequest(request, payload, amzDate);
            log.debug("Canonical Request: {}", canonicalRequest);

            // Step 2: 서명할 문자열 생성
            String credentialScope = String.format("%s/%s/%s/aws4_request", dateStamp, region, service);
            String stringToSign = createStringToSign(amzDate, credentialScope, canonicalRequest);
            log.debug("String to Sign: {}", stringToSign);

            // Step 3: 서명 계산
            String signature = calculateSignature(secretKey, dateStamp, region, service, stringToSign);
            log.debug("Signature: {}", signature);

            // Step 4: Authorization 헤더 생성
            String authorization = String.format(
                    "AWS4-HMAC-SHA256 Credential=%s/%s, SignedHeaders=content-type;host;x-amz-date, Signature=%s",
                    accessKey, credentialScope, signature
            );

            // 헤더 설정
            request.setHeader("Authorization", authorization);
            request.setHeader("X-Amz-Date", amzDate);

            log.info("AWS 서명이 성공적으로 추가되었습니다.");

        } catch (Exception e) {
            log.error("AWS 서명 생성 중 오류 발생", e);
            throw new RuntimeException("AWS 서명 생성 실패", e);
        }
    }

    /**
     * 정규화된 요청 문자열 생성
     */
    private String createCanonicalRequest(HttpPost request, String payload, String amzDate) throws Exception {
        String method = "POST";
        // URI 패스를 그대로 사용 (이미 인코딩된 상태)
        String canonicalUri = request.getURI().getRawPath();
        String canonicalQueryString = ""; // POST 요청이므로 빈 문자열

        // 정규화된 헤더 생성 (content-type 포함)
        Map<String, String> headers = new TreeMap<>();
        headers.put("content-type", "application/json");
        headers.put("host", request.getURI().getHost());
        headers.put("x-amz-date", amzDate);

        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signedHeaders = new StringBuilder();

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            canonicalHeaders.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
            if (signedHeaders.length() > 0) {
                signedHeaders.append(";");
            }
            signedHeaders.append(entry.getKey());
        }

        // 페이로드 해시
        String payloadHash = sha256Hex(payload);

        return method + "\n" +
                canonicalUri + "\n" +
                canonicalQueryString + "\n" +
                canonicalHeaders + "\n" +
                signedHeaders + "\n" +
                payloadHash;
    }

    /**
     * 서명할 문자열 생성
     */
    private String createStringToSign(String amzDate, String credentialScope, String canonicalRequest) throws Exception {
        String algorithm = "AWS4-HMAC-SHA256";
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);

        return algorithm + "\n" +
                amzDate + "\n" +
                credentialScope + "\n" +
                hashedCanonicalRequest;
    }

    /**
     * 서명 계산
     */
    private String calculateSignature(String secretKey, String dateStamp, String region, String service, String stringToSign) throws Exception {
        byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        byte[] kSigning = hmacSha256(kService, "aws4_request");

        byte[] signature = hmacSha256(kSigning, stringToSign);
        return bytesToHex(signature);
    }

    /**
     * HMAC-SHA256 계산
     */
    private byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SHA-256 해시 (16진수 문자열)
     */
    private String sha256Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    /**
     * 바이트 배열을 16진수 문자열로 변환
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Agent 응답 파싱 (스트리밍 응답 처리)
     */
    private String parseAgentResponse(String responseBody) throws JsonProcessingException {
        try {
            log.info("원본 응답 길이: {}", responseBody.length());
            log.info("응답 내용 샘플 (처음 500자): {}",
                    responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

            // Bedrock Agent는 스트리밍 응답으로 여러 이벤트를 보냄
            // :message-type event로 시작하는 부분에서 실제 텍스트를 찾음
            boolean hasMessageType = responseBody.contains(":message-type event");
            boolean hasBytes = responseBody.contains("bytes");

            log.info("응답 분석: message-type event 포함={}, bytes 포함={}", hasMessageType, hasBytes);

            if (hasMessageType) {
                log.info("message-type event 발견, 파싱 시작");
                String[] parts = responseBody.split(":message-type event");
                log.info("분할된 parts 개수: {}", parts.length);

                for (int i = 0; i < parts.length; i++) {
                    String part = parts[i];
                    log.debug("Part {}: {}", i, part.length() > 100 ? part.substring(0, 100) + "..." : part);

                    if (part.contains("bytes")) {
                        log.info("bytes 필드 발견 in part {}", i);
                        // bytes 필드에서 Base64 인코딩된 텍스트 추출
                        try {
                            // 다양한 패턴 시도
                            String[] bytesPatterns = {
                                    "\"bytes\"\\s*:\\s*\"([^\"]+)\"",  // 기본 패턴
                                    "bytes\"\\s*:\\s*\"([^\"]+)\"",    // 앞에 " 없는 경우
                                    "\"bytes\"\\s*:\\s*\"([^\"]*?)\"", // 최소 매칭
                                    "bytes[\"\\s]*:[\"\\s]*([^\"\\}]+)" // 더 유연한 패턴
                            };

                            String base64Text = null;
                            for (String bytesPattern : bytesPatterns) {
                                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(bytesPattern);
                                java.util.regex.Matcher matcher = pattern.matcher(part);

                                if (matcher.find()) {
                                    base64Text = matcher.group(1);
                                    log.info("패턴 '{}' 으로 Base64 텍스트 추출 성공", bytesPattern);
                                    break;
                                }
                            }

                            if (base64Text != null) {
                                log.info("추출된 Base64 텍스트 길이: {}", base64Text.length());
                                log.debug("추출된 Base64 텍스트: {}", base64Text.length() > 100 ? base64Text.substring(0, 100) + "..." : base64Text);

                                // Base64 디코딩 시도
                                try {
                                    byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Text);
                                    String decodedText = new String(decodedBytes, "UTF-8");
                                    log.info("디코딩 성공! 텍스트 길이: {}", decodedText.length());
                                    log.info("디코딩된 텍스트 샘플: {}",
                                            decodedText.length() > 200 ? decodedText.substring(0, 200) + "..." : decodedText);

                                    // 조건을 더 관대하게 설정 - 의미있는 텍스트면 반환
                                    if (decodedText.length() > 20) {
                                        log.info("유효한 텍스트로 판단, 반환합니다.");
                                        return cleanAndFormatText(decodedText);
                                    } else {
                                        log.info("텍스트가 너무 짧습니다: {}", decodedText.length());
                                    }
                                } catch (IllegalArgumentException e) {
                                    log.warn("Base64 디코딩 실패 (잘못된 형식): {}", e.getMessage());
                                    // 디코딩 실패 시 원본 텍스트도 시도
                                    if (base64Text.matches(".*[가-힣].*")) {
                                        return base64Text.trim();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.debug("bytes 패턴 매칭 실패: {}", e.getMessage());
                        }
                    }
                }
            }

            // 대안: 응답에서 직접 Base64 패턴을 찾아 디코딩 시도
            log.info("기본 패턴 매칭 실패, 직접 Base64 텍스트 추출 시도");
            if (hasBytes) {
                // 로그에서 확인된 Base64 텍스트 패턴을 직접 추출
                String[] lines = responseBody.split("\\n");
                for (String line : lines) {
                    if (line.contains("bytes") && line.contains("\"")) {
                        // JSON 형태의 bytes 필드에서 값 추출
                        int start = line.indexOf("\"bytes\":\"");
                        if (start != -1) {
                            start += "\"bytes\":\"".length();
                            int end = line.indexOf("\"", start);
                            if (end != -1) {
                                String base64Text = line.substring(start, end);
                                log.info("직접 추출한 Base64 텍스트 길이: {}", base64Text.length());

                                try {
                                    byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Text);
                                    String decodedText = new String(decodedBytes, "UTF-8");
                                    log.info("직접 디코딩 성공! 텍스트 길이: {}", decodedText.length());
                                    log.info("디코딩된 텍스트: {}",
                                            decodedText.length() > 200 ? decodedText.substring(0, 200) + "..." : decodedText);

                                    if (decodedText.length() > 20) {
                                        return cleanAndFormatText(decodedText);
                                    }
                                } catch (Exception e) {
                                    log.warn("직접 Base64 디코딩 실패: {}", e.getMessage());
                                }
                            }
                        }
                    }
                }
            }

            // 일반적인 텍스트에서 한글 추출
            String koreanText = extractKoreanText(responseBody);
            if (!koreanText.isEmpty()) {
                return koreanText;
            }

            // JSON 파싱 시도 (단, 바이너리 데이터 제거 후)
            String cleanResponse = responseBody.replaceAll("[\\x00-\\x1F\\x7F]", ""); // 제어 문자 제거
            if (cleanResponse.trim().startsWith("{")) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(cleanResponse);

                    JsonNode outputText = jsonNode.path("output").path("text");
                    if (!outputText.isMissingNode()) {
                        return outputText.asText();
                    }

                    JsonNode completion = jsonNode.path("completion");
                    if (!completion.isMissingNode()) {
                        return completion.asText();
                    }
                } catch (Exception e) {
                    log.debug("JSON 파싱 실패: {}", e.getMessage());
                }
            }

            return "에이전트 응답을 파싱할 수 없습니다.";

        } catch (Exception e) {
            log.warn("Agent 응답 파싱 실패: {}", e.getMessage());
            String koreanText = extractKoreanText(responseBody);
            return koreanText.isEmpty() ? "에이전트 응답 파싱 실패" : koreanText;
        }
    }

    /**
     * 응답에서 "추천임계치 선택 이유" 관련 한글 텍스트만 추출
     */
    public Map<String, Map<String, String>> extractReasonBySensorEnglish(String text) {
//        Map<String, Map<String, String>> reasons = new HashMap<>();
//        Pattern pattern = Pattern.compile("(.+ \\(.+\\)):\\n-\\s+([\\s\\S]*?)(?=\\n\\n|In summary)");
//        Matcher matcher = pattern.matcher(text);
//
//        while (matcher.find()) {
//            String title = matcher.group(1).trim();
//            String content = matcher.group(2).trim();
//
//            String sensorKey = title.split(" ")[0].toLowerCase();
//            String mappedKey = mapSensorType(sensorKey);
//
//            if (!mappedKey.isEmpty()) {
//                Map<String, String> details = new HashMap<>();
//                details.put("title", title);
//                details.put("content", content);
//                reasons.put(mappedKey, details);
//            }
//        }
//
//        log.info("reasons: "+reasons);
//        return reasons;

        Map<String, Map<String, String>> reasons = new HashMap<>();

        // ⚠️ 정규식 수정: 첫 문장을 건너뛰고, 제목을 정확히 분리하며, 내용의 경계를 명확히 설정합니다.
        Pattern pattern = Pattern.compile(
                "(Temperature|Humidity|Wind Direction|Electrostatic Discharge|Particle Counts) \\(.+?\\):-([\\s\\S]*?)(?=(?:Temperature|Humidity|Wind Direction|Electrostatic Discharge|Particle Counts) \\(.+?\\):|In summary, the analysis|\\Z)"
        );
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String fullTitle = matcher.group(1).trim(); // 그룹 1: 제목
            String content = matcher.group(2).trim();   // 그룹 2: 내용

            String mappedKey;
            if (fullTitle.equals("Particle Counts")) {
                mappedKey = "particle";
            } else {
                mappedKey = mapSensorType(fullTitle);
            }

            if (!mappedKey.isEmpty()) {
                Map<String, String> details = new HashMap<>();
                details.put("title", fullTitle);
                details.put("content", content);
                reasons.put(mappedKey, details);
            }
        }
        return reasons;
    }

    private String extractKoreanText(String responseBody) {
        try {
            // 추천임계치 관련 키워드 우선 패턴
            String[] patterns = {
                    "추천임계치.*?(?=\\n|$)",
                    "임계치.*선택.*?(?=\\n|$)",
                    "선택한 이유.*?(?=\\n|$)",
                    "[가-힣][가-힣\\s\\.,\\!\\?:;0-9-]+[가-힣\\.]"
            };

            StringBuilder extractedText = new StringBuilder();

            for (String pattern : patterns) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.MULTILINE);
                java.util.regex.Matcher m = p.matcher(responseBody);
                while (m.find()) {
                    String match = m.group().trim();
                    if (match.length() > 5 && !extractedText.toString().contains(match)) {
                        extractedText.append(match).append(" ");
                    }
                }
                // 이미 추천임계치 관련 텍스트를 찾았으면 일반 패턴은 실행하지 않아도 됨
                if (extractedText.length() > 0 && !pattern.equals(patterns[patterns.length - 1])) {
                    break;
                }
            }

            String result = extractedText.toString().trim();
            if (result.length() > 0) {
                log.debug("추출된 추천임계치 설명: {}", result);
                return result;
            }

        } catch (Exception e) {
            log.debug("추천임계치 텍스트 추출 실패: {}", e.getMessage());
        }
        return "";
    }

//    public Map<String, String> extractReasonBySensorKorean(String text) {
//        Map<String, String> reasons = new HashMap<>();
//        String[] lines = text.split("\n");
//        String currentTitle = null;
//        StringBuilder currentContent = new StringBuilder();
//
//        for (String line : lines) {
//            line = line.trim();
//            if (line.isEmpty()) continue;
//
//            if (line.matches("\\d+\\.\\s.*센서 데이터 분석.*")) {
//                // 이전 제목/내용 저장
//                if (currentTitle != null) {
//                    reasons.put(currentTitle, currentContent.toString().trim());
//                }
//                // 새로운 제목 시작
//                currentTitle = line.replaceAll("\\d+\\.\\s*", "").replace(":", "");
//                currentContent.setLength(0);
//            } else {
//                currentContent.append(line).append(" ");
//            }
//        }
//        if (currentTitle != null) {
//            reasons.put(currentTitle, currentContent.toString().trim());
//        }
//        return reasons;
//    }

    public Map<String, Map<String, String>> extractReasonBySensorKorean(String text) {
        Map<String, Map<String, String>> reasons = new HashMap<>();

        // ⚠️ 한글 텍스트에 맞게 수정된 정규식: 각 센서 블록 전체를 매칭
        // 그룹 1: 전체 제목 (ex: "1. 온도 센서 데이터 분석:")
        // 그룹 2: 센서 이름 (ex: "온도")
        // 그룹 3: 센서 내용
        Pattern pattern = Pattern.compile(
                "\\d+\\.\\s(.*?)\\s센서 데이터 분석:([\\s\\S]*?)(?=\\d+\\.\\s.*?센서 데이터 분석:|\\Z)"
        );
        Matcher matcher = pattern.matcher(text);

        // 첫 번째 매치부터 시작하여 모든 센서 블록을 순회
        while (matcher.find()) {
            String fullTitle = "1. " + matcher.group(1).trim() + " 센서 데이터 분석"; // ex: "온도" -> "1. 온도 센서 데이터 분석"
            String sensorName = matcher.group(1).trim(); // "온도", "습도" 등
            String content = matcher.group(2).trim();

            // 파티클의 경우 '0.1' 등의 내용이 있을 수 있어 mapSensorType에 추가 로직 필요
            String mappedKey = mapSensorTypeKorean(sensorName);

            if (!mappedKey.isEmpty()) {
                Map<String, String> details = new HashMap<>();
                details.put("title", fullTitle);
                details.put("content", content);
                reasons.put(mappedKey, details);
            }
        }

        log.info("Korean reasons: " + reasons);
        return reasons;
    }


    /**
     * 텍스트 정리 및 포맷팅
     */
    private String cleanAndFormatText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        // 제어 문자 제거 (단, 개행 문자는 유지)
        String cleaned = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

        // 불필요한 공백 정리
        cleaned = cleaned.replaceAll("[ \\t]+", " "); // 연속된 스페이스/탭을 하나로
        cleaned = cleaned.replaceAll("\\n\\s*\\n\\s*\\n", "\n\n"); // 연속된 빈 줄을 두 줄로 제한

        // 시작과 끝 공백 제거
        cleaned = cleaned.trim();

        log.debug("텍스트 정리 완료. 원본 길이: {}, 정리 후 길이: {}", text.length(), cleaned.length());

        return cleaned;
    }

    /**
     * Model 응답 파싱 (Claude 형식)
     */
    private String parseModelResponse(String responseBody) throws JsonProcessingException {
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        JsonNode content = jsonNode.path("content");

        if (content.isArray() && content.size() > 0) {
            return content.get(0).path("text").asText();
        }

        return "모델 응답을 파싱할 수 없습니다.";
    }


    // Dto에 맞게 변환
    public List<RecommendThresholdDto> convertToDto(Map<String, Map<String, String>> reasons, JsonNode dataNode) {
        List<RecommendThresholdDto> dtos = new ArrayList<>();

        for (JsonNode sensorNode : dataNode) {
            RecommendThresholdDto dto = new RecommendThresholdDto();

            String originalSensorType = sensorNode.path("sensorType").asText();
            String mappedSensorType = mapSensorType(originalSensorType);
            dto.setSensorType(mappedSensorType);
            dto.setZoneId(sensorNode.path("zoneId").asText());

            Map<String, String> reason = reasons.getOrDefault(mappedSensorType, new HashMap<>());
            if (originalSensorType.startsWith("particle_")) {
                reason = reasons.getOrDefault("particle", new HashMap<>());
            } else {
                reason = reasons.getOrDefault(mappedSensorType, new HashMap<>());
            }
            dto.setReasonTitle(reason.get("title"));
            dto.setReasonContent(reason.get("content"));

            // 임계치 값 매핑
            if ("electrostatic".equals(mappedSensorType) || mappedSensorType.startsWith("particle")) {
                dto.setWarningHigh(sensorNode.path("normal").asDouble());
                dto.setAlertHigh(sensorNode.path("warning").asDouble());
                dto.setWarningLow(null);
                dto.setAlertLow(null);
            } else {
                dto.setWarningHigh(sensorNode.path("warningHigh").asDouble());
                dto.setAlertHigh(sensorNode.path("alertHigh").asDouble());
                dto.setWarningLow(sensorNode.path("warningLow").asDouble());
                dto.setAlertLow(sensorNode.path("alertLow").asDouble());
            }
            dtos.add(dto);
        }
        return dtos;
    }

    public String mapSensorType(String type) {
        // ⚠️ 매핑 로직 수정: 센서 제목 전체를 기준으로 매핑합니다.
        switch (type) {
            case "Temperature":
            case "temp": return "temperature";
            case "Humidity":
            case "hum": return "humidity";
            case "Wind Direction":
            case "wind":
            case "wd": return "wind";
            case "Electrostatic Discharge":
            case "esd": return "electrostatic";
            case "Particle Counts":
            case "lpm": return "particle";
            default: return type;
        }
    }

    // 이전에 사용했던 mapSensorType 메서드도 업데이트해야 합니다.
    public String mapSensorTypeKorean(String originalType) {
        switch (originalType) {
            case "온도": return "temperature";
            case "습도": return "humidity";
            case "풍향": return "wind";
            case "정전기": return "electrostatic";
            case "파티클": return "particle";
            default: return originalType;
        }
    }

    private String findMatchingTitle(String sensorType, Set<String> titles) {
        for (String title : titles) {
            if (title.contains(sensorType.substring(0, 4))) return title; // 예: "temp" → "온도 센서 데이터 분석"
        }
        return null;
    }
}
