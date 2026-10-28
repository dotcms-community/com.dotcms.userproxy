package com.dotcms.userproxy.osgi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.dotcms.security.apps.AppsCache;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.util.ConfigUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileMoverUtilTest {

    @TempDir
    Path assets;

    @Test
    void installsReplacesAndRemovesTheDescriptorAndInvalidatesAppsCache() throws IOException {
        AppsCache cache = mock(AppsCache.class);
        try (var config = mockStatic(ConfigUtils.class); var caches = mockStatic(CacheLocator.class)) {
            config.when(ConfigUtils::getAssetPath).thenReturn(assets.toString());
            caches.when(CacheLocator::getAppsCache).thenReturn(cache);
            FileMoverUtil mover = new FileMoverUtil();
            Path descriptor = assets.resolve("server/apps/Dotuserproxy.yml");

            mover.copyAppYml();
            assertTrue(Files.readString(descriptor).contains("User Proxy / Anonymous Access"));
            Files.writeString(descriptor, "old descriptor");
            mover.copyAppYml();
            assertTrue(Files.readString(descriptor).contains("User Proxy / Anonymous Access"));

            mover.deleteYml();
            assertFalse(Files.exists(descriptor));
            mover.deleteYml();
            verify(cache, times(4)).clearCache();
        }
    }
}
