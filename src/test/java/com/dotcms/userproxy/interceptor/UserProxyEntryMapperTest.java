package com.dotcms.userproxy.interceptor;

import org.junit.jupiter.api.Test;

import com.dotcms.userproxy.model.UserProxyEntry;
import com.dotcms.userproxy.model.UserProxyEntryMapper;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserProxyEntryMapper.
 * Tests the parsing and mapping of JSON configuration to UserProxyEntry
 * objects.
 */
class UserProxyEntryMapperTest {

    @Test
    void testParseJsonWithValidEntry() {
        String json = "{\n" +
                "    \"config\": [\n" +
                "        {\n" +
                "            \"userToken\": \"test-token-12345\",\n" +
                "            \"methods\": \"GET, POST\",\n" +
                "            \"urls\": [\n" +
                "                \"/api/v1/page/json.*\",\n" +
                "                \"/api/v1/content/_search.*\"\n" +
                "            ]\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        List<UserProxyEntry> entries = UserProxyEntryMapper.parseJsonToEntries(json);

        assertNotNull(entries, "Entries list should not be null");
        assertEquals(1, entries.size(), "Should have one entry");
        UserProxyEntry entry = entries.get(0);
        assertNotNull(entry);

        // Verify token
        char[] token = entry.getUserToken();
        assertNotNull(token);
        assertEquals("test-token-12345", new String(token));

        // Verify methods (should be lowercase and split by ", ")
        List<String> methods = entry.getMethods();
        assertNotNull(methods);
        assertEquals(2, methods.size());
        assertEquals("get", methods.get(0));
        assertEquals("post", methods.get(1));

        // Verify URLs are compiled as Pattern objects
        Pattern[] urls = entry.getUrls();
        assertNotNull(urls);
        assertEquals(2, urls.length);
        assertNotNull(urls[0]);
        assertNotNull(urls[1]);
    }

    @Test
    void testParseJsonWithMultipleMethods() {
        String json = "{\n" +
                "    \"config\": [\n" +
                "        {\n" +
                "            \"userToken\": \"token-multi\",\n" +
                "            \"methods\": \"GET, POST, PUT, DELETE\",\n" +
                "            \"urls\": [\"/api/v2/*\"]\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        List<UserProxyEntry> entries = UserProxyEntryMapper.parseJsonToEntries(json);

        assertNotNull(entries);
        assertEquals(1, entries.size());
        UserProxyEntry entry = entries.get(0);

        // Verify token
        assertEquals("token-multi", new String(entry.getUserToken()));

        // Verify methods are lowercase
        List<String> methods = entry.getMethods();
        assertEquals(4, methods.size());
        assertEquals("get", methods.get(0));
        assertEquals("post", methods.get(1));
        assertEquals("put", methods.get(2));
        assertEquals("delete", methods.get(3));

        // Verify URLs
        Pattern[] urls = entry.getUrls();
        assertEquals(1, urls.length);
    }

    @Test
    void testParseJsonWithSingleMethod() {
        String json = "{\n" +
                "    \"config\": [\n" +
                "        {\n" +
                "            \"userToken\": \"token-1\",\n" +
                "            \"methods\": \"GET\",\n" +
                "            \"urls\": [\"/api/v1/*\"]\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        List<UserProxyEntry> entries = UserProxyEntryMapper.parseJsonToEntries(json);

        assertNotNull(entries);
        assertEquals(1, entries.size());
        UserProxyEntry entry = entries.get(0);
        assertEquals("token-1", new String(entry.getUserToken()));
        assertEquals(1, entry.getMethods().size());
        assertEquals("get", entry.getMethods().get(0));
    }

    @Test
    void testParseJsonWithNoMethods() {
        String json = "{\n" +
                "    \"config\": [\n" +
                "        {\n" +
                "            \"userToken\": \"test-token\",\n" +
                "            \"methods\": \"\",\n" +
                "            \"urls\": [\"/api/*\"]\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        List<UserProxyEntry> entries = UserProxyEntryMapper.parseJsonToEntries(json);

        assertNotNull(entries);
        assertEquals(1, entries.size());
        UserProxyEntry entry = entries.get(0);
        // Empty methods string splits into single empty string
        assertEquals(1, entry.getMethods().size());
        assertEquals("", entry.getMethods().get(0));
    }

    @Test
    void testParseJsonWithNoUrls() {
        String json = "{\n" +
                "    \"config\": [\n" +
                "        {\n" +
                "            \"userToken\": \"test-token\",\n" +
                "            \"methods\": \"GET\",\n" +
                "            \"urls\": []\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        List<UserProxyEntry> entries = UserProxyEntryMapper.parseJsonToEntries(json);

        assertNotNull(entries);
        assertEquals(1, entries.size());
        UserProxyEntry entry = entries.get(0);
        assertEquals(0, entry.getUrls().length);
    }

    @Test
    void testParseJsonWithMissingUserToken() {
        String json = "{\n" +
                "    \"config\": [\n" +
                "        {\n" +
                "            \"methods\": \"GET\",\n" +
                "            \"urls\": [\"/api/*\"]\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        List<UserProxyEntry> entries = UserProxyEntryMapper.parseJsonToEntries(json);

        // Entry with missing token should return empty list
        assertEquals(0, entries.size(), "Should be empty when token is missing");
    }

