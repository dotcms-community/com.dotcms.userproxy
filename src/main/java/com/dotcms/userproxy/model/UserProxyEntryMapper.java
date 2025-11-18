package com.dotcms.userproxy.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dotcms.security.apps.AppSecrets;
import com.dotcms.userproxy.util.AppKey;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.json.JSONObject;

import io.vavr.control.Try;

/**
 * Mapper for converting JSON configuration entries to UserProxyEntry objects.
 * Handles parsing of userproxy.json and transformation to immutable
 * UserProxyEntry instances.
 */
public class UserProxyEntryMapper {

    private static final String CONFIG_KEY = "config";
    private static final String USER_TOKEN_KEY = "userToken";
    private static final String METHODS_KEY = "methods";
    private static final String URLS_KEY = "urls";

    UserProxyEntryMapper() {
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
            Logger.warn(UserProxyEntryMapper.class, "unable to parse json:" + e);
            return List.of();
        }
    }

    public static List<UserProxyEntry> buildListForHost(String hostIdentifier) {
        try {
            Host host = APILocator.getHostAPI().find(hostIdentifier, APILocator.getUserAPI().getSystemUser(), false);

            List<UserProxyEntry> entries = mapUserProxyEntry(host);
            if (!entries.isEmpty()) {
                return entries;
            }

            return mapUserProxyEntry(APILocator.systemHost());

        } catch (Exception e) {
            Logger.warnAndDebug(UserProxyEntryMapper.class, "error building user proxy map:" + e.getMessage(), e);
        }
        return List.of();
    }

    static List<UserProxyEntry> mapUserProxyEntry(Host host) {
        Optional<AppSecrets> secret = Try
                .of(() -> APILocator.getAppsAPI().getSecrets(AppKey.USER_PROXY_APP_VALUE.appValue,
                        host, APILocator.systemUser()))
                .get();
        if (secret.isEmpty()) {
            return List.of();
        }
        String config = secret.get().getSecrets().get(AppKey.APP_CONFIG_KEY.appValue).getString();
        return parseJsonToEntries(config);

    }
}
