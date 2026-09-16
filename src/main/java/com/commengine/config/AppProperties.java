package com.commengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String clientUrl = "*";
    private final Jwt jwt = new Jwt();
    private final Msg91 msg91 = new Msg91();
    private final Cloudinary cloudinary = new Cloudinary();
    private final Agora agora = new Agora();
    private final SocketIo socketio = new SocketIo();

    public String getClientUrl() { return clientUrl; }
    public void setClientUrl(String clientUrl) { this.clientUrl = clientUrl; }
    public Jwt getJwt() { return jwt; }
    public Msg91 getMsg91() { return msg91; }
    public Cloudinary getCloudinary() { return cloudinary; }
    public Agora getAgora() { return agora; }
    public SocketIo getSocketio() { return socketio; }

    public static class Jwt {
        private String secret = "change_me";
        private String expiresIn = "30d";
        private String tempExpiresIn = "15m";

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getExpiresIn() { return expiresIn; }
        public void setExpiresIn(String expiresIn) { this.expiresIn = expiresIn; }
        public String getTempExpiresIn() { return tempExpiresIn; }
        public void setTempExpiresIn(String tempExpiresIn) { this.tempExpiresIn = tempExpiresIn; }
    }

    public static class Msg91 {
        private String authKey;

        public String getAuthKey() { return authKey; }
        public void setAuthKey(String authKey) { this.authKey = authKey; }
    }

    public static class Cloudinary {
        private String cloudName;
        private String apiKey;
        private String apiSecret;

        public String getCloudName() { return cloudName; }
        public void setCloudName(String cloudName) { this.cloudName = cloudName; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiSecret() { return apiSecret; }
        public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
    }

    public static class Agora {
        private String appId;
        private String appCertificate;
        private int tokenExpirySeconds = 3600;

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getAppCertificate() { return appCertificate; }
        public void setAppCertificate(String appCertificate) { this.appCertificate = appCertificate; }
        public int getTokenExpirySeconds() { return tokenExpirySeconds; }
        public void setTokenExpirySeconds(int tokenExpirySeconds) { this.tokenExpirySeconds = tokenExpirySeconds; }
    }

    public static class SocketIo {
        private String host = "0.0.0.0";
        private int port = 5001;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }
}
