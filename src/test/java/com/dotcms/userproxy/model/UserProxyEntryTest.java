package com.dotcms.userproxy.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    void supportsExplicitSubpathsButDoesNotMatchQueryStrings() {
        UserProxyEntry entry = new UserProxyEntry("token", "GET", new String[]{"/pages/protected/.*"});
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/pages/protected/article");
        assertTrue(entry.matches(request));

        UserProxyEntry queryRule = new UserProxyEntry("token", "GET", new String[]{"/pages/protected/article\\?public=true"});
        assertFalse(queryRule.matches(request));
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
