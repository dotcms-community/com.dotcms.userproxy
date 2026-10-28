package com.dotcms.userproxy.interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dotcms.auth.providers.jwt.beans.JWToken;
import com.dotcms.auth.providers.jwt.factories.ApiTokenAPI;
import com.dotcms.filters.interceptor.Result;
import com.dotcms.userproxy.model.UserProxyEntry;
import com.dotcms.userproxy.model.UserProxyEntryMapper;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.web.HostWebAPI;
import com.dotmarketing.business.web.WebAPILocator;
import com.liferay.portal.model.User;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portal.util.WebKeys;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class UserProxyInterceptorTest {

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final Host host = mock(Host.class);
    private final Host systemHost = mock(Host.class);
    private final ApiTokenAPI tokens = mock(ApiTokenAPI.class);
    private final User user = mock(User.class);
    private final UserProxyInterceptor interceptor = new UserProxyInterceptor();
    private MockedStatic<APILocator> apis;
    private MockedStatic<WebAPILocator> webApis;
    private MockedStatic<PortalUtil> portal;
    private MockedStatic<UserProxyEntryMapper> mapper;

    @BeforeEach
    void setUp() {
        apis = mockStatic(APILocator.class);
        webApis = mockStatic(WebAPILocator.class);
        portal = mockStatic(PortalUtil.class);
        mapper = mockStatic(UserProxyEntryMapper.class);
        HostWebAPI hostApi = mock(HostWebAPI.class);
        webApis.when(WebAPILocator::getHostWebAPI).thenReturn(hostApi);
        when(hostApi.getCurrentHostNoThrow(request)).thenReturn(host);
        when(host.getIdentifier()).thenReturn("site");
        when(systemHost.getIdentifier()).thenReturn("SYSTEM_HOST");
        when(systemHost.isSystemHost()).thenReturn(true);
        apis.when(APILocator::systemHost).thenReturn(systemHost);
        apis.when(APILocator::getApiTokenAPI).thenReturn(tokens);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getRemoteAddr()).thenReturn("192.0.2.1");
        when(user.getUserId()).thenReturn("proxy-user");
        mapper.when(() -> UserProxyEntryMapper.buildListForHost("site")).thenReturn(List.of());
        mapper.when(() -> UserProxyEntryMapper.buildListForHost("SYSTEM_HOST")).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        mapper.close();
        portal.close();
        webApis.close();
        apis.close();
    }

    private UserProxyEntry rule(String token, String path) {
        return new UserProxyEntry(token, "GET", new String[]{path});
    }

    private void validToken(String token) {
        JWToken jwt = mock(JWToken.class);
        when(jwt.getActiveUser()).thenReturn(Optional.of(user));
        when(tokens.fromJwt(token, "192.0.2.1")).thenReturn(Optional.of(jwt));
    }

    @Test
    void assignsTheMatchedUserAndValidatesTheClientIp() {
        mapper.when(() -> UserProxyEntryMapper.buildListForHost("site"))
                .thenReturn(List.of(rule("site-token", "/api/test")));
        validToken("site-token");

        assertEquals(Result.NEXT, interceptor.intercept(request, response));
        verify(request).setAttribute(WebKeys.USER, user);
        verify(request).setAttribute(WebKeys.USER_ID, "proxy-user");
        verify(tokens).fromJwt("site-token", "192.0.2.1");
        mapper.verify(() -> UserProxyEntryMapper.buildListForHost("SYSTEM_HOST"), never());
    }

    @Test
    void fallsBackToSystemHostWhenTheSiteHasNoRules() {
        mapper.when(() -> UserProxyEntryMapper.buildListForHost("SYSTEM_HOST"))
                .thenReturn(List.of(rule("system-token", "/api/test")));
        validToken("system-token");

        interceptor.intercept(request, response);
        verify(request).setAttribute(WebKeys.USER, user);
    }

    @Test
    void fallsBackWhenSiteRulesDoNotMatch() {
        mapper.when(() -> UserProxyEntryMapper.buildListForHost("site"))
                .thenReturn(List.of(rule("site-token", "/other")));
        mapper.when(() -> UserProxyEntryMapper.buildListForHost("SYSTEM_HOST"))
                .thenReturn(List.of(rule("system-token", "/api/test")));
        validToken("system-token");

        interceptor.intercept(request, response);
        verify(tokens, never()).fromJwt(eq("site-token"), anyString());
        verify(request).setAttribute(WebKeys.USER, user);
    }

    @Test
    void invalidTokensDoNotAuthenticate() {
        mapper.when(() -> UserProxyEntryMapper.buildListForHost("site"))
                .thenReturn(List.of(rule("invalid-token", "/api/test")));
        when(tokens.fromJwt("invalid-token", "192.0.2.1")).thenReturn(Optional.empty());

        assertEquals(Result.NEXT, interceptor.intercept(request, response));
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void doesNotOverrideAuthorizationHeadersOrLoggedInUsers() {
        when(request.getHeader("Authorization")).thenReturn("Bearer explicit-token");
        interceptor.intercept(request, response);
        mapper.verifyNoInteractions();

        when(request.getHeader("Authorization")).thenReturn(null);
        portal.when(() -> PortalUtil.getUser(request)).thenReturn(user);
        interceptor.intercept(request, response);
        mapper.verifyNoInteractions();
        verifyNoInteractions(tokens);
    }

    @Test
    void reloadsRulesForEachRequestInsteadOfKeepingRevokedConfiguration() {
        mapper.when(() -> UserProxyEntryMapper.buildListForHost("site"))
                .thenReturn(List.of(rule("site-token", "/api/test")), List.of());
        validToken("site-token");
        interceptor.intercept(request, response);
        clearInvocations(request);

        interceptor.intercept(request, response);
        verify(request, never()).setAttribute(anyString(), any());
        verify(tokens, times(1)).fromJwt("site-token", "192.0.2.1");
    }
}
