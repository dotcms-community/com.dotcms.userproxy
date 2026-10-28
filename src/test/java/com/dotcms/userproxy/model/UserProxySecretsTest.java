package com.dotcms.userproxy.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dotcms.security.apps.AppSecrets;
import com.dotcms.security.apps.AppsAPI;
import com.dotcms.userproxy.util.AppKey;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.util.Logger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class UserProxySecretsTest {

    @Test
    void missingConfigurationDoesNotThrowOrLoadRules() throws Exception {
        Host host = mock(Host.class);
        AppsAPI apps = mock(AppsAPI.class);
        try (var apis = mockStatic(APILocator.class)) {
            apis.when(APILocator::getAppsAPI).thenReturn(apps);
            when(apps.getSecrets(eq(AppKey.USER_PROXY_APP_VALUE.appValue), eq(host), any()))
                    .thenReturn(Optional.of(AppSecrets.empty()));
            assertTrue(UserProxyEntryMapper.mapUserProxyEntry(host).isEmpty());
        }
    }

    @Test
    void expandsTokenReferencesBeforeParsing() throws Exception {
        Host host = mock(Host.class);
        AppsAPI apps = mock(AppsAPI.class);
        AppSecrets secrets = AppSecrets.builder()
                .withKey(AppKey.USER_PROXY_APP_VALUE.appValue)
                .withSecret("configuration", """
                        {"config":[{"userToken":"{token1}","methods":"GET","urls":["/api"]}]}
                        """)
                .withHiddenSecret("token1", "test-token")
                .build();
        try (var apis = mockStatic(APILocator.class)) {
            apis.when(APILocator::getAppsAPI).thenReturn(apps);
            when(apps.getSecrets(eq(AppKey.USER_PROXY_APP_VALUE.appValue), eq(host), any()))
                    .thenReturn(Optional.of(secrets));
            var entries = UserProxyEntryMapper.mapUserProxyEntry(host);
            assertEquals(1, entries.size());
            assertEquals("test-token", new String(entries.get(0).getUserToken()));
        }
    }

    @Test
    void reusesParsedRulesButObservesChangedTokensPathsAndDeletion() throws Exception {
        Host host = mock(Host.class);
        AppsAPI apps = mock(AppsAPI.class);
        String config = """
                {"config":[{"userToken":"{token1}","methods":"GET","urls":["/cached-api"]}]}
                """;
        AppSecrets original = AppSecrets.builder()
                .withSecret("configuration", config).withHiddenSecret("token1", "original-token").build();
        AppSecrets sameContent = AppSecrets.builder()
                .withSecret("configuration", config).withHiddenSecret("token1", "original-token").build();
        AppSecrets changedToken = AppSecrets.builder()
                .withSecret("configuration", config).withHiddenSecret("token1", "changed-token").build();
        AppSecrets changedPath = AppSecrets.builder()
                .withSecret("configuration", config.replace("/cached-api", "/changed-api"))
                .withHiddenSecret("token1", "changed-token").build();
        try (var apis = mockStatic(APILocator.class)) {
            apis.when(APILocator::getAppsAPI).thenReturn(apps);
            when(apps.getSecrets(eq(AppKey.USER_PROXY_APP_VALUE.appValue), eq(host), any()))
                    .thenReturn(Optional.of(original))
                    .thenReturn(Optional.of(sameContent))
                    .thenReturn(Optional.of(changedToken))
                    .thenReturn(Optional.of(changedPath))
                    .thenReturn(Optional.empty());
            var first = UserProxyEntryMapper.mapUserProxyEntry(host);
            assertSame(first, UserProxyEntryMapper.mapUserProxyEntry(host),
                    "Fresh AppSecrets instances with identical content must reuse compiled rules");
            var updatedToken = UserProxyEntryMapper.mapUserProxyEntry(host);
            assertNotSame(first, updatedToken);
            assertEquals("changed-token", new String(updatedToken.get(0).getUserToken()));
            var updatedPath = UserProxyEntryMapper.mapUserProxyEntry(host);
            assertEquals("/changed-api", updatedPath.get(0).getUrls()[0].pattern());
            assertTrue(UserProxyEntryMapper.mapUserProxyEntry(host).isEmpty());
        }
    }

    @Test
    void malformedConfigurationDoesNotLogJsonOrTokens() {
        try (var logger = mockStatic(Logger.class)) {
            assertTrue(UserProxyEntryMapper.parseJsonToEntries(
                    "{sensitive-token-this-is-not-json").isEmpty());
            logger.verify(() -> Logger.warn(UserProxyEntryMapper.class,
                    "Unable to parse User Proxy configuration; no rules loaded."));
            logger.verifyNoMoreInteractions();
        }
    }

    @Test
    void bundledExampleAndAppHintContainValidConfiguration() throws IOException {
        try (var example = getClass().getResourceAsStream("/example-userproxy.json");
             var yaml = getClass().getResourceAsStream("/Dotuserproxy.yml")) {
            assertNotNull(example);
            assertNotNull(yaml);
            List<UserProxyEntry> exampleEntries = UserProxyEntryMapper.parseJsonToEntries(
                    new String(example.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(1, exampleEntries.size());
            String descriptor = new String(yaml.readAllBytes(), StandardCharsets.UTF_8);
            String hint = descriptor.substring(descriptor.indexOf('{'), descriptor.lastIndexOf('}') + 1);
            List<UserProxyEntry> hintEntries = UserProxyEntryMapper.parseJsonToEntries(hint);
            assertEquals(2, hintEntries.size());

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getMethod()).thenReturn("GET");
            for (UserProxyEntry entry : List.of(exampleEntries.get(0), hintEntries.get(0))) {
                when(request.getRequestURI()).thenReturn("/api/v1/page/json");
                assertTrue(entry.matches(request), "The page endpoint must match without a trailing slash");
                when(request.getRequestURI()).thenReturn("/api/v1/page/json/about-us");
                assertTrue(entry.matches(request));
                when(request.getRequestURI()).thenReturn("/api/v1/page/json-admin");
                assertFalse(entry.matches(request));
            }
        }
    }
}
