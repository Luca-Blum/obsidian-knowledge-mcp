# Links are stored as written and resolved at query time

The index stores each Link only as the text the author wrote, together with its kind and its heading anchor. It resolves the Link against the current set of Notes when `related` is called, using Obsidian's rules: the path suffix or file name is matched without regard to case, and more than one match is reported as an Ambiguous Link instead of being guessed. We chose this so that creating, renaming or deleting one Note never forces other Notes to be re-indexed. Replacement stays limited to the Note that changed, and Backlinks and Unresolved Links stay correct without a graph-wide update.

## Considered Options

- **Resolve Links at index time and store edges between Notes.** This makes queries cheaper. Any Note that is created or renamed could change how Links in other Notes resolve, though, so every such change would need a cascade over the whole graph.
