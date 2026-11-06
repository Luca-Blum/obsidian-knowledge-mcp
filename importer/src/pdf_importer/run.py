"""One importer run: every PDF in every Topic's _inbox/ becomes a Source Note."""

import contextlib
import datetime
import fcntl
import itertools
import logging
import os
import tempfile
import time
import traceback
from collections.abc import Iterator
from pathlib import Path

import yaml

from pdf_importer.conversion import Converter
from pdf_importer.language import detect_language

log = logging.getLogger(__name__)


SETTLE_SECONDS = 5


def run(vaults: list[Path]) -> None:
    for vault in vaults:
        try:
            with _locked(vault) as acquired:
                if not acquired:
                    log.warning("skipping %s: another run holds it", vault)
                    continue
                _import_vault(vault)
        except OSError:
            log.exception("could not import from %s", vault)


@contextlib.contextmanager
def _locked(vault: Path) -> Iterator[bool]:
    """Holds an exclusive lock on the Vault folder itself, so the lock leaves no file in the Vault."""
    descriptor = os.open(vault, os.O_RDONLY)
    try:
        try:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            yield False
            return
        yield True
    finally:
        os.close(descriptor)


def _import_vault(vault: Path) -> None:
    pdfs = _settled(_inbox_pdfs(vault))
    if not pdfs:
        return
    try:
        converter = Converter()
    except Exception:
        log.exception("could not start the converter")
        return
    for pdf in pdfs:
        try:
            _import_pdf(pdf, converter)
        except Exception:
            log.exception("failed to import %s", pdf)
            _fail(pdf, traceback.format_exc())


def _settled(pdfs: list[Path]) -> list[Path]:
    """The PDFs whose size and modification time have not changed for a few seconds.

    A PDF that changed recently may still be being copied. Comparing two looks
    a moment apart also catches a copy that keeps the original modification time.
    """
    first = {pdf: _fingerprint(pdf) for pdf in pdfs}
    if first:
        time.sleep(1)
    settled = []
    for pdf, before in first.items():
        after = _fingerprint(pdf)
        if after is None or after != before or time.time() - after[1] < SETTLE_SECONDS:
            log.info("skipping %s: changed a moment ago", pdf)
        else:
            settled.append(pdf)
    return settled


def _fingerprint(pdf: Path) -> tuple[int, float] | None:
    try:
        stat = pdf.stat()
    except FileNotFoundError:
        return None
    return stat.st_size, stat.st_mtime


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
    name = _free_name(pdf.stem, [(topic / "Source-Notes", ".md"), (topic / "_sources", ".pdf")])
    frontmatter: dict[str, object] = {"type": "source", "title": converted.title}
    if lang := detect_language(converted.markdown):
        frontmatter["lang"] = lang
    frontmatter["source"] = f"[[_sources/{name}.pdf]]"
    frontmatter["imported"] = datetime.date.today()

    note = topic / "Source-Notes" / f"{name}.md"
    header = yaml.safe_dump(frontmatter, sort_keys=False, allow_unicode=True)
    _write_atomically(note, f"---\n{header}---\n\n{converted.markdown}\n")
    try:
        _move(pdf, topic / "_sources" / f"{name}.pdf")
    except Exception:
        note.unlink(missing_ok=True)
        raise
    log.info("imported %s as %s", pdf, note)


def _fail(pdf: Path, explanation: str) -> None:
    """Moves the PDF to `_inbox/_failed/` and writes a log with the same base name next to it."""
    failed = pdf.parent / "_failed"
    try:
        name = _free_name(pdf.stem, [(failed, ".pdf"), (failed, ".log")])
        log_file = failed / f"{name}.log"
        _write_atomically(log_file, explanation)
        try:
            _move(pdf, failed / f"{name}.pdf")
        except Exception:
            log_file.unlink(missing_ok=True)
            raise
    except Exception:
        log.exception("could not move %s to %s", pdf, failed)


def _free_name(stem: str, places: list[tuple[Path, str]]) -> str:
    """The stem, or the stem with the smallest numeric suffix, that is free in every place."""
    candidates = itertools.chain([stem], (f"{stem}-{number}" for number in itertools.count(1)))
    return next(
        name
        for name in candidates
        if not any((folder / f"{name}{extension}").exists() for folder, extension in places)
    )


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
