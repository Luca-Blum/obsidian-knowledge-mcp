package io.github.lucablum.knowledge.application.access;

import java.net.URI;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/** What a Client asks for when it registers itself: a name to show on the consent page, and where codes go. */
@ValueObject
public record ClientRegistration(String name, List<URI> redirectUris, boolean wantsSecret) {
}
