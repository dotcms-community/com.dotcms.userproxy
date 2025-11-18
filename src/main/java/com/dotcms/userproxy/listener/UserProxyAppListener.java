package com.dotcms.userproxy.listener;

import com.dotcms.security.apps.AppSecretSavedEvent;

import com.dotcms.system.event.local.model.EventSubscriber;
import com.dotcms.system.event.local.model.KeyFilterable;
import com.dotcms.userproxy.interceptor.UserProxyInterceptor;
import com.dotcms.userproxy.util.AppKey;
import com.dotmarketing.util.Logger;
import java.util.Objects;

public final class UserProxyAppListener implements EventSubscriber<AppSecretSavedEvent>,
        KeyFilterable {

    public UserProxyAppListener() {

    }

    /**
     * Notifies the listener of an {@link AppSecretSavedEvent}.
     *
     * <p>
     * This method is called when an {@link AppSecretSavedEvent} occurs. It performs
     * the following
     * actions:
     *
     * 
     * @param event the {@link AppSecretSavedEvent} that triggered the notification
     */
    @Override
    public void notify(final AppSecretSavedEvent event) {
        if (Objects.isNull(event)) {
            Logger.info(this, "Missing event, aborting");
            return;
        }
        Logger.info(this, "UserProxyAppListener updated, clearing UserProxy map");
        UserProxyInterceptor.resetLazyUserProxyMap();

    }

    @Override
    public Comparable<String> getKey() {
        return AppKey.USER_PROXY_APP_VALUE.appValue;
    }

}
