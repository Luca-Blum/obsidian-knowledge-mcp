package io.github.lucablum.knowledge.adapter.http.access;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/** The JSON answers of the OAuth endpoints, errors in the shape of RFC 6749, section 5.2. */
final class Responses {

    private Responses() {
    }

    /** Tokens and secrets travel in these answers, so no cache may keep them. */
    static void json(RoutingContext request, int status, JsonObject body) {
        request.response()
                .setStatusCode(status)
                .putHeader("Content-Type", "application/json")
                .putHeader("Cache-Control", "no-store")
                .putHeader("Pragma", "no-cache")
                .end(body.encode());
    }

    static void error(RoutingContext request, int status, String error, String description) {
        json(request, status, new JsonObject().put("error", error).put("error_description", description));
    }
}
