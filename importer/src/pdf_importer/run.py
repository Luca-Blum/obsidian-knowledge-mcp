"""One importer run: every PDF in every Topic's _inbox/ becomes a Source Note."""

import datetime
import logging
import os
import tempfile
from pathlib import Path

import yaml

from pdf_importer.conversion import Converter
from pdf_importer.language import detect_language

log = logging.getLogger(__name__)


def run(vaults: list[Path]) -> None:
    pdfs = [pdf for vault in vaults for pdf in _inbox_pdfs(vault)]
    if not pdfs:
        return
    converter = Converter()
    for pdf in pdfs:
        _import_pdf(pdf, converter)


def _inbox_pdfs(vault: Path) -> list[Path]:
    """The PDFs directly inside `<Topic>/_inbox/` of every Topic of the Vault."""
    topics = [
        folder
        for folder in sorted(vault.iterdir())
        if folder.is_dir() and not folder.name.startswith(("_", "."))
    ]
    return [
        candidate
        for topic in topics
        if (topic / "_inbox").is_dir()
        for candidate in sorted((topic / "_inbox").iterdir())
        if candidate.is_file() and candidate.suffix.lower() == ".pdf"
    ]


def _import_pdf(pdf: Path, converter: Converter) -> None:
    topic = pdf.parent.parent
    converted = converter.convert(pdf)
    frontmatter: dict[str, object] = {"type": "source", "title": converted.title}
    if lang := detect_language(converted.markdown):
        frontmatter["lang"] = lang
    frontmatter["source"] = f"[[_sources/{pdf.name}]]"
    frontmatter["imported"] = datetime.date.today()

    note = topic / "Source-Notes" / f"{pdf.stem}.md"
    header = yaml.safe_dump(frontmatter, sort_keys=False, allow_unicode=True)
    _write_atomically(note, f"---\n{header}---\n\n{converted.markdown}\n")
    _move(pdf, topic / "_sources" / pdf.name)
    log.info("imported %s as %s", pdf, note)


def _write_atomically(target: Path, text: str) -> None:
    """Writes a temporary file next to the target and moves it into place."""
    target.parent.mkdir(exist_ok=True)
    with tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", dir=target.parent, prefix=".", suffix=".tmp", delete=False
    ) as temporary:
        temporary.write(text)
        temporary.flush()
        os.fsync(temporary.fileno())
    try:
        _move(Path(temporary.name), target)
    finally:
        Path(temporary.name).unlink(missing_ok=True)


def _move(source: Path, target: Path) -> None:
    """Moves a file within the Vault, failing instead of replacing the target."""
    target.parent.mkdir(exist_ok=True)
    os.link(source, target)
    os.unlink(source)
