"""Converts a PDF to Markdown with Docling and picks its Title."""

from dataclasses import dataclass
from pathlib import Path

import pypdfium2
from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import PdfPipelineOptions
from docling.document_converter import DocumentConverter, PdfFormatOption
from docling_core.types.doc.document import DoclingDocument
from docling_core.types.doc.items.text import SectionHeaderItem, TitleItem


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
        document = self._docling.convert(pdf).document
        return Converted(document.export_to_markdown(), _title(pdf, document))


def _title(pdf: Path, document: DoclingDocument) -> str:
    """The PDF's metadata title, else the first heading, else the file name."""
    pdf_file = pypdfium2.PdfDocument(pdf)
    try:
        metadata_title = pdf_file.get_metadata_value("Title").strip()
    finally:
        pdf_file.close()
    if metadata_title:
        return metadata_title
    for item, _ in document.iterate_items():
        if isinstance(item, (TitleItem, SectionHeaderItem)) and item.text.strip():
            return item.text.strip()
    return pdf.stem
