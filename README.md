# dotCMS User Proxy Plugin

A dotCMS OSGI plugin that provides user proxy authentication for API requests without requiring explicit login credentials. This plugin allows you to set a user token and map it to specific HTTP methods and URL patterns to allow access without having to pass user credientials.

## Overview

The User Proxy Plugin is a web interceptor-based solution for dotCMS that intercepts incoming HTTP requests and validates them against configured user proxy rules. It allows you to:

- **Authenticate requests** using pre-configured API tokens
- **Control access** by HTTP method (GET, POST, PUT, DELETE, etc.)
- **Restrict endpoints** using URL pattern matching with regex support
- **Seamlessly integrate** with dotCMS's native user and permission system

## Features

- **Token-based Authentication**: Validate requests using JWT tokens associated with dotCMS users
- **IP based Authentication**: Because tokens validity can be limited by IP, you can that limit access to this proxy by IP address as well.
- **Method Filtering**: Restrict access based on HTTP methods (GET, POST, PUT, DELETE, PATCH, etc.)
- **URL Pattern Matching**: Support for regex patterns to match request URLs
- **Per-Site Configuration**: Configure user proxy rules on a per-site/host basis
- **Dynamic Reloading**: Configuration changes are reflected without requiring plugin restart
- **Existing Auth Bypass**: Automatically skips proxy validation if request already has valid authentication
- **Secure Token Handling**: Tokens are stored securely as char arrays and never logged in plain text

## Installation

Build the plugin from source using the steps below.



### Prerequisites

