# Ports are interfaces, and every architectural role is marked with a jMolecules annotation

The service is a hexagon with the packages `domain`, `application` and `adapter`, each split further by concept (ADR 0004). Each type states its role with a jMolecules annotation, and `ArchitectureTest` enforces the roles. Names carry the concept, not the role, so there are no `Port`, `Repository` or `Reader` suffixes.

| Role | Annotation | Form | Example |
|---|---|---|---|
| Primary port: what the outside may ask of the core | `@PrimaryPort` | interface in `application` | `SearchChunks`, `IndexVault` |
| Application: the use case behind a primary port | `@Application` | class in `application`, named after how it works | `VectorSearch`, `ContentHashReconciliation` |
| Secondary port: what the core needs from the outside | `@SecondaryPort` | interface in `application` | `IndexedNotes`, `ChunkVectors`, `Catalog`, `Embedding`, `Vault` |
| Primary adapter: turns an outside event into a call of a primary port | `@PrimaryAdapter` | class in `adapter` | `SearchTool`, `ScheduledReconciliation` |
| Secondary adapter: implements a secondary port with a technology | `@SecondaryAdapter` | class in `adapter`, named after the technology | `PostgresIndex`, `OllamaEmbedding`, `FileSystemVault` |
| Value object | `@ValueObject` | record in `domain` (or `EmbeddedChunk`) | `NotePath`, `Chunk`, `HeadingLine` |
| Domain service | `@Service` | stateless public class in `domain` | `Splitter` |

A primary adapter depends on the primary port and never on the class that implements it. We first marked the concrete use case classes as `@PrimaryPort` to get the jMolecules rule green. That merged the port with the application behind it and hid the boundary the rule is meant to protect.

## Consequences

- A use case is an interface plus an implementation, even when there is only one. This is the price of having the adapter depend on the abstraction, and it lets adapter tests replace the core.
- Wiring happens in `adapter.config.UseCases`, which returns the interface type.
- `ArchitectureTest` checks that ports are interfaces, that every interface in `application` is a port, that every concrete class there is an `@Application` implementing a `@PrimaryPort`, that every adapter is marked, and the dependency directions through `JMoleculesArchitectureRules.ensureHexagonal()`.
- Utility classes (private constructor, statics only, such as `VaultRules`) and package-private builders (`SectionAssembler`) carry no annotation, because no jMolecules stereotype fits and a forced label would be wrong. `everyPublicConceptOfTheDomainHasADddStereotype` therefore covers public, instantiable top-level types in `domain` only.
- Every public top-level type in `adapter` outside `adapter.config` has to be a primary or secondary adapter. Helpers that are package-private (`Statements`, `Transaction`) and the wiring in `adapter.config` (`UseCases`, `CollectionConfig`) are not checked. A new public helper type belongs in `adapter.config` or has to become package-private.
