package com.deefacto.ai_service.common.service;

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

import com.deefacto.ai_service.remote.Service.ReportProducer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BedrockService {

    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final LambdaTestService lambdaTestService;

    @Value("${aws.bedrock.model-id:anthropic.claude-3-sonnet-20240229-v1:0}")
    private String modelId;

    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Value("${aws.bedrock.agent-id:}")
    private String agentId;

    @Value("${aws.bedrock.agent-alias-id:}")
    private String agentAliasId;

    /**
     * AWS Bedrock 에이전트 호출 (JSON 형식)
     */
    public String invokeBedrockAgent(String inputText, Map<String, Object> sessionAttributes) {
        if (agentId.isEmpty() || agentAliasId.isEmpty()) {
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
            
            // 세션 ID 생성 (실제 환경에서는 사용자별로 관리)
            requestBody.put("sessionId", "session-" + System.currentTimeMillis());
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.info("Bedrock Agent 요청: {}", jsonBody);

            // AWS API 호출
            String response = callBedrockAgentAPI(jsonBody);
            log.info("Bedrock Agent 응답: {}", response);
            
            return parseAgentResponse(response);
            
        } catch (Exception e) {
            log.warn("Bedrock Agent 호출 실패, 모델 직접 호출로 대체: {}", e.getMessage());
            return invokeBedrockModel(inputText);
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
        if (agentId.isEmpty() || agentAliasId.isEmpty()) {
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
    private final ReportProducer reportProducer;
    /**
     * 리포트 생성용 프롬프트 (JSON 데이터 포함 + Lambda API 호출)
     */
    public String generateReportSummary(String reportType, Map<String, Object> requestData) {
        log.info("리포트 생성 요청 - 타입: {}, 데이터: {}", reportType, requestData);
        log.info("zone test : {}", requestData.get("zone"));
        reportProducer.requestAlimForStore(requestData.get("zone").toString());
        String lambdaResult = "";
        String bedrockPrompt;
        
        // Lambda API 호출
        if (requestData != null && !requestData.isEmpty()) {
            try {
                lambdaResult = callLambdaAPI(requestData);
                log.info("Lambda API 응답 길이: {}", lambdaResult.length());
                log.info("Lambda API 응답 샘플: {}", 
                    lambdaResult.length() > 200 ? lambdaResult.substring(0, 200) + "..." : lambdaResult);
            } catch (Exception e) {
                log.warn("Lambda API 호출 실패: {}", e.getMessage());
                lambdaResult = "Lambda API 호출 실패: " + e.getMessage();
            }
        }
        
        // Bedrock 프롬프트 생성
        if (requestData != null && !requestData.isEmpty()) {
            try {
                String jsonData = objectMapper.writeValueAsString(requestData);
                
                // Lambda 결과를 포함한 프롬프트 생성
                if (!lambdaResult.isEmpty() && !lambdaResult.startsWith("Lambda API 호출 실패")) {
                    bedrockPrompt = String.format(jsonData);
                } else {
                    bedrockPrompt = String.format(jsonData);
                }
            } catch (Exception e) {
                log.warn("JSON 변환 실패, 기본 프롬프트 사용: {}", e.getMessage());
                bedrockPrompt = String.format(
                    "요청 데이터: %s를 기반으로 %s 리포트를 생성해주세요. (생성 시간: %s)",
                    requestData.toString(),
                    reportType,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                );
            }
        } else {
            // 기존 로직 (데이터가 없는 경우)
            bedrockPrompt = String.format(
                "",
                reportType,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
        }
        
        log.info("생성된 프롬프트: {}", bedrockPrompt.length() > 200 ? bedrockPrompt.substring(0, 200) + "..." : bedrockPrompt);
        // 여길 바꿔서 lamda 파싱값을 넣는다!!!!!!!!!!!!!!
//                return generateText(bedrockPrompt);
        String string_temp = generateText(bedrockPrompt);
        log.info("final 리포트 결과: {}", string_temp);
        log.info("final 람다 결과 : {}", lambdaResult);
//        return generateText(bedrockPrompt);
        Pattern pattern = Pattern.compile("\\{[^}]+\\}");
        Matcher matcher = pattern.matcher(string_temp);
        Pattern pattern2 = Pattern.compile("https?://[^\"]+");
        Matcher matcher2 = pattern2.matcher(lambdaResult);
        List<String> urls = new ArrayList<>();
        while(matcher2.find()) {
            urls.add(matcher2.group());
        }
        StringBuffer result = new StringBuffer();
        int i = 0;
        while(matcher.find() && i<urls.size()) {
            matcher.appendReplacement(result, "<img src=\"" + urls.get(i) + "\">");
            i++;
        }
        matcher.appendTail(result);
        log.info("final_찐 리포트 결과 : {}", result.toString());
        return result.toString();
        // 리포트 완성
    }

    /**
     * Lambda API 호출
     */
    private String callLambdaAPI(Map<String, Object> requestData) throws IOException {
//        String lambdaUrl = "http://localhost:8085/reports/lambda/test/sync?functionName=report-graph-lambda";
//
//        HttpPost request = new HttpPost(lambdaUrl);
//        request.setHeader("Content-Type", "application/json");
//        request.setHeader("Accept", "application/json");
//        request.setHeader("X-Employee-Id", "AI-System");
        
        try {
            // Request Body 설정
            String jsonBody = objectMapper.writeValueAsString(requestData);
//            request.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
            
//            log.info("Lambda API 호출 - URL: {}", lambdaUrl);
            log.info("Lambda API 요청 Body: {}", jsonBody);
            
//            try (CloseableHttpResponse response = httpClient.execute(request)) {
//                int statusCode = response.getStatusLine().getStatusCode();
//                String responseBody = EntityUtils.toString(response.getEntity());
//
//                if (statusCode >= 200 && statusCode < 300) {
//                    log.info("Lambda API 호출 성공 - Status: {}", statusCode);
//                    return parseLambdaResponse(responseBody);
//                } else {
//                    log.error("Lambda API 호출 실패 - Status: {}, Body: {}", statusCode, responseBody);
//                    throw new IOException("Lambda API 호출 실패: " + statusCode + " - " + responseBody);
//                }
//            }
            String functionName = "report-graph-lambda";
            String temp = lambdaTestService.invokeLambdaSync(functionName, requestData);

            return temp;
        } catch (Exception e) {
            log.error("Lambda API 호출 중 오류 발생", e);
            throw new IOException("Lambda API 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Lambda API 응답 파싱 (구조적 데이터 추출)
     */
    private String parseLambdaResponse(String responseBody) {
        try {
            log.info("Lambda 응답 파싱 시작 - 길이: {}", responseBody.length());
            log.info("Lambda 원본 응답: {}", responseBody);
            
            // JSON 응답 파싱 시도
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            StringBuilder parsedData = new StringBuilder();
            
            // API 응답 구조에 따라 데이터 추출
            if (jsonNode.has("data")) {
                JsonNode dataNode = jsonNode.get("data");
                if (dataNode.isTextual()) {
                    String result = dataNode.asText();
                    String replaced = result.replaceAll("\\\\\"", "\"");
                    log.info("Lambda data 필드에서 텍스트 추출: {}", 
                        result.length() > 100 ? result.substring(0, 100) + "..." : result);
                    parsedData.append(replaced);
                } else if (dataNode.isObject()) {
                    // data가 객체인 경우 구조적으로 파싱
                    parsedData.append("📊 Lambda 분석 결과:\n\n");
                    parsedData.append(parseStructuredData(dataNode));
                } else if (dataNode.isArray()) {
                    // data가 배열인 경우
                    parsedData.append("📊 Lambda 분석 결과 (목록):\n\n");
                    for (int i = 0; i < dataNode.size(); i++) {
                        parsedData.append(String.format("%d. %s\n", i + 1, parseStructuredData(dataNode.get(i))));
                    }
                }
            }
            
            // message 필드 확인
            if (jsonNode.has("message")) {
                String message = jsonNode.get("message").asText();
                log.info("Lambda message 필드 추출: {}", message);
                if (parsedData.length() > 0) {
                    parsedData.append("\n\n📝 메시지: ").append(message);
                } else {
                    parsedData.append("📝 Lambda 메시지: ").append(message);
                }
            }
            
            // result 필드 확인
            if (jsonNode.has("result")) {
                JsonNode resultNode = jsonNode.get("result");
                if (resultNode.isTextual()) {
                    String result = resultNode.asText();
                    log.info("Lambda result 필드에서 텍스트 추출: {}", result);
                    if (parsedData.length() > 0) {
                        parsedData.append("\n\n🔍 결과: ").append(result);
                    } else {
                        parsedData.append("🔍 Lambda 결과: ").append(result);
                    }
                } else {
                    String result = parseStructuredData(resultNode);
                    if (parsedData.length() > 0) {
                        parsedData.append("\n\n🔍 결과:\n").append(result);
                    } else {
                        parsedData.append("🔍 Lambda 결과:\n").append(result);
                    }
                }
            }
            
            // success/status 등 추가 필드 확인
            if (jsonNode.has("success")) {
                boolean success = jsonNode.get("success").asBoolean();
                parsedData.append("\n\n✅ 처리 상태: ").append(success ? "성공" : "실패");
            }
            
            if (jsonNode.has("status")) {
                String status = jsonNode.get("status").asText();
                parsedData.append("\n\n📊 상태: ").append(status);
            }
            
            // 추가 메타데이터 파싱
            if (jsonNode.has("timestamp")) {
                String timestamp = jsonNode.get("timestamp").asText();
                parsedData.append("\n\n⏰ 생성 시간: ").append(timestamp);
            }
            
            if (jsonNode.has("executionTime")) {
                String executionTime = jsonNode.get("executionTime").asText();
                parsedData.append("\n\n⚡ 실행 시간: ").append(executionTime);
            }
            
            // 파싱된 데이터가 있으면 반환, 없으면 전체 응답을 구조적으로 파싱
            if (parsedData.length() > 0) {
                String finalResult = parsedData.toString();
                log.info("구조적 파싱 완료 - 결과 길이: {}", finalResult.length());
                return finalResult;
            } else {
                // 전체 JSON을 구조적으로 파싱
                String structuredResult = "📊 Lambda 전체 응답:\n\n" + parseStructuredData(jsonNode);
                log.info("전체 구조적 파싱 완료 - 결과 길이: {}", structuredResult.length());
                return structuredResult;
            }
            
        } catch (Exception e) {
            log.warn("Lambda 응답 JSON 파싱 실패, 원본 텍스트 반환: {}", e.getMessage());
            return "📋 Lambda 원본 응답:\n" + responseBody;
        }
    }
    
    /**
     * JSON 객체를 구조적으로 파싱하여 읽기 쉬운 형태로 변환
     */
    private String parseStructuredData(JsonNode node) {
        StringBuilder result = new StringBuilder();
        
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                
                // 키를 읽기 쉬운 형태로 변환
                String displayKey = formatKey(key);
                
                if (value.isTextual()) {
                    result.append("• ").append(displayKey).append(": ").append(value.asText()).append("\n");
                } else if (value.isNumber()) {
                    result.append("• ").append(displayKey).append(": ").append(value.asText()).append("\n");
                } else if (value.isBoolean()) {
                    result.append("• ").append(displayKey).append(": ").append(value.asBoolean() ? "예" : "아니오").append("\n");
                } else if (value.isArray()) {
                    result.append("• ").append(displayKey).append(":\n");
                    for (int i = 0; i < value.size(); i++) {
                        result.append("  ").append(i + 1).append(". ").append(parseStructuredData(value.get(i))).append("\n");
                    }
                } else if (value.isObject()) {
                    result.append("• ").append(displayKey).append(":\n");
                    String nestedData = parseStructuredData(value);
                    // 중첩된 데이터에 들여쓰기 추가
                    String[] lines = nestedData.split("\n");
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            result.append("  ").append(line).append("\n");
                        }
                    }
                }
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                result.append(i + 1).append(". ").append(parseStructuredData(node.get(i))).append("\n");
            }
        } else {
            result.append(node.asText());
        }
        
        return result.toString();
    }
    
    /**
     * JSON 키를 읽기 쉬운 형태로 변환
     */
    private String formatKey(String key) {
        // 일반적인 키 변환
        switch (key.toLowerCase()) {
            case "timestamp": return "시간";
            case "temperature": return "온도";
            case "humidity": return "습도";
            case "pressure": return "기압";
            case "zone": return "구역";
            case "sensor": return "센서";
            case "sensors": return "센서 목록";
            case "data": return "데이터";
            case "result": return "결과";
            case "status": return "상태";
            case "success": return "성공 여부";
            case "message": return "메시지";
            case "error": return "오류";
            case "warning": return "경고";
            case "info": return "정보";
            case "reportType": return "리포트 유형";
            case "start": return "시작 시간";
            case "end": return "종료 시간";
            case "duration": return "기간";
            case "count": return "개수";
            case "average": return "평균";
            case "max": return "최대값";
            case "min": return "최소값";
            case "total": return "총계";
            default:
                // camelCase를 읽기 쉬운 형태로 변환
                return key.replaceAll("([a-z])([A-Z])", "$1 $2")
                         .substring(0, 1).toUpperCase() + 
                         key.replaceAll("([a-z])([A-Z])", "$1 $2").substring(1);
        }
    }

    /**
     * 리포트 생성용 프롬프트 (기존 메서드 - 하위 호환성)
     */
    public String generateReportSummary(String reportType) {
        return generateReportSummary(reportType, null);
    }

    /**
     * Bedrock Agent API 호출
     */
    private String callBedrockAgentAPI(String jsonBody) throws IOException {
        // Bedrock Agent의 올바른 엔드포인트 형식
        String endpoint = String.format(
            "https://bedrock-agent-runtime.%s.amazonaws.com/agents/%s/agentAliases/%s/sessions/%s/text",
            region, agentId, agentAliasId, "session-" + System.currentTimeMillis()
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
     *
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
     * 응답에서 한글 텍스트 추출
     */
    private String extractKoreanText(String responseBody) {
        try {
            // 로그에서 보이는 실제 한글 텍스트 패턴들
            String[] patterns = {
                "테스트\\s*리포트\\s*요약.*?(?=\\n|$)",
                "시스템\\s*상태.*?(?=\\n|$)", 
                "성능\\s*지표.*?(?=\\n|$)",
                "개선사항.*?(?=\\n|$)",
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
            }
            
            // 기본 한글 라인 추출 (기존 로직)
            if (extractedText.length() == 0) {
                String[] lines = responseBody.split("\\n");
                for (String line : lines) {
                    String cleanLine = line.replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
                    if (cleanLine.matches(".*[가-힣]{2,}.*") && cleanLine.length() > 10) {
                        extractedText.append(cleanLine).append(" ");
                    }
                }
            }
            
            String result = extractedText.toString().trim();
            if (result.length() > 0) {
                log.debug("추출된 한글 텍스트: {}", result);
                return result;
            }
            
        } catch (Exception e) {
            log.debug("한글 텍스트 추출 실패: {}", e.getMessage());
        }
        return "";
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

    /**
     * 모의 응답 생성 (개발/테스트용)
     */
    private String generateMockResponse(String prompt) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        if (prompt.contains("테스트")) {
            return String.format(
                "🤖 AI 생성 테스트 리포트\n\n" +
                "📅 생성 시간: %s\n" +
                "🔍 분석 내용: 시스템 상태 정상, 모든 서비스가 원활히 작동 중입니다.\n" +
                "📈 성능 지표: CPU 사용률 15%%, 메모리 사용률 60%%, 응답시간 평균 120ms\n" +
                "✅ 개선사항: 현재 시스템은 안정적으로 운영되고 있으며, 추가적인 최적화가 가능합니다.\n\n" +
                "이 리포트는 AI에 의해 자동 생성되었습니다.",
                timestamp
            );
        } else if (prompt.contains("월간")) {
            return String.format(
                "📊 월간 종합 리포트 요약\n\n" +
                "📅 리포트 기간: %s\n" +
                "📈 주요 성과: 이번 달 시스템 가동률 99.8%%, 사용자 만족도 상승\n" +
                "🚀 핵심 지표: 트래픽 전월 대비 15%% 증가, 응답 속도 개선\n" +
                "🔧 개선 완료: 데이터베이스 최적화, 캐시 시스템 업그레이드\n" +
                "📋 다음 달 계획: 보안 강화, 모니터링 시스템 확장\n\n" +
                "상세한 분석 결과와 권장사항이 포함된 완전한 리포트입니다.",
                timestamp
            );
        } else {
            return String.format(
                "AI 응답이 생성되었습니다. (생성 시간: %s)\n" +
                "요청하신 내용에 대한 분석을 완료했습니다.\n" +
                "추가적인 세부 정보가 필요하시면 말씀해 주세요.",
                timestamp
            );
        }
    }
}
