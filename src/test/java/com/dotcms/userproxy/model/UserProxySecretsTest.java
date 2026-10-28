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
import java.util.Optional;
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
            assertEquals(1, UserProxyEntryMapper.parseJsonToEntries(
                    new String(example.readAllBytes(), StandardCharsets.UTF_8)).size());
            String descriptor = new String(yaml.readAllBytes(), StandardCharsets.UTF_8);
            String hint = descriptor.substring(descriptor.indexOf('{'), descriptor.lastIndexOf('}') + 1);
            assertEquals(2, UserProxyEntryMapper.parseJsonToEntries(hint).size());
        }
    }
}
