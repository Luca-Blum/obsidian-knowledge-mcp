# Obsidian Knowledge

A self-hosted knowledge base built from Obsidian vaults. Claude searches it for the places in its owner's notes and sources that bear on a question. Retrieval happens here; Claude writes the answer.

## Language

### Collections and notes

**Collection**:
An isolated knowledge base fed by exactly one Vault. It exists so that queries return relevant results and so that each Collection can be configured on its own, not as an access-control boundary.
_Avoid_: Knowledge base, index, instance

**Vault**:
The Obsidian folder a Collection is built from. It is the only source of truth and is never written to by the knowledge service.
_Avoid_: Repository, source folder

**Topic**:
The first folder below the Vault root that contains a Note. Deeper folders inherit it, and a Note directly in the Vault root has no Topic.
_Avoid_: Thema, category, subject

**Note**:
A Markdown file in a Vault that is not inside a folder prefixed with `_` or `.`. A Note is identified by its path relative to the Vault root and is always replaced as a whole. Claude can read only Notes that are indexed.
_Avoid_: Document, page, file

**Note Kind**:
Whether a Note is a Source Note or an Own Note. There are exactly these two kinds.
_Avoid_: Type, note type

**Source Note**:
A Note whose frontmatter says `type: source`. The kind belongs to the Note, not to its location: a Note in `Source-Notes/` without that type is still an Own Note.
_Avoid_: Quellen-Note, source, reference

**Own Note**:
Any Note that is not a Source Note, whatever else its `type` says.
_Avoid_: Eigene Note, personal note

**Title**:
The `title` from a Note's frontmatter, or its file name without `.md` if there is none; a Note's own first heading is never used. A Source Note gets its Title once, when it is imported: the PDF's title, else the PDF's first heading, else the PDF's file name.

**Imported Date**:
The day a Source Note was created from a PDF.

**Published Date**:
When the work behind a Source Note was published, given as a year, a year and month, or a full date. It is optional and set by hand.
_Avoid_: Date

**Note Language**:
The language a Note is written in, taken from its frontmatter `lang` or else from the Collection's default.

### Retrieval

**Chunk**:
A section of a Note's content that can be searched on its own, with its heading path as context. Small neighbouring sections are merged into one Chunk and a large section is split into several, which repeat a little of each other's text. Its identity is only its position in the current version of the Note.
_Avoid_: Passage, snippet, segment

**Search Hit**:
A Chunk returned for one particular query, together with the Note it belongs to, its heading path, the Note Kind and a score. The score only means something relative to that query.
_Avoid_: Passage, result, match

**Reconciliation**:
Comparing every Note in the Vault with the index and re-indexing only the Notes that differ. Reconciliation is what keeps the index correct. Watching for file changes only makes updates faster.
_Avoid_: Sync, rescan

**Re-Index**:
Rebuilding a Collection's whole index from its Vault, needed whenever the embedding model or the chunking rules change. While it runs the Collection answers no searches.
_Avoid_: Rebuild, reindexing

**Minimum Similarity**:
The semantic closeness a Chunk must reach to count as relevant to a query at all. It belongs to an embedding model and is calibrated per Collection with Negative Questions.
_Avoid_: Minimum score, threshold

### Links

**Link**:
A reference written in a Note, kept as the text the author wrote and resolved only when it is read. Kinds are a note link, an embed and a file link (for example the `source:` field that points to a PDF).
_Avoid_: Edge, reference

**Backlink**:
A Link in another Note that resolves to the Note being looked at.

**Unresolved Link**:
A Link whose target matches no Note or file in the Vault.
_Avoid_: Broken link, dangling link

**Ambiguous Link**:
A Link whose target matches more than one Note. It is reported as ambiguous instead of being guessed.

### Evaluation

**Evaluation Set**:
The real questions for one Collection, each paired with the Notes expected to answer it. Answers are judged at Note level, not Chunk level.
_Avoid_: Test set, benchmark

**Negative Question**:
A question in an Evaluation Set that the Collection must not answer, such as a training question asked of the engineering Collection.
