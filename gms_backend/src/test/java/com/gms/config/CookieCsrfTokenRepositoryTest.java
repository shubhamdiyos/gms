package com.gms.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CookieCsrfTokenRepository - Double Submit Cookie Pattern")
class CookieCsrfTokenRepositoryTest {

    private CookieCsrfTokenRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CookieCsrfTokenRepository();
    }

    @Test
    @DisplayName("generateToken should create a valid CSRF token with correct header and parameter names")
    void generateToken_shouldCreateValidToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        CsrfToken token = repository.generateToken(request);

        assertThat(token).isNotNull();
        assertThat(token.getHeaderName()).isEqualTo("X-XSRF-TOKEN");
        assertThat(token.getParameterName()).isEqualTo("_csrf");
        assertThat(token.getToken()).isNotBlank();
    }

    @Test
    @DisplayName("generateToken should create unique tokens on each call")
    void generateToken_shouldCreateUniqueTokens() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        CsrfToken token1 = repository.generateToken(request);
        CsrfToken token2 = repository.generateToken(request);

        assertThat(token1.getToken()).isNotEqualTo(token2.getToken());
    }

    @Test
    @DisplayName("saveToken should set cookie with correct attributes when token is not null")
    void saveToken_withToken_shouldSetCookieWithCorrectAttributes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(request);

        repository.saveToken(token, request, response);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(1);

        String cookieHeader = cookies.get(0);
        assertThat(cookieHeader).contains("XSRF-TOKEN=" + token.getToken());
        assertThat(cookieHeader).contains("Path=/");
        assertThat(cookieHeader).contains("Secure");
        assertThat(cookieHeader).contains("SameSite=Lax");
        assertThat(cookieHeader).doesNotContain("HttpOnly");
    }

    @Test
    @DisplayName("saveToken with a null token must leave the caller's cookie untouched")
    void saveToken_withNullToken_shouldLeaveCookieInPlace() {
        // The null branch is reached by Spring's CsrfAuthenticationStrategy on every authenticated
        // request under the STATELESS chain, which cannot tell "already signed in" from "signed in
        // now". Clearing here erased the double-submit cookie the SPA was still sending its header
        // from, so its first write after a read was answered 403. See the repository's javadoc.
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(null, request, response);

        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    @DisplayName("saveToken should not create duplicate Set-Cookie headers")
    void saveToken_shouldNotCreateDuplicateHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(request);

        repository.saveToken(token, request, response);
        repository.saveToken(token, request, response);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(2);
    }

    @Test
    @DisplayName("loadToken should return null when no cookies present")
    void loadToken_withNoCookies_shouldReturnNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        CsrfToken token = repository.loadToken(request);

        assertThat(token).isNull();
    }

    @Test
    @DisplayName("loadToken should return null when XSRF-TOKEN cookie is not present")
    void loadToken_withoutCsrfCookie_shouldReturnNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other_cookie", "value"));

        CsrfToken token = repository.loadToken(request);

        assertThat(token).isNull();
    }

    @Test
    @DisplayName("loadToken should return token when XSRF-TOKEN cookie is present")
    void loadToken_withCsrfCookie_shouldReturnToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String tokenValue = "test-csrf-token-12345";
        request.setCookies(new Cookie("XSRF-TOKEN", tokenValue));

        CsrfToken token = repository.loadToken(request);

        assertThat(token).isNotNull();
        assertThat(token.getToken()).isEqualTo(tokenValue);
        assertThat(token.getHeaderName()).isEqualTo("X-XSRF-TOKEN");
        assertThat(token.getParameterName()).isEqualTo("_csrf");
    }

    @Test
    @DisplayName("loadToken should return null when XSRF-TOKEN cookie is empty")
    void loadToken_withEmptyCsrfCookie_shouldReturnNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("XSRF-TOKEN", ""));

        CsrfToken token = repository.loadToken(request);

        assertThat(token).isNull();
    }

    @Test
    @DisplayName("loadToken should handle multiple cookies and find XSRF-TOKEN")
    void loadToken_withMultipleCookies_shouldFindCsrfToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String tokenValue = "test-csrf-token";
        request.setCookies(
            new Cookie("other_cookie", "value"),
            new Cookie("XSRF-TOKEN", tokenValue),
            new Cookie("session", "abc123")
        );

        CsrfToken token = repository.loadToken(request);

        assertThat(token).isNotNull();
        assertThat(token.getToken()).isEqualTo(tokenValue);
    }

    @Test
    @DisplayName("Token round-trip: save then load should preserve token value")
    void tokenRoundTrip_shouldPreserveValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        CsrfToken originalToken = repository.generateToken(request);
        repository.saveToken(originalToken, request, response);

        String cookieHeader = response.getHeader("Set-Cookie");
        assertThat(cookieHeader).isNotNull();

        String tokenValue = originalToken.getToken();
        request.setCookies(new Cookie("XSRF-TOKEN", tokenValue));

        CsrfToken loadedToken = repository.loadToken(request);

        assertThat(loadedToken).isNotNull();
        assertThat(loadedToken.getToken()).isEqualTo(originalToken.getToken());
    }
}
