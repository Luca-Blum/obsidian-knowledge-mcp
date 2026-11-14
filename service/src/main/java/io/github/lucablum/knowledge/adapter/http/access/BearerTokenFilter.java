package io.github.lucablum.knowledge.adapter.http.access;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.access.AuthorizeClients;
import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

/**
 * Lets a request through only with a bearer token: the static token of a configured Client, such as Claude Code, or
 * an OAuth access token this instance issued, such as claude.ai's. So the MCP endpoint and {@code /admin} stay closed
 * wherever they are mounted. Health stays open, as Docker checks it without a token and it shows only whether the
 * instance is up, and so do the OAuth endpoints a Client needs before it has a token.
 */
@ApplicationScoped
@PrimaryAdapter
public class BearerTokenFilter {

    private static final Logger LOG = Logger.getLogger(BearerTokenFilter.class);

    /** Runs before every route, the MCP server's included. */
    private static final int PRIORITY = 100;

    private static final String HEALTH = "/q/health";

    private static final String BEARER = "Bearer ";

    private final Map<String, String> tokens;
    private final Optional<OAuthEndpoints> oauth;
    private final AuthorizeClients authorize;

    public BearerTokenFilter(CollectionConfig collection, AuthorizeClients authorize) {
        this.tokens = configuredTokens(collection.auth().tokens());
        this.oauth = OAuthEndpoints.of(collection);
        this.authorize = authorize;
        if (tokens.isEmpty() && oauth.isEmpty()) {
            throw new IllegalStateException("Neither a client token nor OAuth is configured for Collection %s, so no "
                    + "Client could reach it: set COLLECTION_AUTH_TOKENS_<CLIENT> or COLLECTION_OAUTH_PASSPHRASE"
                    .formatted(collection.name()));
        }
    }

    /** Leaves out every Client whose token was removed. */
    private static Map<String, String> configuredTokens(Map<String, Optional<String>> tokens) {
        return tokens.entrySet().stream()
                .filter(client -> client.getValue().isPresent())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, client -> client.getValue().get()));
    }

    void register(@Observes Filters filters) {
        filters.register(this::filter, PRIORITY);
    }

    private void filter(RoutingContext request) {
        if (isOpen(request.normalizedPath())) {
            request.next();
            return;
        }
        Optional<String> token = bearerToken(request.request().getHeader(HttpHeaders.AUTHORIZATION));
        if (token.isEmpty()) {
            rejectAsUnauthorized(request);
            return;
        }
        Optional<String> client = clientOf(token.get());
        if (client.isPresent()) {
            LOG.debugf("Client %s: %s %s", client.get(), request.request().method(), request.normalizedPath());
            request.next();
        } else if (oauth.isPresent()) {
            admitAccessToken(request, token.get());
        } else {
            rejectAsUnauthorized(request);
        }
    }

    private boolean isOpen(String path) {
        return path.startsWith(HEALTH) || oauth.filter(endpoints -> endpoints.isPublic(path)).isPresent();
    }

    /** Looking up the token blocks on the database, so it runs off the event loop while the request waits. */
    private void admitAccessToken(RoutingContext request, String accessToken) {
        request.request().pause();
        request.vertx().executeBlocking(() -> authorize.admits(accessToken), false).onComplete(admitted -> {
            request.request().resume();
            if (admitted.failed()) {
                LOG.error("Checking an access token failed", admitted.cause());
                request.fail(admitted.cause());
            } else if (admitted.result()) {
                LOG.debugf("OAuth client: %s %s", request.request().method(), request.normalizedPath());
                request.next();
            } else {
                rejectAsUnauthorized(request);
            }
        });
    }

    /** With OAuth on, the header tells a Client such as claude.ai where to find out how to get a token. */
    private void rejectAsUnauthorized(RoutingContext request) {
        LOG.infof("Rejected %s %s without a known token", request.request().method(), request.normalizedPath());
        String challenge = oauth
                .map(endpoints -> "Bearer resource_metadata=\"%s\"".formatted(endpoints.resourceMetadata()))
                .orElse("Bearer");
        request.response()
                .setStatusCode(401)
                .putHeader("WWW-Authenticate", challenge)
                .end();
    }

    /** The token the header carries; the scheme {@code Bearer} may be in any case. */
    private static Optional<String> bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return Optional.empty();
        }
        return Optional.of(authorization.substring(BEARER.length()));
    }

    /** The name of the Client whose static token this is. */
    private Optional<String> clientOf(String token) {
        byte[] presented = token.getBytes(StandardCharsets.UTF_8);
        return tokens.entrySet().stream()
                .filter(client -> matches(presented, client.getValue()))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** Compares in constant time, so the response time does not give away how much of a token was right. */
    private static boolean matches(byte[] presented, String token) {
        return MessageDigest.isEqual(presented, token.getBytes(StandardCharsets.UTF_8));
    }
}
