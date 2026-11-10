package com.workhub.auth;

import com.workhub.user.UserProfile;

/** Body returned whenever a session starts or is refreshed. The refresh token travels in a cookie. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserProfile user) {
}
