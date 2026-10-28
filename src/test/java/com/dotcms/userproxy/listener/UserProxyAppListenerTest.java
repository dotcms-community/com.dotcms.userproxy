package com.dotcms.userproxy.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import com.dotcms.security.apps.AppSecretSavedEvent;
import com.dotcms.security.apps.AppSecrets;
import com.dotmarketing.business.APILocator;
import org.junit.jupiter.api.Test;

class UserProxyAppListenerTest {

    @Test
    void ignoresEventsWithoutConfiguration() {
        AppSecretSavedEvent event = mock(AppSecretSavedEvent.class);
        when(event.getAppSecrets()).thenReturn(AppSecrets.empty());
        try (var apis = mockStatic(APILocator.class)) {
            UserProxyAppListener listener = new UserProxyAppListener();
            assertDoesNotThrow(() -> listener.notify(event));
            assertDoesNotThrow(() -> listener.notify(null));
            apis.verifyNoInteractions();
        }
    }
}
