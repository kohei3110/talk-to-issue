package com.github.talktoissue.util;

/**
 * Utility class for securely retrieving and validating the GITHUB_TOKEN environment variable.
 * Ensures the token is never logged or exposed in error messages.
 */
public final class TokenProvider {
    private static final String ENV_NAME = "GITHUB_TOKEN";
    private static final String GENERIC_ERROR = "GITHUB_TOKEN environment variable is not set or is empty. Please set it before running.";

    private TokenProvider() {}

    /**
     * Retrieves the GITHUB_TOKEN from environment, validates it, and returns it.
     * Never logs or exposes the token value.
     *
     * @return the GitHub token string
     * @throws IllegalStateException if the token is missing or empty
     */
    public static String getToken() {
        String token = System.getenv(ENV_NAME);
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalStateException(GENERIC_ERROR);
        }
        return token;
    }
}