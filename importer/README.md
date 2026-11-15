# PDF importer

Turns the documents in a Topic's `_inbox/` into Source Notes. It runs on the Mac, converts each file with [Docling](https://github.com/docling-project/docling) (OCR included for PDFs) and writes the Note to the Topic's `Source-Notes/` folder. The original then moves to the Topic's `_sources/` folder.

## Supported formats

| Format | Extension | Title |
| --- | --- | --- |
| PDF | `.pdf` | metadata title, else first heading, else file name |
| EPUB | `.epub` | first heading, else file name |
| Word | `.docx`, `.doc` | first heading, else file name |
| OpenDocument text | `.odt` | first heading, else file name |
| Rich Text | `.rtf` | first heading, else file name |
| PowerPoint | `.pptx`, `.ppt` | first slide title, else file name |
| OpenDocument presentation | `.odp` | first slide title, else file name |
| HTML | `.html` | first heading, else file name (not the `<title>` tag) |
| MHTML web archive | `.mhtml` | first heading, else file name |
| AsciiDoc | `.adoc` | first heading, else file name |
| LaTeX | `.tex` | first heading, else file name |
| JATS XML | `.xml` | article title, else file name |
| Markdown | `.md` | existing `title`, else first heading, else file name |
| Plain text | `.txt` | file name |

`.doc`, `.rtf` and `.ppt` are converted through LibreOffice, so they need it installed (`brew install --cask libreoffice`). An `.xml` file that is not JATS is not guessed at: it moves to `_inbox/_failed/` with a log, like a corrupt file. So does an `.html`, `.mhtml`, `.adoc`, `.tex`, `.md` or `.txt` file that contains binary data, and a `.md` whose frontmatter is not valid YAML. A `.doc` can lose the structure of its tables on the way. An EPUB whose chapters mark headings with styled paragraphs instead of heading tags gets the file name as its Title and no headings in the text.

