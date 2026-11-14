package io.github.lucablum.knowledge.adapter.http.access;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * The two documents a Client reads first: which authorization server guards the MCP endpoint (RFC 9728), and where
 * that server's endpoints are (RFC 8414). They come from configuration alone and call no port.
 */
@ApplicationScoped
class OAuthDiscovery {

    private final CollectionConfig collection;

    OAuthDiscovery(CollectionConfig collection) {
        this.collection = collection;
    }

    void register(@Observes Router router) {
        OAuthEndpoints.of(collection).ifPresent(endpoints -> {
            JsonObject resource = protectedResource(endpoints);
            JsonObject server = authorizationServer(endpoints);
            router.get(OAuthEndpoints.RESOURCE_METADATA).handler(request -> Responses.json(request, 200, resource));
            router.get(OAuthEndpoints.RESOURCE_METADATA_FOR_MCP)
                    .handler(request -> Responses.json(request, 200, resource));
            router.get(OAuthEndpoints.AUTHORIZATION_SERVER_METADATA)
                    .handler(request -> Responses.json(request, 200, server));
        });
    }

    private static JsonObject protectedResource(OAuthEndpoints endpoints) {
        return new JsonObject()
                .put("resource", endpoints.resource())
                .put("authorization_servers", new JsonArray().add(endpoints.issuer()))
                .put("bearer_methods_supported", new JsonArray().add("header"));
    }

    private static JsonObject authorizationServer(OAuthEndpoints endpoints) {
        return new JsonObject()
                .put("issuer", endpoints.issuer())
                .put("authorization_endpoint", endpoints.url(OAuthEndpoints.AUTHORIZE))
                .put("token_endpoint", endpoints.url(OAuthEndpoints.TOKEN))
                .put("registration_endpoint", endpoints.url(OAuthEndpoints.REGISTER))
                .put("response_types_supported", new JsonArray().add("code"))
                .put("grant_types_supported", new JsonArray().add("authorization_code").add("refresh_token"))
                .put("code_challenge_methods_supported", new JsonArray().add("S256"))
                .put("token_endpoint_auth_methods_supported", new JsonArray(ClientRegistrationRoute.AUTH_METHODS));
    }
}
