package com.dotcms.userproxy.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.dotmarketing.util.Logger;

/**
 * Immutable entry representing a user proxy configuration.
 * Contains the user token, allowed HTTP methods, and URL patterns.
 */
public final class UserProxyEntry {

    private final char[] userToken;
    private final List<String> methods;
    private final Pattern[] urls;

    /**
     * Constructs a UserProxyEntry with the given parameters.
     *
     * @param userToken the user authentication token as a char array
     * @param methods   array of allowed HTTP methods (e.g., "GET", "POST")
     * @param urls      array of URL patterns that this entry applies to
     */
    public UserProxyEntry(final String userToken, final String methods, final String[] urls) {
        this.userToken = userToken != null ? userToken.toCharArray() : new char[0];
        this.methods = methods != null ? Arrays.asList(methods.toLowerCase().split("\\s*,\\s*", -1)) : List.of();
        this.urls = stringsToPatterns(urls);
    }

    Pattern[] stringsToPatterns(String[] strings) {
        if (strings == null) {
            return new Pattern[0];
        }
        List<Pattern> patterns = new ArrayList<>();
        for (String regex : strings) {
            try {
                patterns.add(Pattern.compile(regex));
            } catch (Exception e) {
                Logger.warn(this.getClass(), "Cannot compile url pattern for userproxy:" + regex);
            }
        }
        return patterns.toArray(new Pattern[0]);

    }

    /**
     * Gets a copy of the user token.
     *
     * @return a copy of the user token char array
     */
    public char[] getUserToken() {
        return userToken.clone();
    }

    /**
     * Gets a copy of the allowed methods.
     *
     * @return a copy of the methods array
     */
    public List<String> getMethods() {
        return methods;
    }

    /**
     * Gets a copy of the URL patterns.
     *
     * @return a copy of the URLs array
     */
    public Pattern[] getUrls() {
        return urls;
    }

    /**
     * Matches the given request against this entry's methods and URL patterns.
     *
     * @param request the HttpServletRequest to match
     * @return true if the request method and URL match this entry's configuration
     */
    public boolean matches(final HttpServletRequest request) {
        String url = request.getRequestURI();
        String method = request.getMethod().toLowerCase();

        // Check if method matches (case-insensitive)
        if (!methods.contains(method)) {
            return false;
        }

        // Check if URL matches any pattern
        for (Pattern p : urls) {
            if (p.matcher(url).find()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {

        return "UserProxyEntry{" +
                "userToken=" + (userToken != null ? "***" : "null") +
                ", methods=" + methods.toString() +
                ", urls=" + java.util.Arrays.toString(urls) +
                '}';
    }

}
