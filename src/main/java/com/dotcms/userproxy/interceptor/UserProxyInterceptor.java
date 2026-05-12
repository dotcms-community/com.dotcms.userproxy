package com.dotcms.userproxy.interceptor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.dotcms.auth.providers.jwt.beans.JWToken;

import com.dotcms.filters.interceptor.Result;
import com.dotcms.filters.interceptor.WebInterceptor;
import com.dotcms.userproxy.model.UserProxyEntry;
import com.dotcms.userproxy.model.UserProxyEntryMapper;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.web.WebAPILocator;
import com.dotmarketing.util.Logger;
import com.liferay.portal.model.User;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portal.util.WebKeys;

import io.vavr.control.Try;

/**
 * Web interceptor for user proxy authentication and authorization.
 * Loads user proxy configurations from userproxy.json and validates requests
 * against configured user tokens, HTTP methods, and URL patterns.
 */
public class UserProxyInterceptor implements WebInterceptor {

    public UserProxyInterceptor() {
    }

    @Override
    public String[] getFilters() {
        return new String[] { "/*" };
    }

    @Override
    public Result intercept(final HttpServletRequest request, final HttpServletResponse response) {

        if (hasExistingAuth(request)) {
            Logger.debug(this, () -> "UserProxy skip: request already has auth. method="
                    + request.getMethod() + " uri=" + request.getRequestURI());
            return Result.NEXT;
        }

        Host host = WebAPILocator.getHostWebAPI().getCurrentHostNoThrow(request);
        if (host == null) {
            Logger.warn(this, "UserProxy could not resolve current host. method="
                    + request.getMethod() + " uri=" + request.getRequestURI());
            return Result.NEXT;
        }

        List<UserProxyEntry> entries = UserProxyEntryMapper.buildListForHost(host.getIdentifier());

        Logger.info(this, "UserProxy intercept: method=" + request.getMethod()
                + " uri=" + request.getRequestURI()
                + " remoteAddr=" + request.getRemoteAddr()
                + " hostId=" + host.getIdentifier()
                + " entries=" + entries.size());

        if (entries.isEmpty()) {
            Logger.warn(this, "UserProxy found no entries for hostId=" + host.getIdentifier()
                    + " uri=" + request.getRequestURI());
            return Result.NEXT;
        }

        // break on first match
        for (UserProxyEntry entry : entries) {
            if (entry.matches(request)) {
                Logger.info(this, "UserProxy matched entry for method=" + request.getMethod()
                        + " uri=" + request.getRequestURI()
                        + " allowedMethods=" + entry.getMethods()
                        + " patterns=" + Arrays.toString(entry.getUrls()));

                final Optional<JWToken> token = APILocator.getApiTokenAPI()
                        .fromJwt(new String(entry.getUserToken()),
                                request.getRemoteAddr());

                Logger.info(this, "UserProxy token lookup result for uri="
                        + request.getRequestURI() + ": tokenPresent=" + token.isPresent());

                User user = Try.of(() -> token.get().getActiveUser().get()).getOrNull();

                if (user != null) {
                    Logger.info(this, "UserProxy authenticated userId=" + user.getUserId()
                            + " for uri=" + request.getRequestURI());
                    request.setAttribute(WebKeys.USER, user);
                    request.setAttribute(WebKeys.USER_ID, user.getUserId());
                    break;
                }

                Logger.warn(this, "UserProxy match did not resolve an active user for uri="
                        + request.getRequestURI() + " remoteAddr=" + request.getRemoteAddr());
            } else {
                Logger.debug(this, () -> "UserProxy entry did not match. method="
                        + request.getMethod() + " uri=" + request.getRequestURI()
                        + " allowedMethods=" + entry.getMethods()
                        + " patterns=" + Arrays.toString(entry.getUrls()));
            }
        }

        return Result.NEXT;

    }

    public boolean hasExistingAuth(HttpServletRequest request) {
        User user = PortalUtil.getUser(request);
        if (user != null && !user.isAnonymousUser()) {
            return true;
        }
        if (request.getHeader("Authorization") != null) {
            return true;
        }

        return false;
    }

    public boolean matches(HttpServletRequest request, UserProxyEntry entry) {
        String url = getFullURL(request);
        String method = request.getMethod();

        if (!entry.getMethods().contains(method)) {
            return false;
        }
        boolean urlMatch = false;
        for (Pattern p : entry.getUrls()) {
            if (p.matcher(url).matches()) {
                urlMatch = true;
                break;
            }
        }
        return urlMatch;
    }

    String getFullURL(HttpServletRequest request) {
        StringBuilder requestURL = new StringBuilder(request.getRequestURL().toString());
        String queryString = request.getQueryString();

        if (queryString == null) {
            return requestURL.toString();
        } else {
            return requestURL.append('?').append(queryString).toString();
        }
    }

}
