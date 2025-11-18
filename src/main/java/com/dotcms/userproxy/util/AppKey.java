
package com.dotcms.userproxy.util;

import com.dotmarketing.exception.DotRuntimeException;

public enum AppKey {
    USER_PROXY_APP_VALUE("Dotuserproxy"),
    APP_CONFIG_KEY("configuration");

    public final String appValue;

    AppKey(String appValue) {
        this.appValue = appValue;
    }

    public static AppKey fromString(String appValue) {
        for (AppKey appKey : AppKey.values()) {
            if (appKey.appValue.equalsIgnoreCase(appValue)) {
                return appKey;
            }
            if (appKey.name().equalsIgnoreCase(appValue)) {
                return appKey;
            }
        }
        throw new DotRuntimeException("Unknown app key: " + appValue);
    }

}
