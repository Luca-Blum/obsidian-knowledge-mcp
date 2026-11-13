package io.github.lucablum.knowledge.application.indexing;

import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** How the Collection's index was built, as a Re-Index reads and renews it. */
@SecondaryPort
public interface IndexBuild {

    /** The fingerprint of the last completed build, or none if a build never completed. */
    Optional<IndexFingerprint> fingerprint();

    /**
     * Forgets the fingerprint, removes every Chunk, makes room for vectors of {@code dimension} and marks every Note
     * as changed, so the next Reconciliation indexes all of them again. Notes keep their text and Links meanwhile.
     */
    void startOver(int dimension);

    /** Records that every Note was indexed with {@code fingerprint}. */
    void complete(IndexFingerprint fingerprint);
}
