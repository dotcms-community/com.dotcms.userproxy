# dotCMS User Proxy Plugin

A dotCMS OSGI plugin that provides user proxy authentication and authorization capabilities. This plugin allows you to configure user tokens and map them to specific HTTP methods and URL patterns, enabling seamless user authentication for API requests without requiring explicit login credentials.

## Overview

The User Proxy Plugin is a web interceptor-based solution for dotCMS that intercepts incoming HTTP requests and validates them against configured user proxy rules. It allows you to:

- **Authenticate requests** using pre-configured API tokens
- **Control access** by HTTP method (GET, POST, PUT, DELETE, etc.)
- **Restrict endpoints** using URL pattern matching with regex support
- **Seamlessly integrate** with dotCMS's native user and permission system

## Features

- **Token-based Authentication**: Validate requests using JWT tokens associated with dotCMS users
- **Method Filtering**: Restrict access based on HTTP methods (GET, POST, PUT, DELETE, PATCH, etc.)
- **URL Pattern Matching**: Support for regex patterns to match request URLs
- **Per-Site Configuration**: Configure user proxy rules on a per-site/host basis
- **Dynamic Reloading**: Configuration changes are reflected without requiring plugin restart
- **Existing Auth Bypass**: Automatically skips proxy validation if request already has valid authentication
- **Secure Token Handling**: Tokens are stored securely as char arrays and never logged in plain text

## Installation

### Prerequisites

- dotCMS 25.10.03 or later
- Java 11 or later
- Maven 3.6+

### Building the Plugin

```bash
./mvnw clean package
```

This will create an OSGi bundle JAR file in the `target/` directory:
```
target/userproxy-25.10.05.jar
```

### Deploying to dotCMS

1. Copy the generated JAR file to your dotCMS plugins directory:
   ```bash
   cp target/userproxy-25.10.05.jar /path/to/dotcms/plugins/
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

### Configuration File

User proxy rules are configured in a JSON file (`userproxy.json`) that must be placed in your dotCMS `/apps` directory. The plugin automatically loads configurations for each site.

### Configuration Format

```json
{
    "config": 
        {
            "userToken": "your-jwt-token-here",
            "methods": "GET,POST",
            "urls": [
                "/api/v1/page/json.*",
                "/api/v1/content/_search.*",
                "/api/v1/graphql/.*",
                "/pages/protected-directory/.*"
            ]
        }
    
}
```

### Configuration Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `userToken` | String | JWT token associated with a dotCMS user. This token is used to authenticate requests. |
| `methods` | String | Comma-separated list of allowed HTTP methods (case-insensitive). Example: `"GET,POST,PUT"` |
| `urls` | Array[String] | Array of regex patterns that match the request URI paths. The interceptor uses regex matching. |

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
- Manages lazy-loaded configuration cache per host

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
- Handles file asset creation from JAR resources

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

Run integration tests:

```bash
./mvnw verify
```

### Code Quality

The plugin follows dotCMS conventions:
- Maven Bundle Plugin for OSGi packaging
- Felix framework integration
- Logging via dotCMS Logger API
- Exception handling with DotRuntimeException
- Immutable models where appropriate

## Dependencies

### Core Dependencies

- **dotcms-core** (25.10.03-01) - dotCMS core API and framework
- **maven-bundle-plugin** (5.1.9) - OSGi bundle packaging
- **aspectj** (1.8.10) - Aspect-oriented programming support

### Test Dependencies

- **JUnit 5** (5.12.2) - Testing framework
- **Mockito** (5.17.0) - Mocking framework

### Runtime Dependencies

Dependencies are embedded in the bundle JAR file during build.

## Troubleshooting

### Plugin Not Activating

**Problem**: The plugin fails to start.

**Solutions**:
- Check dotCMS logs for startup errors
- Verify Java version is 11 or later
- Ensure JAR file is in the correct plugins directory
- Check that all required dotCMS core APIs are available

### Requests Not Authenticated

**Problem**: Requests are not being matched against user proxy rules.

**Solutions**:
- Verify configuration file exists in `/path/to/dotcms/apps/userproxy.json`
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

**Problem**: Changes to userproxy.json are not reflected.

**Solutions**:
- Restart the dotCMS instance
- Or trigger app cache refresh through dotCMS admin UI
- Verify file is valid JSON format

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

### v25.10.05
- Initial release
- Core user proxy authentication functionality
- Support for token-based access control
- URL pattern matching with regex support
- Per-site configuration management

## Additional Resources

- [dotCMS Official Documentation](https://www.dotcms.com/docs)
- [dotCMS OSGi Plugin Development](https://www.dotcms.com/docs/latest/osgi-plugins)
- [Jakarta Servlet API](https://jakarta.ee/specifications/servlet/)
- [OSGi Framework Specification](https://osgi.org/specification/)
