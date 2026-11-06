# Minimum Similarity is applied before rank fusion, not to the fused score

Hybrid search fuses the vector list and the full-text list with Reciprocal Rank Fusion. The relevance cut-off is a cosine-similarity threshold applied to the vector list before the fusion. The fused score only sets the order of the Search Hits. RRF scores depend only on ranks, so the best candidate for an off-topic question scores about the same as the best candidate for an on-topic one, and a threshold on the fused score cannot tell them apart. Full-text hits are not filtered by this threshold. They are already restricted by matching the query's terms against a stemmed, stop-word-aware configuration.

## Consequences

- The threshold belongs to the embedding model, because cosine values are not comparable across models. It is calibrated per Collection with Negative Questions and has to be calibrated again after every model change.
