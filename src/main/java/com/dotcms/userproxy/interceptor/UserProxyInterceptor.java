package com.dotcms.userproxy.interceptor;

import com.dotcms.auth.providers.jwt.beans.JWToken;
import com.dotcms.filters.interceptor.Result;
import com.dotcms.filters.interceptor.WebInterceptor;
import com.dotcms.userproxy.model.UserProxyEntry;
import com.dotcms.userproxy.model.UserProxyEntryMapper;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.web.WebAPILocator;
import com.liferay.portal.model.User;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portal.util.WebKeys;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Web interceptor for user proxy authentication and authorization. Loads configuration from the User Proxy app
 * and validates requests against configured user tokens, HTTP methods, and URL patterns.
 * Configuration uses the Apps API cache, whose invalidations propagate across nodes.
 */
public class UserProxyInterceptor implements WebInterceptor {

    @Override
    public String[] getFilters() {
        return new String[]{"/*"};
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

        interceptForHost(request, host).ifPresent(user -> {
            request.setAttribute(WebKeys.USER, user);
            request.setAttribute(WebKeys.USER_ID, user.getUserId());
        });
        return Result.NEXT;
    }

    private Optional<User> interceptForHost(final HttpServletRequest request, final Host host) {

        final List<UserProxyEntry> entries = UserProxyEntryMapper.buildListForHost(host.getIdentifier());

        for (final UserProxyEntry entry : entries) {
            if (!entry.matches(request)) {
                continue;
            }
            final User user = APILocator.getApiTokenAPI()
                    .fromJwt(new String(entry.getUserToken()), request.getRemoteAddr())
                    .flatMap(JWToken::getActiveUser)
                    .orElse(null);
            if (user != null) {
                return Optional.of(user);
            }
        }
        // Try System Host rules only after all site rules fail.
        if (!host.isSystemHost()) {
            return interceptForHost(request, APILocator.systemHost());
        }

        return Optional.empty();
    }

    public boolean hasExistingAuth(final HttpServletRequest request) {
        final User user = PortalUtil.getUser(request);
        if (user != null && !user.isAnonymousUser()) {
            return true;
        }
        return request.getHeader("Authorization") != null;
    }

}