The importer is a Python project managed with [uv](https://docs.astral.sh/uv/) and is independent of the Maven build. It shares nothing with the knowledge service except the frontmatter format of a Source Note.

## What a run does

A run looks at every Topic of every Vault it is given, meaning every first-level folder that is not prefixed with `_` or `.`. Only files with an accepted extension (the table above, listed in `conversion.py`) directly inside `<Topic>/_inbox/` are imported, and everything else is left alone. For each file the run:

1. converts it to Markdown with headings and tables,
2. writes `<Topic>/Source-Notes/<file name>.md` atomically: a temporary file first, then linked into place,
3. moves the original to `<Topic>/_sources/`.

## What a run never does

- It never opens an existing Note for writing. If `Source-Notes/` or `_sources/` already holds the name, the new Note and the new original both get the same numeric suffix (`name-1.md`, `name-1.pdf`; the original keeps its own extension), and the `source` link points at the suffixed original.
- If a file cannot be converted, or cannot be moved to `_sources/` after its Note was written (the Note is then deleted again), the file moves to `_inbox/_failed/` and a log with the same base name explains the error. A name taken there gets a suffix too.
- One failing file does not stop the others.
- It skips a file that changed within the last 5 seconds, so a file still being copied is picked up by a later run.
- It holds a lock on the Vault folder while it works. A second run on the same Vault skips it and leaves everything as it is.
- It never touches `_inbox/_failed/`, other subfolders or files with other extensions.

`Source-Notes/` and `_sources/` are created when they are missing. The Note starts with this frontmatter:

```yaml
---
type: source
title: Notes on the Dependency Rule
lang: en
source: '[[_sources/dependency-rule.pdf]]'
imported: 2026-11-06
---
```

- `title` follows the Title column of the table above.
- `lang` is `de` or `en` when the offline detector ([Lingua](https://github.com/pemistahl/lingua-py)) is confident. It is left out otherwise, and the Collection's default applies.
- `topic`, `published`, `author` and `tags` are never written. `published`, `author` and `tags` are for you to add by hand.

### Markdown and text files

A `.md` and a `.txt` skip Docling: the text becomes the Note body, apart from trailing blank lines. A `.txt` is never parsed for frontmatter, so its Title is the file name. A `.md` that already has frontmatter keeps every value in it. A `title` that is empty or not text counts as missing. The importer only adds the fields that are missing (`title`, `lang`, `source`, `imported`), and `type: source` is always set, replacing any other `type`, because otherwise the file would not be a Source Note.

## Install

You need uv (`brew install uv`). Then, from this directory:

```sh
uv sync
```

This installs Python and the pinned dependencies from `uv.lock`, Docling included.

Docling downloads its layout, table and OCR models to its default cache the first time it converts a PDF. To fetch them ahead of time:

```sh
uv run docling-tools models download
```

## Run

```sh
uv run pdf-importer --vault ~/Obsidian/Engineering --vault ~/Obsidian/Training
```

Each file is logged to stderr with its outcome and, for a converted or failed file, how long it took:

```text
2026-11-06 14:02:11,512 INFO converted /…/Coaching/_inbox/dependency-rule.pdf as /…/Coaching/Source-Notes/dependency-rule.md in 8.3s
2026-11-06 14:02:12,044 ERROR failed /…/Coaching/_inbox/broken.pdf in 0.4s
2026-11-06 14:02:13,100 INFO skipped /…/Coaching/_inbox/half-copied.pdf: changed a moment ago
```

## Run in the background

A `launchd` agent starts the importer about once a minute after login. Each run scans every Topic's `_inbox/` in the Vaults it is given, so a new Topic needs no change here. The repository holds only a template, `launchd/io.github.lucablum.pdf-importer.plist`. The Vault paths, the interval and the log location are placeholders that you fill in when you install it, so they live in `~/Library/LaunchAgents/`, outside the code.

### Download Docling's models first

Do this once before installing the agent. Otherwise the first background import downloads several hundred MB of models while it runs, and a slow or offline Mac makes that import fail:

```sh
uv run docling-tools models download
```

The models land in Docling's default cache and are shared by the background runs, because the agent runs as you.

### Install the agent

From this directory:

```sh
mkdir -p ~/Library/Logs/pdf-importer
sed \
  -e "s|__UV__|$(command -v uv)|" \
  -e "s|__IMPORTER_DIR__|$PWD|" \
  -e "s|__VAULT_1__|$HOME/Obsidian/Engineering|" \
  -e "s|__VAULT_2__|$HOME/Obsidian/Training|" \
  -e "s|__INTERVAL_SECONDS__|60|" \
  -e "s|__LOG_FILE__|$HOME/Library/Logs/pdf-importer/importer.log|g" \
  launchd/io.github.lucablum.pdf-importer.plist \
  > ~/Library/LaunchAgents/io.github.lucablum.pdf-importer.plist
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/io.github.lucablum.pdf-importer.plist
```

Change the two Vault paths, the interval or the log file in those `sed` lines to suit. A third Vault needs another `--vault` pair in the plist. To change anything later, edit the installed plist, then `launchctl bootout gui/$(id -u)/io.github.lucablum.pdf-importer` and bootstrap again. To remove the agent, boot it out and delete the plist.

The template sets `DOCLING_DEVICE=cpu`. Docling's default on Apple Silicon is the GPU (`mps`), and there a larger PDF crashed the whole run with a segfault (exit code 139) and stayed in `_inbox/` without a log, so every following run crashed on it again. On the CPU the same 5 MB PDF converts in about 70 seconds. Remove the variable from the plist if you want to try the GPU.

### Check that it works

1. Drop a PDF into `<Vault>/<Topic>/_inbox/`.
2. Wait one interval, plus the time the conversion takes.
3. Check that `<Topic>/Source-Notes/` holds the Note and `_sources/` the PDF, and that the log shows the file as converted:

```sh
tail -f ~/Library/Logs/pdf-importer/importer.log
```

If nothing happens, `launchctl print gui/$(id -u)/io.github.lucablum.pdf-importer` shows the agent's state and last exit code.

### Retry a failed file

A failed file waits in `<Topic>/_inbox/_failed/` with a `.log` next to it that holds the error. Fix the cause if there is one, then move the file back into `<Topic>/_inbox/`. The next run picks it up. Delete the old `.log` once you no longer need it.

## Tests

The tests build a temporary Vault, run the importer once with real Docling and check the files it leaves behind:

```sh
uv run pytest
```

CI runs them on every pull request and caches Docling's models between runs. Locally, the first run downloads the models and takes about a minute longer.

`tests/fixtures/sample.pdf` is generated by `tests/fixtures/make_sample_pdf.py`. Run that script only to change the fixture.

The other fixtures (`sample.docx`, `.doc`, `.odt`, `.rtf`, `.epub` and `headless.docx`) are generated by `tests/fixtures/make_sample_documents.py`, which needs LibreOffice. The slide and markup fixtures (`sample.pptx`, `.ppt`, `.odp`, `.html`, `.mhtml`, `.adoc`, `.tex`, `.xml` and `not-jats.xml`) come from `tests/fixtures/make_sample_markup.py`, which needs LibreOffice too.
