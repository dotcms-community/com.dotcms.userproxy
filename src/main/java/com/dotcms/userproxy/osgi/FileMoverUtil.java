package com.dotcms.userproxy.osgi;

import com.dotcms.userproxy.util.AppKey;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.util.ConfigUtils;
import com.dotmarketing.util.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileMoverUtil {

    private final Path installedAppYaml = Path.of(ConfigUtils.getAssetPath(), "server", "apps",
            AppKey.USER_PROXY_APP_VALUE.appValue + ".yml");

    public void copyAppYml() throws IOException {
        Logger.info(this.getClass().getName(), "Copying YAML file: " + installedAppYaml);
        try (InputStream in = this.getClass()
                .getResourceAsStream("/" + AppKey.USER_PROXY_APP_VALUE.appValue + ".yml")) {
            if (in == null) {
                throw new IOException("User Proxy app descriptor is missing from the bundle");
            }
            Files.createDirectories(installedAppYaml.getParent());
            Files.copy(in, installedAppYaml, StandardCopyOption.REPLACE_EXISTING);
        }
        CacheLocator.getAppsCache().clearCache();
    }

    public void deleteYml() throws IOException {
        Logger.info(this.getClass().getName(), "Deleting YAML file: " + installedAppYaml);
        Files.deleteIfExists(installedAppYaml);
        CacheLocator.getAppsCache().clearCache();
    }
}
