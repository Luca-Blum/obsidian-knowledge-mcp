package io.github.lucablum.knowledge.domain.chunk;

/** One line of a Note's Markdown, either a heading that opens a section or text that belongs to one; a code block counts as one. */
sealed interface MarkdownLine permits HeadingLine, TextLine, CodeBlock {
}
