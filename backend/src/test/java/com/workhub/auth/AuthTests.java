package com.workhub.auth;

import static com.workhub.support.TestApi.PASSWORD;
import static com.workhub.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import com.workhub.support.RecordingEmailSender;
import com.workhub.support.TestApi;
import com.workhub.support.TestApi.Session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class AuthTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RecordingEmailSender emails;

	// ---- register ----

	@Test
	void registerStartsSessionAndSendsVerificationEmail() throws Exception {
		Session session = TestApi.register(mockMvc);

		assertThat(session.refreshCookie()).isNotNull();
		assertThat(session.refreshCookie().isHttpOnly()).isTrue();
		assertThat(session.refreshCookie().getPath()).isEqualTo("/api/auth");
		mockMvc.perform(get("/api/me").header("Authorization", session.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(session.email()))
			.andExpect(jsonPath("$.emailVerified").value(false))
			.andExpect(jsonPath("$.createdAt").isNotEmpty());
		assertThat(emails.lastToken(session.email(), "/verify-email")).isNotBlank();
	}

	@Test
	void registerNormalisesEmailAndRejectsDuplicates() throws Exception {
		String email = TestApi.uniqueEmail();
		TestApi.register(mockMvc, email);

		postJson("/api/auth/register", json("email", email.toUpperCase(), "password", PASSWORD,
				"fullName", "Again"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
	}

	@Test
	void registerValidatesInput() throws Exception {
		postJson("/api/auth/register", json("email", "not-an-email", "password", "short", "fullName", ""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors.email").exists())
			.andExpect(jsonPath("$.errors.password").exists())
			.andExpect(jsonPath("$.errors.fullName").exists());
	}

	@Test
	void registerRejectsPasswordsOverBcryptLimit() throws Exception {
		// 30 Vietnamese characters fit the 72-character limit but are 90 UTF-8 bytes.
		postJson("/api/auth/register", json("email", TestApi.uniqueEmail(), "password", "ữ".repeat(30), "fullName", "X"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
	}

	// ---- login ----

	@Test
	void loginWithCorrectPasswordIsCaseInsensitiveOnEmail() throws Exception {
		Session session = TestApi.register(mockMvc);

		postJson("/api/auth/login", json("email", session.email().toUpperCase(), "password", PASSWORD))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.user.id").value(session.userId().toString()));
	}

	@Test
	void loginFailuresLookTheSameForWrongPasswordAndUnknownEmail() throws Exception {
		Session session = TestApi.register(mockMvc);

		postJson("/api/auth/login", json("email", session.email(), "password", "wrong-password"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		postJson("/api/auth/login", json("email", TestApi.uniqueEmail(), "password", PASSWORD))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	// ---- refresh / logout ----

	@Test
	void refreshRotatesTheToken() throws Exception {
		Session session = TestApi.register(mockMvc);

		Cookie rotated = refresh(session.refreshCookie()).andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andReturn()
			.getResponse()
			.getCookie(RefreshCookies.NAME);

		assertThat(rotated.getValue()).isNotEqualTo(session.refreshCookie().getValue());
		refresh(rotated).andExpect(status().isOk());
	}

	@Test
	void reusingARotatedRefreshTokenRevokesAllSessions() throws Exception {
		Session session = TestApi.register(mockMvc);
		Cookie rotated = refresh(session.refreshCookie()).andReturn().getResponse().getCookie(RefreshCookies.NAME);

		refresh(session.refreshCookie()).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
		// The legitimate client's newer token is revoked too.
		refresh(rotated).andExpect(status().isUnauthorized());
	}

	@Test
	void refreshWithoutCookieIsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesTheRefreshTokenAndClearsTheCookie() throws Exception {
		Session session = TestApi.register(mockMvc);

		Cookie cleared = mockMvc.perform(post("/api/auth/logout").cookie(session.refreshCookie()))
			.andExpect(status().isNoContent())
			.andReturn()
			.getResponse()
			.getCookie(RefreshCookies.NAME);

		assertThat(cleared.getMaxAge()).isZero();
		refresh(session.refreshCookie()).andExpect(status().isUnauthorized());
	}

	// ---- email verification ----

	@Test
	void verifyEmailMarksTheAccountVerifiedOnce() throws Exception {
		Session session = TestApi.register(mockMvc);
		String token = emails.lastToken(session.email(), "/verify-email");

		postJson("/api/auth/verify-email", json("token", token)).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/me").header("Authorization", session.bearer()))
			.andExpect(jsonPath("$.emailVerified").value(true));
		postJson("/api/auth/verify-email", json("token", token)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void resendingVerificationInvalidatesTheOlderLink() throws Exception {
		Session session = TestApi.register(mockMvc);
		String first = emails.lastToken(session.email(), "/verify-email");

		mockMvc.perform(post("/api/me/email-verification").header("Authorization", session.bearer()))
			.andExpect(status().isNoContent());
		String second = emails.lastToken(session.email(), "/verify-email");

		postJson("/api/auth/verify-email", json("token", first)).andExpect(status().isBadRequest());
		postJson("/api/auth/verify-email", json("token", second)).andExpect(status().isNoContent());
	}

	// ---- password reset ----

	@Test
	void forgotPasswordDoesNotRevealUnknownEmails() throws Exception {
		String unknown = TestApi.uniqueEmail();
		postJson("/api/auth/forgot-password", json("email", unknown)).andExpect(status().isNoContent());
		assertThat(emails.countTo(unknown)).isZero();
	}

	@Test
	void resetPasswordReplacesPasswordAndSignsOutEverywhere() throws Exception {
		Session session = TestApi.register(mockMvc);
		postJson("/api/auth/forgot-password", json("email", session.email())).andExpect(status().isNoContent());
		String token = emails.lastToken(session.email(), "/reset-password");

		postJson("/api/auth/reset-password", json("token", token, "newPassword", "brand-new-password"))
			.andExpect(status().isNoContent());

		postJson("/api/auth/login", json("email", session.email(), "password", PASSWORD))
			.andExpect(status().isUnauthorized());
		postJson("/api/auth/login", json("email", session.email(), "password", "brand-new-password"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.emailVerified").value(true));
		refresh(session.refreshCookie()).andExpect(status().isUnauthorized());
		postJson("/api/auth/reset-password", json("token", token, "newPassword", "another-password"))
			.andExpect(status().isBadRequest());
	}

	// ---- profile & change password ----

	@Test
	void profileCanBeUpdated() throws Exception {
		Session session = TestApi.register(mockMvc);

		mockMvc.perform(put("/api/me").header("Authorization", session.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("fullName", "  Hồ Đăng Quỳnh ")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fullName").value("Hồ Đăng Quỳnh"));
	}

	@Test
	void avatarCanBeUploadedServedAndRemoved() throws Exception {
		Session session = TestApi.register(mockMvc);
		byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

		String url = com.jayway.jsonpath.JsonPath.read(mockMvc
			.perform(multipart(HttpMethod.PUT, "/api/me/avatar")
				.file(new MockMultipartFile("file", "a.png", "image/png", png))
				.header("Authorization", session.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.startsWith("/api/public/users/")))
			.andReturn().getResponse().getContentAsString(), "$.avatarUrl");

		// Public: an <img> tag cannot send the bearer token.
		mockMvc.perform(get(url.substring(0, url.indexOf('?'))))
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/png"))
			.andExpect(content().bytes(png));

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/me/avatar")
			.file(new MockMultipartFile("file", "a.png", "image/png", "not an image".getBytes()))
			.header("Authorization", session.bearer()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AVATAR_INVALID_TYPE"));

		mockMvc.perform(delete("/api/me/avatar").header("Authorization", session.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.avatarUrl").doesNotExist());
		mockMvc.perform(get(url.substring(0, url.indexOf('?')))).andExpect(status().isNotFound());
	}

	@Test
	void changePasswordRequiresCurrentPasswordAndRotatesSessions() throws Exception {
		Session session = TestApi.register(mockMvc);

		mockMvc.perform(post("/api/me/password").header("Authorization", session.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("currentPassword", "wrong-password", "newPassword", "brand-new-password")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

		MvcResult changed = mockMvc.perform(post("/api/me/password").header("Authorization", session.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("currentPassword", PASSWORD, "newPassword", "brand-new-password")))
			.andExpect(status().isOk())
			.andReturn();

		// Another device still holding the old token is rejected without killing the new session.
		refresh(session.refreshCookie()).andExpect(status().isUnauthorized());
		refresh(changed.getResponse().getCookie(RefreshCookies.NAME)).andExpect(status().isOk());
		postJson("/api/auth/login", json("email", session.email(), "password", "brand-new-password"))
			.andExpect(status().isOk());
	}

	private ResultActions postJson(String path, String body) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions refresh(Cookie cookie) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh").cookie(cookie));
	}

}
