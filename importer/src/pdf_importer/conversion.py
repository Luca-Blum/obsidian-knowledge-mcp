"""Converts a source file to Markdown with Docling and picks its Title."""

import logging
import shutil
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

import pypdfium2
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


class Converter:
    def __init__(self) -> None:
        options = PdfPipelineOptions(do_ocr=True, do_table_structure=True)
        self._docling = DocumentConverter(
            format_options={InputFormat.PDF: PdfFormatOption(pipeline_options=options)}
        )

    def convert(self, source: Path) -> Converted:
        _require_signature(source)
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
    ".rtf": b"{\\rtf",
}


def _require_signature(source: Path) -> None:
    signature = _SIGNATURES.get(source.suffix.lower())
    if signature is None:
        return
    with source.open("rb") as file:
        if file.read(len(signature)) != signature:
            raise ValueError(f"{source.name} is not a valid {source.suffix.lower()} file")


# How each accepted format picks its Title. Adding a format is one line here plus a test,
# and a signature in _SIGNATURES when the format has one and LibreOffice would otherwise read garbage as text.
_TITLE_RULES: dict[str, Callable[[Path, DoclingDocument], str]] = {
    ".pdf": _pdf_title,
    ".epub": _heading_title,
    ".docx": _heading_title,
    ".doc": _heading_title,
    ".odt": _heading_title,
    ".rtf": _heading_title,
}

_NEEDS_LIBREOFFICE = frozenset({".doc", ".rtf"})


def accepted_extensions() -> frozenset[str]:
    """The extensions a run imports. Without LibreOffice the legacy formats stay in the inbox."""
    if shutil.which("soffice"):
        return frozenset(_TITLE_RULES)
    log.warning("LibreOffice (soffice) not found: leaving .doc and .rtf files in the inbox")
    return frozenset(_TITLE_RULES) - _NEEDS_LIBREOFFICE
