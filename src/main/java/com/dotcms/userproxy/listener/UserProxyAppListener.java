package com.dotcms.userproxy.listener;

import com.dotcms.api.system.event.message.MessageSeverity;
import com.dotcms.api.system.event.message.SystemMessageEventUtil;
import com.dotcms.api.system.event.message.builder.SystemMessageBuilder;
import com.dotcms.security.apps.AppSecretSavedEvent;

import com.dotcms.system.event.local.model.EventSubscriber;
import com.dotcms.system.event.local.model.KeyFilterable;
import com.dotcms.userproxy.model.UserProxyEntry;
import com.dotcms.userproxy.model.UserProxyEntryMapper;
import com.dotcms.userproxy.util.AppKey;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.liferay.portal.model.User;
import io.vavr.control.Try;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
        final var configuration = event.getAppSecrets().getSecrets().get(AppKey.APP_CONFIG_KEY.appValue);
        if (configuration == null) {
            return;
        }
        String jsonConfig = configuration.getString();

        List<User> adminUsers = Try.of(
                        () -> APILocator.getRoleAPI().findUsersForRole(APILocator.getRoleAPI().loadCMSAdminRole()))
                .getOrElse(List.of());

        List<String> users = (event.getUserId() != null)
                ? List.of(event.getUserId())
                : adminUsers.stream().map(u -> u.getUserId()).collect(Collectors.toList());

        if (UtilMethods.isEmpty(jsonConfig)) {
            return;
        }
        List<UserProxyEntry> entries = UserProxyEntryMapper.parseJsonToEntries(jsonConfig);
        if (!entries.isEmpty()) {
            return;
        }
        final SystemMessageBuilder systemMessageBuilder = new SystemMessageBuilder();
        systemMessageBuilder.setMessage("No valid User Proxy configuration found.")
                .setLife(7 * 1000)
                .setSeverity(MessageSeverity.ERROR);

        SystemMessageEventUtil.getInstance().pushMessage(systemMessageBuilder.create(), users);
    }

    @Override
    public Comparable<String> getKey() {
        return AppKey.USER_PROXY_APP_VALUE.appValue;
    }

}
