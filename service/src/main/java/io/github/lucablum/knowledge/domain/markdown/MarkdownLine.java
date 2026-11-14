package io.github.lucablum.knowledge.domain.markdown;

/**
 * One line of a Note's Markdown, either a heading that opens a section or text that belongs to one; a code block
 * counts as one line, as it is never split.
 */
public sealed interface MarkdownLine permits HeadingLine, TextLine, CodeBlock {
}
