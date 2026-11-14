package io.github.lucablum.knowledge.adapter.http.access;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.access.AuthorizeClients;
import io.github.lucablum.knowledge.application.access.ClientCredentials;
import io.github.lucablum.knowledge.application.access.CodeRedemption;
import io.github.lucablum.knowledge.domain.access.IssuedTokens;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

/**
 * {@code POST /token}: a Client trades a code, or a refresh token, for new tokens. It names itself with
 * {@code client_id}, and a Client registered with a secret sends it in the form or with HTTP Basic.
 */
@ApplicationScoped
@PrimaryAdapter
public class TokenRoute {

    private static final int BODY_LIMIT = 16 * 1024;

    private static final String BASIC = "Basic ";

    private final AuthorizeClients authorize;
    private final CollectionConfig collection;

    public TokenRoute(AuthorizeClients authorize, CollectionConfig collection) {
        this.authorize = authorize;
        this.collection = collection;
    }

    void register(@Observes Router router) {
        if (OAuthEndpoints.of(collection).isPresent()) {
            router.post(OAuthEndpoints.TOKEN)
                    .handler(BodyHandler.create().setBodyLimit(BODY_LIMIT))
                    .blockingHandler(this::token, false);
        }
    }

    private void token(RoutingContext request) {
        MultiMap form = request.request().formAttributes();
        Optional<ClientCredentials> credentials = credentials(request, form);
        if (credentials.isEmpty()) {
            Responses.error(request, 400, "invalid_request", "client_id is missing");
            return;
        }
        String grantType = String.valueOf(form.get("grant_type"));
        switch (grantType) {
            case "authorization_code" -> redeemCode(request, form, credentials.get());
            case "refresh_token" -> refresh(request, form, credentials.get());
            default -> Responses.error(request, 400, "unsupported_grant_type", "Unsupported: " + grantType);
        }
    }

    private void redeemCode(RoutingContext request, MultiMap form, ClientCredentials credentials) {
        String code = form.get("code");
        String redirectUri = form.get("redirect_uri");
        String verifier = form.get("code_verifier");
        if (code == null || redirectUri == null || verifier == null) {
            Responses.error(request, 400, "invalid_request", "code, redirect_uri and code_verifier are required");
            return;
        }
        Optional<IssuedTokens> tokens;
        try {
            tokens = authorize.redeem(new CodeRedemption(code, credentials, URI.create(redirectUri), verifier));
        } catch (IllegalArgumentException e) {
            tokens = Optional.empty();
        }
        answer(request, tokens);
    }

    private void refresh(RoutingContext request, MultiMap form, ClientCredentials credentials) {
        String refreshToken = form.get("refresh_token");
        if (refreshToken == null) {
            Responses.error(request, 400, "invalid_request", "refresh_token is required");
            return;
        }
        answer(request, authorize.refresh(refreshToken, credentials));
    }

    /** Every refusal is {@code invalid_grant}, so a Client learns nothing about what it got wrong. */
    private static void answer(RoutingContext request, Optional<IssuedTokens> tokens) {
        tokens.ifPresentOrElse(
                issued -> Responses.json(request, 200, new JsonObject()
                        .put("access_token", issued.accessToken())
                        .put("token_type", "Bearer")
                        .put("expires_in", issued.expiresIn().toSeconds())
                        .put("refresh_token", issued.refreshToken())),
                () -> Responses.error(request, 400, "invalid_grant", "The code or refresh token is not valid"));
    }

    /** Who the Client says it is: from HTTP Basic if it sent that (RFC 6749, section 2.3.1), else from the form. */
    private static Optional<ClientCredentials> credentials(RoutingContext request, MultiMap form) {
        String authorization = request.request().getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, BASIC, 0, BASIC.length())) {
            return basicCredentials(authorization.substring(BASIC.length()));
        }
        return Optional.ofNullable(form.get("client_id"))
                .map(clientId -> new ClientCredentials(clientId, Optional.ofNullable(form.get("client_secret"))));
    }

    private static Optional<ClientCredentials> basicCredentials(String encoded) {
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(encoded.strip()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            return Optional.empty();
        }
        String clientId = URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8);
        String secret = URLDecoder.decode(decoded.substring(colon + 1), StandardCharsets.UTF_8);
        return Optional.of(new ClientCredentials(clientId, Optional.of(secret)));
    }
}
