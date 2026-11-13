# Ideas

Possibilities that are not tickets yet. Each entry says why it waits and what would make it worth another look. When an idea becomes a ticket, link the issue here and move the entry out. When it becomes a decision, record it as an ADR in `docs/adr/`.

## Describe pictures with a larger model

**Idea:** Docling can replace the `<!-- image -->` placeholder of a picture with a generated description (`do_picture_description`). Pointing it at a larger local vision model through `PictureDescriptionApiOptions` (LM Studio or Ollama) might read the labels in diagrams.

**Why it waits:** An experiment with Docling's default model, SmolVLM-256M, on the clean-architecture diagram of Clean Architecture (PDF pages 229-230) produced only "In this image, we can see a diagram. In the diagram, we can see the labels of different things." and leaked the model's `<end_of_utterance>` token into the Note. The surrounding text of the book already explains most diagrams, and a wrong description would stand in a Source Note like book text. A larger model has not been tried because no local vision model is installed.

**Look again when:** The Evaluation Set (#4) contains questions that only a diagram can answer, or a source depends on charts or tables that exist only as pictures. Try a larger model on those sources only, and mark any description clearly as generated.

## Page number in the picture placeholder

**Idea:** Replace the bare `<!-- image -->` with a visible hint that names the PDF page, for example `> [Figure on page 229 of the source PDF]`. Docling records the page of every picture, so the importer could read it per picture. Consecutive pictures collapse into one hint. Formats without pages (EPUB, DOCX) keep a hint without a page.

**Why it waits:** The AI Agent can already tell from the placeholder that a picture was there, and the PDF behind the Note's `source` link holds the picture for a human. There is no case yet where the missing page number hurt.

**Look again when:** Reading a Chunk regularly leaves a question about a picture, or the idea below gets built, because it needs the page. Note that the page is the PDF's page, not the number printed in the book.

## MCP tool that returns a PDF page

**Idea:** A tool in the knowledge service that returns one page of the PDF behind a Source Note, so the AI Agent can look at a picture itself. It follows the Note's `source` link and needs the PDF on the Pi, which Syncthing already carries there.

**Why it waits:** It depends on the page number in the placeholder (previous idea), and there is no evidence yet that the AI Agent needs pictures to answer from the Vaults. It also touches the knowledge service's API and its read-only handling of Vaults.

**Look again when:** The page number in the placeholder exists and the Evaluation Set or everyday use shows answers that fail for lack of a picture.
