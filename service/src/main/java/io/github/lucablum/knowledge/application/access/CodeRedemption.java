package io.github.lucablum.knowledge.application.access;

import java.net.URI;
import org.jmolecules.ddd.annotation.ValueObject;

/** A Client trading a code for tokens: the code, who it is, the redirect it asked with, and the PKCE verifier. */
@ValueObject
public record CodeRedemption(String code, ClientCredentials client, URI redirectUri, String verifier) {

    @Override
    public String toString() {
        return "CodeRedemption[client=%s]".formatted(client.clientId());
    }
}
