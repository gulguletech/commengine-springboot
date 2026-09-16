package com.commengine.service;

import com.commengine.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verifies the access-token returned by MSG91's OTP Widget SDK on the client
 * (React Native app) after it has already sent + verified the OTP itself.
 * Ported from config/msg91.js.
 */
@Service
public class Msg91Service {

    private static final Logger log = LoggerFactory.getLogger(Msg91Service.class);
    private static final String VERIFY_URL = "https://control.msg91.com/api/v5/widget/verifyAccessToken";

    public record VerifyResult(boolean success, String phone, String error) {
        static VerifyResult ok(String phone) { return new VerifyResult(true, phone, null); }
        static VerifyResult failure(String error) { return new VerifyResult(false, null, error); }
    }

    private final AppProperties props;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Msg91Service(AppProperties props) {
        this.props = props;
    }

    public VerifyResult verifyWidgetAccessToken(String accessToken) {
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("authkey", props.getMsg91().getAuthKey());
            body.put("access-token", accessToken);

            String responseBody = restClient.post()
                    .uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(responseBody);
            String type = json.path("type").asText();
            if (!"success".equals(type)) {
                return VerifyResult.failure(json.path("message").asText("Verification failed"));
            }
            return VerifyResult.ok(normalizePhone(json.path("message").asText()));
        } catch (Exception e) {
            log.error("MSG91 verifyAccessToken failed: {}", e.getMessage());
            return VerifyResult.failure(e.getMessage());
        }
    }

    // MSG91 usually returns the phone with country code (e.g. "919876543210").
    // Keep just the last 10 digits so it matches numbers stored/looked-up elsewhere.
    private static String normalizePhone(String raw) {
        String digits = raw.replaceAll("\\D", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }
}
