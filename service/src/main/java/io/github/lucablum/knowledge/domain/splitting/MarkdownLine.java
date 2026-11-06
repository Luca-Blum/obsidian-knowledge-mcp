package io.github.lucablum.knowledge.domain.splitting;

/** One line of a Note's Markdown, either a heading that opens a section or text that belongs to one. */
sealed interface MarkdownLine permits HeadingLine, TextLine {
}
