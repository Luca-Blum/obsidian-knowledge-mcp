"""One importer run: every source file in every Topic's _inbox/ becomes a Source Note."""

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

from pdf_importer.conversion import Converter, accepted_extensions
from pdf_importer.language import detect_language

log = logging.getLogger(__name__)


SETTLE_SECONDS = 5


def run(vaults: list[Path]) -> None:
    for vault in vaults:
        try:
            with _locked(vault) as acquired:
                if not acquired:
                    log.warning("skipped %s: another run holds it", vault)
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
    files = _settled(_inbox_files(vault))
    if not files:
        return
    try:
        converter = Converter()
    except Exception:
        log.exception("could not start the converter")
        return
    for file in files:
        started = time.monotonic()
        try:
            note = _import_file(file, converter)
            log.info("converted %s as %s in %.1fs", file, note, time.monotonic() - started)
        except Exception:
            log.exception("failed %s in %.1fs", file, time.monotonic() - started)
            _fail(file, traceback.format_exc())


def _settled(files: list[Path]) -> list[Path]:
    """The files whose size and modification time have not changed for a few seconds.

    A file that changed recently may still be being copied. Comparing two looks
    a moment apart also catches a copy that keeps the original modification time.
    """
    first = {file: _fingerprint(file) for file in files}
    if first:
        time.sleep(1)
    settled = []
    for file, before in first.items():
        after = _fingerprint(file)
        if after is None or after != before or time.time() - after[1] < SETTLE_SECONDS:
            log.info("skipped %s: changed a moment ago", file)
        else:
            settled.append(file)
    return settled


def _fingerprint(file: Path) -> tuple[int, float] | None:
    try:
        stat = file.stat()
    except FileNotFoundError:
        return None
    return stat.st_size, stat.st_mtime


def _inbox_files(vault: Path) -> list[Path]:
    """The source files directly inside `<Topic>/_inbox/` of every Topic of the Vault."""
    topics = [
        folder
        for folder in sorted(vault.iterdir())
        if folder.is_dir() and not folder.name.startswith(("_", "."))
    ]
    accepted = accepted_extensions()
    return [
        candidate
        for topic in topics
        if (topic / "_inbox").is_dir()
        for candidate in sorted((topic / "_inbox").iterdir())
        if candidate.is_file() and candidate.suffix.lower() in accepted
    ]


def _import_file(file: Path, converter: Converter) -> Path:
    topic = file.parent.parent
    converted = converter.convert(file)
    name = _free_name(file.stem, [(topic / "Source-Notes", ".md"), (topic / "_sources", file.suffix)])
    frontmatter: dict[str, object] = {"type": "source"}
    frontmatter.update((key, value) for key, value in converted.frontmatter.items() if key != "type")
    frontmatter.setdefault("title", converted.title)
    if "lang" not in frontmatter and (lang := detect_language(converted.markdown)):
        frontmatter["lang"] = lang
    frontmatter.setdefault("source", f"[[_sources/{name}{file.suffix}]]")
    frontmatter.setdefault("imported", datetime.date.today())

    note = topic / "Source-Notes" / f"{name}.md"
    header = yaml.safe_dump(frontmatter, sort_keys=False, allow_unicode=True)
    body = converted.markdown.rstrip("\n")
    _write_atomically(note, f"---\n{header}---\n\n{body}\n")
    try:
        _move(file, topic / "_sources" / f"{name}{file.suffix}")
    except Exception:
        note.unlink(missing_ok=True)
        raise
    return note


def _fail(file: Path, explanation: str) -> None:
    """Moves the file to `_inbox/_failed/` and writes a log with the same base name next to it."""
    failed = file.parent / "_failed"
    try:
        name = _free_name(file.stem, [(failed, file.suffix), (failed, ".log")])
        log_file = failed / f"{name}.log"
        _write_atomically(log_file, explanation)
        try:
            _move(file, failed / f"{name}{file.suffix}")
        except Exception:
            log_file.unlink(missing_ok=True)
            raise
    except Exception:
        log.exception("could not move %s to %s", file, failed)


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
