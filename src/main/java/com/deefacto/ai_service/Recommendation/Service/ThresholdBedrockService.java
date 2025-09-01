package com.deefacto.ai_service.Recommendation.Service;

import com.deefacto.ai_service.Recommendation.domain.SensorThresholdUpdateRequestDto;
import com.deefacto.ai_service.common.dto.ApiResponseDto;
import com.deefacto.ai_service.common.exception.ErrorCode;
import com.deefacto.ai_service.common.service.BedrockService;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThresholdBedrockService {
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private static final String BEDROCK_ENDPOINT = "https://bedrock-agent-runtime.%s.amazonaws.com/agents/%s/agentAliases/%s/text";

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
            return "";
        }

        try {
            // Bedrock Agent 요청 JSON 구성
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("inputText", inputText);

            if (sessionAttributes != null && !sessionAttributes.isEmpty()) {
                requestBody.put("sessionAttributes", sessionAttributes);
            }

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.info("Bedrock Agent 요청: {}", jsonBody);

            // AWS API 호출
            String response = callBedrockAgentAPI(jsonBody);
            log.info("Bedrock Agent 응답: {}", response);

            return extractKoreanText(response);

        } catch (Exception e) {
            return ErrorCode.INTERNAL_ERROR.getMessage();
        }
    }


    // Bedrockagent 호출 API
    private String callBedrockAgentAPI(String jsonBody) throws IOException {
        // Bedrock Agent의 올바른 엔드포인트 형식
        String endpoint = String.format(
                BEDROCK_ENDPOINT, region, ThresholdAgentId, ThresholdAgentAliasId
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
            agentRequestBody.put("endSession", true);

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
     * 응답에서 "추천임계치 선택 이유" 관련 한글 텍스트만 추출
     */
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


}
