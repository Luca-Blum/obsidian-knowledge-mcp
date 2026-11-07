"""Converts a source file to Markdown with Docling and picks its Title."""

import logging
import re
import shutil
from collections.abc import Callable
from dataclasses import dataclass, field
from pathlib import Path

import pypdfium2
import yaml
from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import PdfPipelineOptions
from docling.document_converter import DocumentConverter, PdfFormatOption
from docling_core.types.doc.document import DoclingDocument
from docling_core.types.doc.items.text import SectionHeaderItem, TitleItem

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Converted:
    markdown: str
    title: str
    frontmatter: dict[str, object] = field(default_factory=dict)


class Converter:
    def __init__(self) -> None:
        options = PdfPipelineOptions(do_ocr=True, do_table_structure=True)
        self._docling = DocumentConverter(
            format_options={InputFormat.PDF: PdfFormatOption(pipeline_options=options)}
        )

    def convert(self, source: Path) -> Converted:
        _require_signature(source)
        if source.suffix.lower() in _PLAIN_TEXT_FORMATS:
            return _plain_text(source)
        document = self._docling.convert(source).document
        return Converted(document.export_to_markdown(), _TITLE_RULES[source.suffix.lower()](source, document))


def _pdf_title(pdf: Path, document: DoclingDocument) -> str:
    """The PDF's metadata title, else the first heading, else the file name."""
    pdf_file = pypdfium2.PdfDocument(pdf)
    try:
        metadata_title = pdf_file.get_metadata_value("Title").strip()
    finally:
        pdf_file.close()
    if metadata_title:
        return metadata_title
    return _heading_title(pdf, document)


def _heading_title(file: Path, document: DoclingDocument) -> str:
    """The first heading, else the file name."""
    for item, _ in document.iterate_items():
        if isinstance(item, (TitleItem, SectionHeaderItem)) and item.text.strip():
            return item.text.strip()
    return file.stem


# LibreOffice reads any bytes as text, so Docling accepts garbage in these formats without complaint.
_SIGNATURES = {
    ".doc": b"\xd0\xcf\x11\xe0",
    ".ppt": b"\xd0\xcf\x11\xe0",
    ".rtf": b"{\\rtf",
}


# Docling's text parsers tolerate anything, so binary content has to be turned away first.
_TEXT_FORMATS = frozenset({".html", ".mhtml", ".adoc", ".tex", ".md", ".txt"})
_BINARY_SNIFF_BYTES = 8192


def _require_signature(source: Path) -> None:
    extension = source.suffix.lower()
    signature = _SIGNATURES.get(extension)
    if signature is not None:
        with source.open("rb") as file:
            if file.read(len(signature)) != signature:
                raise ValueError(f"{source.name} is not a valid {extension} file")
    if extension in _TEXT_FORMATS:
        with source.open("rb") as file:
            if b"\x00" in file.read(_BINARY_SNIFF_BYTES):
                raise ValueError(f"{source.name} is not a text file")


# Markdown and text are taken over as they are, without Docling.
_PLAIN_TEXT_FORMATS = frozenset({".md", ".txt"})
_FRONTMATTER = re.compile(r"\A---[ \t]*\r?\n(?:(.*?)\r?\n)?---[ \t]*(?:\r?\n|\Z)", re.DOTALL)
_HEADING = re.compile(r"^ {0,3}#{1,6}[ \t]+(.+?)(?:[ \t]+#+)?[ \t]*$")
_FENCE = re.compile(r"^ {0,3}(```|~~~)")


def _plain_text(source: Path) -> Converted:
    """A `.md` keeps the frontmatter it has. A `.txt` is all body, titled by its file name."""
    text = source.read_text(encoding="utf-8-sig")
    if source.suffix.lower() == ".txt":
        return Converted(text, source.stem)
    existing: dict[str, object] = {}
    if match := _FRONTMATTER.match(text):
        loaded = yaml.safe_load(match.group(1) or "")
        if loaded is not None and not isinstance(loaded, dict):
            raise ValueError(f"the frontmatter of {source.name} is not a mapping")
        existing = loaded or {}
        text = text[match.end():]
    title = existing.get("title")
    if not (isinstance(title, str) and title.strip()):
        existing.pop("title", None)
        title = _first_heading(text) or source.stem
    return Converted(text, title, existing)


def _first_heading(markdown: str) -> str | None:
    fence = None
    for line in markdown.splitlines():
        if opening := _FENCE.match(line):
            fence = None if fence == opening.group(1) else fence or opening.group(1)
        elif fence is None and (heading := _HEADING.match(line)):
            return heading.group(1)
    return None


# How each accepted format picks its Title. Adding a format is one line here plus a test,
# and a signature in _SIGNATURES when the format has one and LibreOffice would otherwise read garbage as text,
# or _TEXT_FORMATS when its parser accepts any bytes.
_TITLE_RULES: dict[str, Callable[[Path, DoclingDocument], str]] = {
    ".pdf": _pdf_title,
    ".epub": _heading_title,
    ".docx": _heading_title,
    ".doc": _heading_title,
    ".odt": _heading_title,
    ".rtf": _heading_title,
    ".pptx": _heading_title,
    ".ppt": _heading_title,
    ".odp": _heading_title,
    ".html": _heading_title,
    ".mhtml": _heading_title,
    ".adoc": _heading_title,
    ".tex": _heading_title,
    ".xml": _heading_title,
}

_NEEDS_LIBREOFFICE = frozenset({".doc", ".rtf", ".ppt"})


def accepted_extensions() -> frozenset[str]:
    """The extensions a run imports. Without LibreOffice the legacy formats stay in the inbox."""
    if shutil.which("soffice"):
        return frozenset(_TITLE_RULES) | _PLAIN_TEXT_FORMATS
    log.warning("LibreOffice (soffice) not found: leaving .doc, .rtf and .ppt files in the inbox")
    return (frozenset(_TITLE_RULES) - _NEEDS_LIBREOFFICE) | _PLAIN_TEXT_FORMATS
