package com.kellen.security;

/**
 * 认证 token 生命周期 Redis key 和 JWT claim 常量。
 *
 * <p>这里只存 token 生命周期元数据，不存用户对象，避免恢复旧 Redis token 用户模式。</p>
 */
public final class AuthTokenRedisKeys {

    public static final String CLAIM_TOKEN_TYPE = "tokenType";

    public static final String CLAIM_TOKEN_VERSION = "tokenVersion";

    public static final String CLAIM_REFRESH_TOKEN_ID = "refreshTokenId";

    public static final String ACCESS_TOKEN_TYPE = "access";

    public static final String REFRESH_TOKEN_TYPE = "refresh";

    private static final String ACCESS_TOKEN_REVOKED_PREFIX = "auth:access-token:revoked:";

    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh-token:";

    private static final String USER_TOKEN_VERSION_PREFIX = "auth:user-token-version:";

    private AuthTokenRedisKeys() {
    }

    public static String accessTokenRevoked(String tokenId) {
        return ACCESS_TOKEN_REVOKED_PREFIX + tokenId;
    }

    public static String refreshToken(String tokenId) {
        return REFRESH_TOKEN_PREFIX + tokenId;
    }

    public static String userTokenVersion(String userId) {
        return USER_TOKEN_VERSION_PREFIX + userId;
    }
}
