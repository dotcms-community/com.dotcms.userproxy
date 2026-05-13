package com.dotcms.userproxy.interceptor;

import com.dotcms.auth.providers.jwt.beans.JWToken;
import com.dotcms.business.SystemCache;
import com.dotcms.filters.interceptor.Result;
import com.dotcms.filters.interceptor.WebInterceptor;
import com.dotcms.userproxy.model.UserProxyEntry;
import com.dotcms.userproxy.model.UserProxyEntryMapper;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.business.web.WebAPILocator;
import com.liferay.portal.model.User;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portal.util.WebKeys;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Web interceptor for user proxy authentication and authorization.
 * Loads user proxy configurations from userproxy.json and validates requests
 * against configured user tokens, HTTP methods, and URL patterns.
 *
 * <p>Per-host entry lists are cached in the clustered {@link SystemCache} so that
 * invalidation propagates across nodes. Global resets bump a version counter,
 * causing each node to miss-and-rebuild on next access; stale versioned keys
 * age out naturally.
 */
public class UserProxyInterceptor implements WebInterceptor {

    private static final SystemCache cache = CacheLocator.getSystemCache();
    private static final String KEY_PREFIX = "com.dotcms.userproxy.entries";
    private static final String VERSION_KEY = "com.dotcms.userproxy.version";

    public UserProxyInterceptor() {
    }

    /**
     * Invalidates every cached host entry list across the cluster by bumping the
     * version counter. Old keys remain in cache until they age out, but are
     * unreachable.
     */
    public static void resetLazyUserProxyMap() {
        final Long current = (Long) cache.get(VERSION_KEY);
        cache.put(VERSION_KEY, (current == null ? 0L : current) + 1L);
    }

    @Override
    public String[] getFilters() {
        return new String[] { "/*" };
    }

    @Override
    public Result intercept(final HttpServletRequest request, final HttpServletResponse response) {

        if (hasExistingAuth(request)) {
            return Result.NEXT;
        }

        final Host host = WebAPILocator.getHostWebAPI().getCurrentHostNoThrow(request);
        if (host == null) {
            return Result.NEXT;
        }

        final List<UserProxyEntry> entries = entriesForHost(host.getIdentifier());
        if (entries.isEmpty()) {
            return Result.NEXT;
        }

        for (final UserProxyEntry entry : entries) {
            if (!entry.matches(request)) {
                continue;
            }
            final User user = APILocator.getApiTokenAPI()
                    .fromJwt(new String(entry.getUserToken()), request.getRemoteAddr())
                    .flatMap(JWToken::getActiveUser)
                    .orElse(null);
            if (user != null) {
                request.setAttribute(WebKeys.USER, user);
                request.setAttribute(WebKeys.USER_ID, user.getUserId());
                return Result.NEXT;
            }
        }

        return Result.NEXT;
    }

    public boolean hasExistingAuth(final HttpServletRequest request) {
        final User user = PortalUtil.getUser(request);
        if (user != null && !user.isAnonymousUser()) {
            return true;
        }
        return request.getHeader("Authorization") != null;
    }

    @SuppressWarnings("unchecked")
    private static List<UserProxyEntry> entriesForHost(final String hostId) {
        final String key = buildKey(hostId);
        List<UserProxyEntry> entries = (List<UserProxyEntry>) cache.get(key);
        if (entries == null) {
            entries = List.copyOf(UserProxyEntryMapper.buildListForHost(hostId));
            cache.put(key, entries);
        }
        return entries;
    }

    private static String buildKey(final String hostId) {
        final Long version = (Long) cache.get(VERSION_KEY);
        return KEY_PREFIX + ":v" + (version == null ? 0L : version) + ":" + hostId;
    }

}
