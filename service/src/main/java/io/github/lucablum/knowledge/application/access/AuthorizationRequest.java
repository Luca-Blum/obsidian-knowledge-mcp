package io.github.lucablum.knowledge.application.access;

import io.github.lucablum.knowledge.domain.access.CodeChallenge;
import java.net.URI;
import org.jmolecules.ddd.annotation.ValueObject;

/** A Client asking for a code: which Client, where to send the code, and the PKCE challenge to bind it to. */
@ValueObject
public record AuthorizationRequest(String clientId, URI redirectUri, CodeChallenge challenge) {
}
