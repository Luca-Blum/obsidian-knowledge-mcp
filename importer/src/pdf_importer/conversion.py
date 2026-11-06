"""Converts a PDF to Markdown with Docling and picks its Title."""

import re
from dataclasses import dataclass
from pathlib import Path

import pypdfium2
from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import PdfPipelineOptions
from docling.document_converter import DocumentConverter, PdfFormatOption

_HEADING = re.compile(r"^#+\s+(.+?)\s*$", re.MULTILINE)


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

    def convert(self, pdf: Path) -> Converted:
        markdown = self._docling.convert(pdf).document.export_to_markdown()
        return Converted(markdown, _title(pdf, markdown))


def _title(pdf: Path, markdown: str) -> str:
    """The PDF's metadata title, else the first heading, else the file name."""
    document = pypdfium2.PdfDocument(pdf)
    try:
        metadata_title = document.get_metadata_value("Title").strip()
    finally:
        document.close()
    if metadata_title:
        return metadata_title
    if heading := _HEADING.search(markdown):
        return heading.group(1)
    return pdf.stem
