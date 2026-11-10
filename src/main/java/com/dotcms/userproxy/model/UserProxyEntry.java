package com.dotcms.userproxy.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.dotmarketing.util.Logger;

/**
 * Immutable entry representing a user proxy configuration.
 * Contains the user token, allowed HTTP methods, and URL patterns.
 */
public final class UserProxyEntry {

    private static final int MAX_URL_LENGTH = 2048;
    private static final long MATCH_BUDGET_NANOS = 50_000_000L;

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
        this.methods = methods != null
                ? List.copyOf(Arrays.asList(methods.trim().toLowerCase(Locale.ROOT).split("\\s*,\\s*", -1)))
                : List.of();
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
        return urls.clone();
    }

    /**
     * Matches the request method and entire URI, including the raw query string.
     *
     * @param request the HttpServletRequest to match
     * @return true if the request method and URL match this entry's configuration
     */
    public boolean matches(final HttpServletRequest request) {
        String url = request.getRequestURI();
        String query = request.getQueryString();
        if (query != null) {
            url += "?" + query;
        }
        String method = request.getMethod().toLowerCase(Locale.ROOT);

        // Check if method matches (case-insensitive)
        if (!methods.contains(method) || url.length() > MAX_URL_LENGTH) {
            return false;
        }

        // Share one deadline across all patterns in this entry.
        Deadline input = new Deadline(url, System.nanoTime() + MATCH_BUDGET_NANOS);
        for (Pattern p : urls) {
            try {
                if (p.matcher(input).matches()) {
                    return true;
                }
            } catch (IllegalStateException e) {
                Logger.warn(getClass(), "User Proxy regex matching timed out for pattern: " + p);
                return false;
            }
        }

        return false;
    }

    private record Deadline(CharSequence value, long endNanos) implements CharSequence {
        @Override
        public int length() {
            return value.length();
        }

        @Override
        public char charAt(int index) {
            if (System.nanoTime() - endNanos >= 0) {
                throw new IllegalStateException("User Proxy regex timeout");
            }
            return value.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new Deadline(value.subSequence(start, end), endNanos);
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    @Override
    public String toString() {

        return "UserProxyEntry{" +
                "userToken=***" +
                ", methods=" + methods.toString() +
                ", urls=" + java.util.Arrays.toString(urls) +
                '}';
    }

}