- dotCMS [26.10.28-02](https://github.com/dotCMS/core/releases/tag/v26.10.28-02) (latest stable release verified on 2026-10-28)
- Java 25 (matching this dotCMS release)
- Maven 3.9+ (the included wrapper uses 3.9.9)

### Building the Plugin

```bash
./mvnw clean package
```

This will create an OSGi bundle JAR file in the `target/` directory:
```
target/userproxy-26.10.28-02.jar
```

### Deploying to dotCMS

1. Copy the generated JAR file to your dotCMS dynamic plugin directory (remove any older User Proxy bundle first):
   ```bash
   cp target/userproxy-26.10.28-02.jar /path/to/dotcms/felix/load/
   ```

2. Start or restart dotCMS. The plugin will automatically:
   - Copy the app configuration YAML file
   - Register the web interceptor
   - Initialize event listeners

3. Verify installation by checking the dotCMS logs:
   ```
   INFO com.dotcms.userproxy.osgi.Activator - Starting UserProxy Plugin
   INFO com.dotcms.userproxy.osgi.Activator - Starting App Listener
   ```

## Configuration

Configure rules in the **User Proxy / Anonymous Access** app for each site. Rules are evaluated in order; the first matching rule with a valid token supplies the request user. If no site rule authenticates the request, System Host rules are tried. Configuration is read through the dotCMS Apps API cache, so saves and deletions use dotCMS cluster invalidation; each node reloads its own values.

**Security:** Matching endpoints become publicly accessible with the token user's permissions. Use a least-privilege service account and narrow paths and methods. Keep configured regexes simple to avoid expensive matching on every request.

**Upgrade note:** URL patterns now match the **entire request path**, not a substring. Use `/pages/protected/.*` for subpaths, not the glob `/pages/protected/*`. Query strings are not matched. This release targets Java 25 and is not compatible with older Java runtimes.

### Configuration Format

```json
    {
        "config": [
            {
                "userToken": "api-user-token-0xZjQ....",
                "methods": "GET,POST,HEAD,OPTIONS",
                "urls": [
                    "/api/v1/page/json/.*",
                    "/api/v1/content/_search",
                    "/api/v1/graphql"
                ]
            },
            {
                "userToken": "page-user-token-7gf16....",
                "methods": "GET",
                "urls": [
                    "/pages/protected/.*"
                ]
            }
        ]
    }
```

### Configuration Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `userToken` | String | JWT token associated with a dotCMS user. This token is used to authenticate requests. |
| `methods` | String | Comma-separated list of allowed HTTP methods (case-insensitive). Example: `"GET,POST,PUT"` |
| `urls` | Array[String] | Regular expressions matched against the entire request URI path, excluding the query string. |

### Example Configurations

**API-only access for a service account:**
```json
{
    "userToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "methods": "GET,POST",
    "urls": [
        "/api/v1/.*"
    ]
}
```

**Read-only access to specific pages:**
```json
{
    "userToken": "token123...",
    "methods": "GET",
    "urls": [
        "/pages/blog/.*",
        "/pages/news/.*"
    ]
}
```

**GraphQL access:**
```json
{
    "userToken": "token456...",
    "methods": "POST",
    "urls": [
        "/api/v1/graphql"
    ]
}
```

## Usage

### How It Works

1. **Request Interception**: Every incoming HTTP request is intercepted by the `UserProxyInterceptor`
2. **Pre-Authentication Check**: If the request already has valid authentication, the plugin skips processing
3. **Configuration Lookup**: The plugin retrieves user proxy configurations for the current site/host
4. **Rule Matching**: The request's HTTP method and URI are matched against configured rules
5. **User Assignment**: If a match is found, the token is validated and the associated user is assigned to the request
6. **Request Continuation**: The request continues with the authenticated user context

### Request Lifecycle

```
Incoming Request
       ↓
Has Existing Auth? → YES → Skip Proxy → Continue
       ↓ NO
Load Site Config
       ↓
Match Method + URL Pattern? → NO → Continue as Anonymous
       ↓ YES
Validate JWT Token
       ↓
User Found? → YES → Assign User to Request
       ↓ NO
Continue with Original User
       ↓
End
```

### Authentication Header Alternative

While this plugin is designed for token-based configuration, requests can also use standard `Authorization` headers. If an Authorization header is present, the plugin will skip its proxy authentication process.

## Architecture

### Key Components

#### 1. **Activator** (`com.dotcms.userproxy.osgi.Activator`)
- OSGI bundle lifecycle management
- Registers the web interceptor on bundle start
- Copies app configuration YAML files
- Sets up event listeners for app secret changes
- Cleans up resources on bundle stop

#### 2. **UserProxyInterceptor** (`com.dotcms.userproxy.interceptor.UserProxyInterceptor`)
- Implements `WebInterceptor` interface
- Intercepts all HTTP requests (`/*`)
- Performs request validation and user assignment
- Reads per-host configuration through the dotCMS Apps API cache

#### 3. **UserProxyEntry** (`com.dotcms.userproxy.model.UserProxyEntry`)
- Immutable configuration model
- Stores user token, allowed methods, and URL patterns
- Provides request matching logic

#### 4. **UserProxyEntryMapper** (`com.dotcms.userproxy.model.UserProxyEntryMapper`)
- Loads and parses user proxy configurations
- Builds configuration map per site

#### 5. **FileMoverUtil** (`com.dotcms.userproxy.osgi.FileMoverUtil`)
- Copies plugin resources to dotCMS
- Manages app YAML configuration files
- Creates the apps directory when needed and closes copied resources

### Request Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    HTTP Request                                 │
└────────────────────────────┬────────────────────────────────────┘
                             ↓
                    ┌────────────────────┐
                    │ UserProxyInterceptor│
                    │  .intercept()       │
                    └────────┬────────────┘
                             ↓
                 ┌───────────────────────────┐
                 │ Has Existing Auth?        │
                 └──────────┬────────────────┘
                   YES ↙    ↘ NO
                      ↓      ↓
                  Result.NEXT │
                      ↑       ↓
                      │   Get Host Config
                      │       ↓
                      │   UserProxyEntry.matches()
                      │       ↓
                      │   ┌───────────┐
                      │   │ Match?    │
                      │   └─┬───────┬─┘
                      │  NO ↓   YES ↓
                      │    │   Validate Token
                      │    │       ↓
                      │    │   Set User on Request
                      │    ↓   ↓
                      └───────────┘
                             ↓
                    Return Result.NEXT
```

## Development

### Project Structure

```
com.dotcms.userproxy/
├── pom.xml                                    # Maven configuration
├── README.md                                  # This file
├── src/
│   ├── main/
│   │   ├── java/com/dotcms/userproxy/
│   │   │   ├── osgi/
│   │   │   │   ├── Activator.java            # Bundle lifecycle
│   │   │   │   └── FileMoverUtil.java        # File utilities
│   │   │   ├── interceptor/
│   │   │   │   └── UserProxyInterceptor.java # Web interceptor
│   │   │   ├── listener/
│   │   │   │   └── UserProxyAppListener.java # Event listener
│   │   │   ├── model/
│   │   │   │   ├── UserProxyEntry.java       # Configuration model
│   │   │   │   └── UserProxyEntryMapper.java # Config loader
│   │   │   └── util/
│   │   │       └── AppKey.java               # Constants
│   │   └── resources/
│   │       ├── Dotuserproxy.yml              # App descriptor
│   │       └── example-userproxy.json        # Example config
│   └── test/
│       └── java/com/dotcms/userproxy/        # Unit tests
└── target/                                   # Build output
```

### Building

Build the plugin using Maven:

```bash
./mvnw clean package
```

### Testing

Run unit tests:

```bash
./mvnw test
```

Run all checks and package the bundle:

```bash
./mvnw verify
```

There are no live-server integration tests. Before production, deploy to staging and verify authentication, System Host fallback, configuration updates/deletions across nodes, and bundle stop/start.

### Code Quality

The plugin follows dotCMS conventions:
- Maven Bundle Plugin for OSGi packaging
- Felix framework integration
- Logging via dotCMS Logger API
- Exception handling with DotRuntimeException
- Immutable models where appropriate

## Dependencies

### Core Dependencies

- **dotcms-core** (26.10.28-02) - dotCMS core API and framework (provided by the server)
- **maven-bundle-plugin** (6.2.0) - OSGi bundle packaging

### Test Dependencies

- **JUnit 5** (5.12.2) - Testing framework
- **Mockito** (5.24.0) - Mocking framework with Java 25 support

### Runtime Dependencies

Runtime APIs are provided by dotCMS, not embedded in the plugin bundle.

## Troubleshooting

### Plugin Not Activating

**Problem**: The plugin fails to start.

**Solutions**:
- Check dotCMS logs for startup errors
- Verify Java version is 25
- Ensure JAR file is in the correct plugins directory
- Check that all required dotCMS core APIs are available

### Requests Not Authenticated

**Problem**: Requests are not being matched against user proxy rules.

**Solutions**:
- Verify the site or System Host has a configuration in the User Proxy app
- Check that URL patterns are valid regex expressions
- Ensure user token is correctly formatted and valid
- Verify HTTP method matches (case-insensitive)
- Check dotCMS logs for UserProxyInterceptor debug messages

### Token Validation Errors

**Problem**: Valid tokens are being rejected.

**Solutions**:
- Verify the token is a valid JWT for a dotCMS user
- Check token expiration date
- Ensure user associated with token is active and not locked
- Verify token permissions in dotCMS

### Configuration Not Updating

**Problem**: App configuration changes are not reflected.

**Solutions**:
- Save valid JSON in the User Proxy app for the correct site
- Check System Host rules if deleted site rules still appear to grant access
- Verify dotCMS cache invalidations reach all cluster nodes

## Logging

The plugin logs its activities using dotCMS Logger API:

```
INFO com.dotcms.userproxy.osgi.Activator - Starting UserProxy Plugin
INFO com.dotcms.userproxy.osgi.Activator - Copying UserProxy APP
INFO com.dotcms.userproxy.osgi.Activator - Starting App Listener
```

Enable debug logging to see detailed request matching:

```properties
log4j.logger.com.dotcms.userproxy=DEBUG
```

## Contributing

When making changes to this plugin:

1. Follow dotCMS code style conventions
2. Add unit tests for new functionality
3. Update this README if behavior changes
4. Test with multiple dotCMS instances if possible
5. Verify no regression in existing functionality

## License

This plugin is provided as part of the dotCMS community plugins. Refer to the main dotCMS LICENSE file for licensing details.

## Support

For issues, questions, or contributions:

1. Check the dotCMS documentation
2. Review the code comments and JavaDoc
3. Check dotCMS logs for error details
4. Reach out to the dotCMS community

## Version History

### v26.10.28-02
- Updated to dotCMS 26.10.28-02 and Java 25
- Restored request user assignment and System Host fallback
- Reused dotCMS Apps API caching instead of a separate versioned cache
- Restricted rules to full-path regex matching and corrected examples
- Fixed resource copying, missing configuration handling, and malformed-JSON logging
- Added authentication and path-matching regression tests

### v25.10.05
- Initial release
- Core user proxy authentication functionality
- Support for token-based access control
- URL pattern matching with regex support
- Per-site configuration management

## Additional Resources

- [dotCMS Official Documentation](https://www.dotcms.com/docs)
- [dotCMS OSGi Plugin Development](https://www.dotcms.com/docs/latest/osgi-plugins)
- [Servlet 3.1 API](https://javaee.github.io/javaee-spec/javadocs/javax/servlet/package-summary.html) (dotCMS still uses `javax.servlet`)
- [OSGi Framework Specification](https://osgi.org/specification/)
