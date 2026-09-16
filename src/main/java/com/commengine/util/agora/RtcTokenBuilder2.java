package com.commengine.util.agora;

public final class RtcTokenBuilder2 {

    public enum Role { PUBLISHER, SUBSCRIBER }

    private RtcTokenBuilder2() {}

    /**
     * @param tokenExpireSeconds      overall token lifetime, seconds from now
     * @param privilegeExpireSeconds  per-privilege lifetime, seconds from now (Agora recommends this equal tokenExpireSeconds)
     */
    public static String buildTokenWithUid(String appId, String appCertificate, String channelName,
                                            long uid, Role role, long tokenExpireSeconds, long privilegeExpireSeconds) {
        String uidStr = uid == 0 ? "" : Long.toString(uid);
        AccessToken2 token = new AccessToken2(appId, appCertificate, channelName, uidStr, tokenExpireSeconds);
        token.addPrivilege(AccessToken2.PRIVILEGE_JOIN_CHANNEL, privilegeExpireSeconds);
        if (role == Role.PUBLISHER) {
            token.addPrivilege(AccessToken2.PRIVILEGE_PUBLISH_AUDIO_STREAM, privilegeExpireSeconds);
            token.addPrivilege(AccessToken2.PRIVILEGE_PUBLISH_VIDEO_STREAM, privilegeExpireSeconds);
            token.addPrivilege(AccessToken2.PRIVILEGE_PUBLISH_DATA_STREAM, privilegeExpireSeconds);
        }
        return token.build();
    }
}
