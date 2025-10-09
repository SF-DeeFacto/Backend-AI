package com.deefacto.ai_service.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class LambdaTestServiceNew {

    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    
    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;
    
    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;
    
    @Value("${cloud.aws.region.static}")
    private String region;
    
    /**
     * Lambda 함수 동기 호출
     */
    public String invokeLambdaSync(String functionName, Map<String, Object> payload) {
        return invokeLambda(functionName, payload, false);
    }
    
    /**
     * Lambda 함수 비동기 호출
     */
    public void invokeLambdaAsync(String functionName, Map<String, Object> payload) {
        invokeLambda(functionName, payload, true);
    }
    
    /**
     * Lambda 함수 호출 (공통 메서드)
     */
    private String invokeLambda(String functionName, Map<String, Object> payload, boolean async) {
        try {
            log.info("Lambda 함수 호출 시작 - 함수: {}, 비동기: {}", functionName, async);
            
            // 페이로드 준비
            String jsonPayload = objectMapper.writeValueAsString(payload);
            log.debug("Lambda 요청 페이로드: {}", jsonPayload);
            
            // Lambda 엔드포인트 URL
            String lambdaUrl = String.format("https://lambda.%s.amazonaws.com/2015-03-31/functions/%s/invocations",
                    region, functionName);
            
            // HTTP 요청 설정 (타임아웃을 짧게 설정)
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(5000)        // 5초 연결 타임아웃
                    .setSocketTimeout(10000)        // 10초 소켓 타임아웃
                    .setConnectionRequestTimeout(3000) // 3초 연결 요청 타임아웃
                    .build();
            
            HttpPost request = new HttpPost(lambdaUrl);
            request.setConfig(requestConfig);
            request.setEntity(new StringEntity(jsonPayload, StandardCharsets.UTF_8));
            request.setHeader("Content-Type", "application/json");
            
            if (async) {
                request.setHeader("X-Amz-Invocation-Type", "Event");
            } else {
                request.setHeader("X-Amz-Invocation-Type", "RequestResponse");
            }
            
            // AWS4 서명 추가
            signRequest(request, jsonPayload, functionName);
            
            // Lambda 함수 호출
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                log.info("Lambda 응답 - 상태: {}, 본문: {}", statusCode, responseBody);
                
                if (statusCode == 200 || statusCode == 202) {
                    return responseBody;
                } else {
                    throw new RuntimeException("Lambda 호출 실패 - 상태: " + statusCode + ", 응답: " + responseBody);
                }
            }
            
        } catch (Exception e) {
            log.error("Lambda 호출 중 오류 발생 - 함수: {}, 오류: {}", functionName, e.getMessage(), e);
            throw new RuntimeException("Lambda 호출 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * Lambda 함수 정보 조회 (시뮬레이션)
     */
    public Map<String, Object> getLambdaFunctionInfo(String functionName) {
        try {
            log.info("Lambda 함수 정보 조회 시뮬레이션: {}", functionName);
            
            Map<String, Object> info = new HashMap<>();
            info.put("functionName", functionName);
            info.put("runtime", "python3.9");
            info.put("handler", "lambda_function.lambda_handler");
            info.put("timeout", 30);
            info.put("memorySize", 128);
            info.put("lastModified", LocalDateTime.now().toString());
            info.put("codeSize", 1024);
            info.put("state", "Active");
            info.put("description", "테스트용 Lambda 함수");
            info.put("note", "실제 AWS API 호출이 아닌 시뮬레이션 결과입니다.");
            
            return info;
            
        } catch (Exception e) {
            log.error("Lambda 함수 정보 조회 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Lambda 함수 정보 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * Lambda 함수 목록 조회 (시뮬레이션)
     */
    public Map<String, Object> listLambdaFunctions() {
        try {
            log.info("Lambda 함수 목록 조회 시뮬레이션");
            
            Map<String, Object> function1 = new HashMap<>();
            function1.put("functionName", "test-function");
            function1.put("runtime", "python3.9");
            function1.put("handler", "lambda_function.lambda_handler");
            function1.put("timeout", 30);
            function1.put("memorySize", 128);
            function1.put("lastModified", LocalDateTime.now().toString());
            function1.put("state", "Active");
            
            Map<String, Object> function2 = new HashMap<>();
            function2.put("functionName", "chart-generator");
            function2.put("runtime", "nodejs18.x");
            function2.put("handler", "index.handler");
            function2.put("timeout", 60);
            function2.put("memorySize", 256);
            function2.put("lastModified", LocalDateTime.now().toString());
            function2.put("state", "Active");
            
            Map<String, Object> result = new HashMap<>();
            result.put("functions", java.util.List.of(function1, function2));
            result.put("totalCount", 2);
            result.put("note", "실제 AWS API 호출이 아닌 시뮬레이션 결과입니다.");
            
            return result;
            
        } catch (Exception e) {
            log.error("Lambda 함수 목록 조회 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Lambda 함수 목록 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 테스트용 Lambda 함수 호출
     */
    public String testLambdaFunction(String functionName) {
        Map<String, Object> testPayload = new HashMap<>();
        testPayload.put("test", true);
        testPayload.put("message", "Hello from Spring Boot!");
        testPayload.put("timestamp", System.currentTimeMillis());
        testPayload.put("functionName", functionName);
        
        return invokeLambdaSync(functionName, testPayload);
    }
    
    /**
     * AWS4 서명 생성 및 추가
     */
    private void signRequest(HttpPost request, String payload, String functionName) {
        try {
            String service = "lambda";
            String algorithm = "AWS4-HMAC-SHA256";
            
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
            String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            
            // 호스트 헤더
            String host = "lambda." + region + ".amazonaws.com";
            request.setHeader("Host", host);
            request.setHeader("X-Amz-Date", amzDate);
            
            // Canonical Request 생성
            String canonicalUri = "/2015-03-31/functions/" + functionName + "/invocations";
            String canonicalQuerystring = "";
            String canonicalHeaders = "host:" + host + "\n" + "x-amz-date:" + amzDate + "\n";
            String signedHeaders = "host;x-amz-date";
            String payloadHash = sha256Hex(payload);
            
            String canonicalRequest = "POST" + "\n" +
                    canonicalUri + "\n" +
                    canonicalQuerystring + "\n" +
                    canonicalHeaders + "\n" +
                    signedHeaders + "\n" +
                    payloadHash;
            
            // String to Sign 생성
            String credentialScope = dateStamp + "/" + region + "/" + service + "/" + "aws4_request";
            String stringToSign = algorithm + "\n" +
                    amzDate + "\n" +
                    credentialScope + "\n" +
                    sha256Hex(canonicalRequest);
            
            // 서명 키 생성
            byte[] signingKey = getSignatureKey(secretKey, dateStamp, region, service);
            String signature = hmacSha256Hex(signingKey, stringToSign);
            
            // Authorization 헤더 생성
            String authorizationHeader = algorithm + " " +
                    "Credential=" + accessKey + "/" + credentialScope + ", " +
                    "SignedHeaders=" + signedHeaders + ", " +
                    "Signature=" + signature;
            
            request.setHeader("Authorization", authorizationHeader);
            
        } catch (Exception e) {
            log.error("AWS4 서명 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("AWS4 서명 생성 실패", e);
        }
    }
    
    private byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) throws Exception {
        byte[] kDate = hmacSha256(("AWS4" + key).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        return hmacSha256(kService, "aws4_request");
    }
    
    private byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
    
    private String hmacSha256Hex(byte[] key, String data) throws Exception {
        byte[] hash = hmacSha256(key, data);
        return bytesToHex(hash);
    }
    
    private String sha256Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
