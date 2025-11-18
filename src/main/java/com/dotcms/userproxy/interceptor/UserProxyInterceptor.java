package com.dotcms.userproxy.interceptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
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
import com.liferay.portal.model.User;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portal.util.WebKeys;

import io.vavr.Lazy;
import io.vavr.control.Try;

/**
 * Web interceptor for user proxy authentication and authorization.
 * Loads user proxy configurations from userproxy.json and validates requests
 * against configured user tokens, HTTP methods, and URL patterns.
 */
public class UserProxyInterceptor implements WebInterceptor {

    private static final ConcurrentHashMap<String,Optional<UserProxyEntry>> lazyUserProxyMap = new ConcurrentHashMap<>();

    public UserProxyInterceptor() {
        resetLazyUserProxyMap();
    }

    public static void resetLazyUserProxyMap() {
        lazyUserProxyMap.clear();
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

        Host host = WebAPILocator.getHostWebAPI().getCurrentHostNoThrow(request);

        Optional<UserProxyEntry> entry = lazyUserProxyMap.computeIfAbsent(host.getIdentifier(),h->UserProxyEntryMapper.buildMapForHost(h));

        if(entry.isEmpty()){
            return Result.NEXT;
        }


        if (entry.get().matches(request)) {
            final Optional<JWToken> token = APILocator.getApiTokenAPI().fromJwt(new String(entry.get().getUserToken()),
                    request.getRemoteAddr());

            User user = Try.of(() -> token.get().getActiveUser().get()).getOrNull();

            if (user != null) {
                request.setAttribute(WebKeys.USER, user);
                request.setAttribute(WebKeys.USER_ID, user.getUserId());
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
        String url = request.getRequestURI();
        String method = request.getMethod();
        boolean methodMatch = Collections.binarySearch(entry.getMethods(), method) > -1;
        boolean urlMatch = false;
        for (Pattern p : entry.getUrls()) {
            if (p.matcher(url).matches()) {
                urlMatch = true;
                break;
            }
        }
        return methodMatch && urlMatch;
    }

}