    @Test
    void testParseJsonWithMissingConfig() {
        String json = "{}";

        List<UserProxyEntry> entries = UserProxyEntryMapper.parseJsonToEntries(json);

        // Missing config should return empty list
        assertEquals(0, entries.size(), "Should be empty when config is missing");
    }

    @Test
    void testParseJsonWithInvalidJson() {
        String json = "{ invalid json }";

        List<UserProxyEntry> entries = UserProxyEntryMapper.parseJsonToEntries(json);

        // Invalid JSON should return empty list
        assertEquals(0, entries.size(), "Should be empty for invalid JSON");
    }

    @Test
    void testParseJsonWithWhitespaceInMethods() {
        String json = "{\n" +
                "    \"config\": [\n" +
                "        {\n" +
                "            \"userToken\": \"test-token\",\n" +
                "            \"methods\": \"GET, POST , PUT\",\n" +
                "            \"urls\": [\"/api/*\"]\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        List<UserProxyEntry> entries = UserProxyEntryMapper.parseJsonToEntries(json);

        assertNotNull(entries);
        assertEquals(1, entries.size());
        UserProxyEntry entry = entries.get(0);
        List<String> methods = entry.getMethods();

        // Methods are split by "\\s*,\\s*" regex which trims whitespace around commas
        // So "GET, POST , PUT" becomes ["get", "post", "put"]
        assertEquals(3, methods.size());
        assertEquals("get", methods.get(0));
        assertEquals("post", methods.get(1));
        assertEquals("put", methods.get(2));
    }

    @Test
    void testUserProxyEntryTokenImmutability() {
        UserProxyEntry entry = new UserProxyEntry("test-token", "GET", new String[] { "/api/*" });

        // Get token and modify it
        char[] token1 = entry.getUserToken();
        token1[0] = 'X';

        // Get token again - should be unchanged
        char[] token2 = entry.getUserToken();
        assertEquals("test-token", new String(token2), "Token should be immutable");
    }

    @Test
    void testUrlPatternsCompile() {
        String json = "{\n" +
                "    \"config\": [\n" +
                "        {\n" +
                "            \"userToken\": \"test-token\",\n" +
                "            \"methods\": \"GET\",\n" +
                "            \"urls\": [\"/api/v1.*\", \"/admin/.*\", \"/static/.*\"]\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        List<UserProxyEntry> entries = UserProxyEntryMapper.parseJsonToEntries(json);

        assertNotNull(entries);
        assertEquals(1, entries.size());
        UserProxyEntry entry = entries.get(0);
        Pattern[] patterns = entry.getUrls();

        assertEquals(3, patterns.length);

        // Test that patterns can match URLs
        assertTrue(patterns[0].matcher("/api/v1/page").find());
        assertTrue(patterns[1].matcher("/admin/users").find());
        assertTrue(patterns[2].matcher("/static/css/style.css").find());
    }

    @Test
    void testInvalidUrlPatternHandling() {
        // Test with an invalid regex pattern
        String json = "{\n" +
                "    \"config\": [\n" +
                "        {\n" +
                "            \"userToken\": \"test-token\",\n" +
                "            \"methods\": \"GET\",\n" +
                "            \"urls\": [\"[invalid-regex\"]\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        List<UserProxyEntry> entries = UserProxyEntryMapper.parseJsonToEntries(json);

        // Should still return entry, but with invalid patterns skipped
        assertNotNull(entries);
        assertEquals(1, entries.size());
        UserProxyEntry entry = entries.get(0);
        // Invalid pattern should be skipped, resulting in 0 patterns
        assertEquals(0, entry.getUrls().length);
    }

    @Test
    void testUppercaseMethodsConvertedToLowercase() {
        String json = "{\n" +
                "    \"config\": [\n" +
                "        {\n" +
                "            \"userToken\": \"test-token\",\n" +
                "            \"methods\": \"GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS\",\n" +
                "            \"urls\": [\"/api/*\"]\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        List<UserProxyEntry> entries = UserProxyEntryMapper.parseJsonToEntries(json);

        assertNotNull(entries);
        assertEquals(1, entries.size());
        UserProxyEntry entry = entries.get(0);
        List<String> methods = entry.getMethods();

        // All methods should be lowercase
        for (String method : methods) {
            assertEquals(method, method.toLowerCase(), "Methods should be lowercase");
        }
    }

    @Test
    void testMultipleUrlPatterns() {
        String json = "{\n" +
                "    \"config\": [\n" +
                "        {\n" +
                "            \"userToken\": \"test-token\",\n" +
                "            \"methods\": \"GET\",\n" +
                "            \"urls\": [\"/api/v1/page/json.*\", \"/api/v1/content/_search.*\", \"/api/v2.*\"]\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        List<UserProxyEntry> entries = UserProxyEntryMapper.parseJsonToEntries(json);

        assertNotNull(entries);
        assertEquals(1, entries.size());
        UserProxyEntry entry = entries.get(0);
        Pattern[] patterns = entry.getUrls();

        assertEquals(3, patterns.length);
        assertTrue(patterns[0].matcher("/api/v1/page/json123").find());
        assertTrue(patterns[1].matcher("/api/v1/content/_search/result").find());
        assertTrue(patterns[2].matcher("/api/v2/something").find());
    }
}
