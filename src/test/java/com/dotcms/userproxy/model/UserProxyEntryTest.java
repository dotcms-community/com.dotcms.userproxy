package com.dotcms.userproxy.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.dotmarketing.util.Logger;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class UserProxyEntryTest {

    @Test
    void matchesOnlyTheWholePathAndAllowedMethod() {
        UserProxyEntry entry = new UserProxyEntry("token", " GET, POST ", new String[]{"/api/v1/graphql"});
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/graphql");
        assertTrue(entry.matches(request));

        when(request.getRequestURI()).thenReturn("/api/v1/graphql-admin");
        assertFalse(entry.matches(request));
        when(request.getRequestURI()).thenReturn("/other/api/v1/graphql");
        assertFalse(entry.matches(request));
        when(request.getRequestURI()).thenReturn("/api/v1/graphql");
        when(request.getMethod()).thenReturn("DELETE");
        assertFalse(entry.matches(request));
    }

    @Test
    void supportsExplicitSubpaths() {
        UserProxyEntry entry = new UserProxyEntry("token", "GET", new String[]{"/pages/protected/.*"});
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/pages/protected/article");
        assertTrue(entry.matches(request));
    }

    @Test
    void matchesTheWholePathAndRawQueryString() {
        UserProxyEntry entry = new UserProxyEntry("token", "GET",
                new String[]{"/pages/protected/article\\?public=true&name=hello%20world"});
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/pages/protected/article");
        assertFalse(entry.matches(request));

        when(request.getQueryString()).thenReturn("public=true&name=hello%20world");
        assertTrue(entry.matches(request));
        UserProxyEntry pathOnly = new UserProxyEntry("token", "GET",
                new String[]{"/pages/protected/article"});
        assertFalse(pathOnly.matches(request));

        when(request.getQueryString()).thenReturn("public=false&name=hello%20world");
        assertFalse(entry.matches(request));
        when(request.getQueryString()).thenReturn("public=true&name=hello%20world&extra=1");
        assertFalse(entry.matches(request));
    }

    @Test
    void preservesAnEmptyQueryString() {
        UserProxyEntry entry = new UserProxyEntry("token", "GET", new String[]{"/api\\?"});
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api");
        assertFalse(entry.matches(request));
        when(request.getQueryString()).thenReturn("");
        assertTrue(entry.matches(request));
    }

    @Test
    void boundsTheCombinedPathAndQueryLength() {
        UserProxyEntry entry = new UserProxyEntry("token", "GET", new String[]{".*"});
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/" + "a".repeat(2047));
        assertTrue(entry.matches(request));

        when(request.getRequestURI()).thenReturn("/" + "a".repeat(2048));
        assertFalse(entry.matches(request));

        when(request.getRequestURI()).thenReturn("/api");
        when(request.getQueryString()).thenReturn("a".repeat(2043));
        assertTrue(entry.matches(request));
        when(request.getQueryString()).thenReturn("a".repeat(2044));
        assertFalse(entry.matches(request));
    }

    @Test
    void timesOutBacktrackingWithoutTryingPermissiveFallbackOrLoggingTheUrl() {
        String pattern = "/(a+){10}$";
        UserProxyEntry entry = new UserProxyEntry("token", "GET", new String[]{pattern, ".*"});
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/" + "a".repeat(128) + "!");

        try (var logger = mockStatic(Logger.class)) {
            assertTimeout(Duration.ofSeconds(2), () -> assertFalse(entry.matches(request)));
            logger.verify(() -> Logger.warn(UserProxyEntry.class,
                    "User Proxy regex matching timed out for pattern: " + pattern));
            logger.verifyNoMoreInteractions();
        }
    }

    @Test
    void methodMatchingIsIndependentOfServerLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            UserProxyEntry entry = new UserProxyEntry("token", "LINK", new String[]{"/api"});
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getMethod()).thenReturn("link");
            when(request.getRequestURI()).thenReturn("/api");
            assertTrue(entry.matches(request));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void callersCannotChangeMethodsOrPatterns() {
        UserProxyEntry entry = new UserProxyEntry("token", "GET", new String[]{"/api"});
        assertThrows(UnsupportedOperationException.class, () -> entry.getMethods().set(0, "post"));
        entry.getUrls()[0] = Pattern.compile(".*");
        assertEquals("/api", entry.getUrls()[0].pattern());
    }
}
