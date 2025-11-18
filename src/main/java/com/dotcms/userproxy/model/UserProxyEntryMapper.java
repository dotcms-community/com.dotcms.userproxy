package com.dotcms.userproxy.model;

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
    public static Optional<UserProxyEntry> parseJsonToEntries(final String jsonContent) {

        try {
            JSONObject json = new JSONObject(jsonContent).getJSONObject(CONFIG_KEY);

            String userToken = json.getString(USER_TOKEN_KEY);
            String methodString = json.getString(METHODS_KEY);
            List<String> urls = json.getJSONArray(URLS_KEY);

            return Optional.of(new UserProxyEntry(userToken, methodString, urls.toArray(new String[0])));
        } catch (Exception e) {
            Logger.warn(UserProxyEntryMapper.class, "unable to parse json:" + e);
            return Optional.empty();
        }
    }

    public static Optional<UserProxyEntry> buildMapForHost(String hostIdentifier) {
        try {
            Host host  = APILocator.getHostAPI().find(hostIdentifier, APILocator.getUserAPI().getSystemUser(), false);


            Optional<UserProxyEntry> entry = mapUserProxyEntry(host);
            if (entry.isPresent()) {
                return entry;
            }
            return mapUserProxyEntry(APILocator.systemHost());

        } catch (Exception e) {
            Logger.warnAndDebug(UserProxyEntryMapper.class, "error building user proxy map:" + e.getMessage(), e);
        }
        return Optional.empty();
    }

    static Optional<UserProxyEntry> mapUserProxyEntry(Host host) {
        Optional<AppSecrets> secret = Try
                .of(() -> APILocator.getAppsAPI().getSecrets(AppKey.USER_PROXY_APP_VALUE.appValue,
                        host, APILocator.systemUser()))
                .get();
        if (secret.isEmpty()) {
            return Optional.empty();
        }
        String config = secret.get().getSecrets().get(AppKey.APP_CONFIG_KEY.appValue).getString();
        return parseJsonToEntries(config);

    }
}
