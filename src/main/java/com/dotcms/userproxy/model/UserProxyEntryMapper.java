package com.dotcms.userproxy.model;

import com.dotcms.security.apps.AppSecrets;
import com.dotcms.userproxy.util.AppKey;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.dotmarketing.util.json.JSONObject;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Mapper for converting app JSON configuration to UserProxyEntry objects. Handles parsing and
 * transformation to immutable UserProxyEntry instances.
 */
public class UserProxyEntryMapper {

    private static final String CONFIG_KEY = "config";
    private static final String USER_TOKEN_KEY = "userToken";
    private static final String METHODS_KEY = "methods";
    private static final String URLS_KEY = "urls";

    // Cache by resolved content, not AppSecrets identity, so Apps API updates and deletions remain authoritative.
    private static final LoadingCache<String, List<UserProxyEntry>> parsedEntries = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(UserProxyEntryMapper::parseJsonToEntries);

    private UserProxyEntryMapper() {
    }

    /**
     * Parses JSON string content into UserProxyEntry objects.
     *
     * @param jsonContent the JSON string content to parse
     * @return a list of UserProxyEntry objects
     */
    @SuppressWarnings("unchecked")
    public static List<UserProxyEntry> parseJsonToEntries(final String jsonContent) {

        List<UserProxyEntry> entries = new ArrayList<>();
        try {
            List<JSONObject> jsonArray = new JSONObject(jsonContent).getJSONArray(CONFIG_KEY);
            for (JSONObject json : jsonArray) {
                String userToken = json.getString(USER_TOKEN_KEY);
                String methodString = json.getString(METHODS_KEY);
                List<String> urls = json.getJSONArray(URLS_KEY);
                entries.add(new UserProxyEntry(userToken, methodString, urls.toArray(new String[0])));
            }
            return Collections.unmodifiableList(entries);
        } catch (Exception e) {
            Logger.warn(UserProxyEntryMapper.class, "Unable to parse User Proxy configuration; no rules loaded.");
            return List.of();
        }
    }

    public static List<UserProxyEntry> buildListForHost(String hostIdentifier) {
        try {
            Host host = APILocator.getHostAPI().find(hostIdentifier, APILocator.getUserAPI().getSystemUser(), false);

            return host == null ? List.of() : mapUserProxyEntry(host);

        } catch (Exception e) {
            Logger.warnAndDebug(UserProxyEntryMapper.class, "error building user proxy map:" + e.getMessage(), e);
        }
        return List.of();
    }

    static List<UserProxyEntry> mapUserProxyEntry(Host host) {
        Optional<AppSecrets> secrets = Try
                .of(() -> APILocator.getAppsAPI().getSecrets(AppKey.USER_PROXY_APP_VALUE.appValue,
                        host, APILocator.systemUser()))
                .get();
        if (secrets.isEmpty()) {
            return List.of();
        }

        Optional<String> parsedConfig = secretsToJson(secrets.get());
        if (parsedConfig.isEmpty()) {
            return List.of();
        }
        return parsedEntries.get(parsedConfig.get());

    }

    private static Optional<String> secretsToJson(AppSecrets secrets) {
        if (!secrets.getSecrets().containsKey(AppKey.APP_CONFIG_KEY.appValue)) {
            return Optional.empty();
        }
        String config = secrets.getSecrets().get(AppKey.APP_CONFIG_KEY.appValue).getString();
        if (UtilMethods.isEmpty(config)) {
            return Optional.empty();
        }
        Map<String, String> tokens = new HashMap<>();
        secrets.getSecrets().entrySet().stream().filter(entry -> entry.getKey().startsWith("token"))
                .forEach(entry -> {
                    tokens.put(entry.getKey(), new String(entry.getValue().getValue()));
                });
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            config = config.replace("{" + token.getKey() + "}", token.getValue());
        }

        return Optional.of(config);


    }


}
