package io.github.lucablum.knowledge.adapter.http.access;

import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The page on which the User lets a Client into the Collection by typing the passphrase. The Client's name comes from
 * whoever registered it, so everything shown is escaped, and the page may not be framed by another site.
 */
final class ConsentPage {

    private static final String PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>%s</title>
            <style>
            body { font-family: system-ui, sans-serif; max-width: 26rem; margin: 4rem auto; padding: 0 1rem; }
            input[type=password] { width: 100%%; padding: .5rem; box-sizing: border-box; }
            button { margin-top: 1rem; padding: .5rem 1rem; }
            .error { color: #b00020; }
            </style>
            </head>
            <body>
            %s
            </body>
            </html>
            """;

    private static final String SECURITY_POLICY = "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'";

    private ConsentPage() {
    }

    /** The form, which posts the request's fields back together with the passphrase. */
    static void show(RoutingContext request, int status, String collection, String client,
            Map<String, String> fields, String error) {
        String hidden = fields.entrySet().stream()
                .map(field -> "<input type=\"hidden\" name=\"%s\" value=\"%s\">"
                        .formatted(escape(field.getKey()), escape(field.getValue())))
                .collect(Collectors.joining("\n"));
        String body = """
                <h1>Connect %s</h1>
                <p><strong>%s</strong> asks to search and read the %s Collection.</p>
                %s
                <form method="post" action="%s">
                %s
                <label for="passphrase">Enter the Collection's passphrase to allow it:</label>
                <input type="password" id="passphrase" name="passphrase" autocomplete="current-password" autofocus>
                <button type="submit">Allow</button>
                </form>
                """.formatted(escape(collection), escape(client), escape(collection),
                error == null ? "" : "<p class=\"error\">" + escape(error) + "</p>", OAuthEndpoints.AUTHORIZE, hidden);
        send(request, status, "Connect " + collection, body);
    }

    /** For a request that cannot be sent back to the Client, because the redirect is not one it registered. */
    static void showError(RoutingContext request, int status, String message) {
        send(request, status, "Cannot connect", "<h1>Cannot connect</h1>\n<p>" + escape(message) + "</p>");
    }

    private static void send(RoutingContext request, int status, String title, String body) {
        request.response()
                .setStatusCode(status)
                .putHeader("Content-Type", "text/html; charset=utf-8")
                .putHeader("Cache-Control", "no-store")
                .putHeader("X-Frame-Options", "DENY")
                .putHeader("Content-Security-Policy", SECURITY_POLICY)
                .putHeader("Referrer-Policy", "no-referrer")
                .end(PAGE.formatted(escape(title), body));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
