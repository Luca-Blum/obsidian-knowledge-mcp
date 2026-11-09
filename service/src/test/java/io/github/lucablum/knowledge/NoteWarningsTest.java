package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.application.indexing.ContentHashReconciliation;
import io.github.lucablum.knowledge.application.indexing.IndexVault;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Indexing reports what looks wrong in a Note as a warning and never corrects it. */
@QuarkusTest
class NoteWarningsTest {

    private final Logger logger = Logger.getLogger(ContentHashReconciliation.class.getName());
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.add(new SimpleFormatter().formatMessage(record));
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    @Inject
    TestVault vault;

    @Inject
    IndexVault indexVault;

    @BeforeEach
    void captureWarnings() {
        vault.clear();
        logger.addHandler(capture);
    }

    @AfterEach
    void stopCapturing() {
        logger.removeHandler(capture);
    }

    @Test
    void whenIndexVault_givenNoteInSourceNotesFolderWithoutTypeSource_thenWarnAboutIt() {
        vault.write("Running/Source-Notes/Daniels.md", "---\ntype: summary\n---\ntext");
        indexVault.run();

        assertThat(warnings).singleElement().asString()
                .contains("Running/Source-Notes/Daniels.md")
                .contains("Source-Notes/");
    }

    @Test
    void whenIndexVault_givenValidNotesWithAnyType_thenWarnAboutNothing() {
        vault.write("Running/Source-Notes/Daniels.md", """
                ---
                type: source
                title: Daniels' Running Formula
                imported: 2026-01-05
                published: 2021-03
                lang: de
                ---
                text""");
        vault.write("Running/Idea.md", "---\ntype: idea\n---\ntext");
        vault.write("Running/Plain.md", "text");
        indexVault.run();

        assertThat(warnings).isEmpty();
    }

    @Test
    void whenIndexVault_givenInvalidDates_thenWarnAboutEach() {
        vault.write("Running/Daniels.md", "---\ntype: source\nimported: yesterday\npublished: 2021-13\n---\ntext");
        indexVault.run();

        assertThat(warnings).hasSize(2)
                .anySatisfy(warning -> assertThat(warning).contains("imported").contains("yesterday"))
                .anySatisfy(warning -> assertThat(warning).contains("published").contains("2021-13"));
    }

    @Test
    void whenIndexVault_givenUnknownLang_thenWarnAboutIt() {
        vault.write("Running/Daniels.md", "---\nlang: klingon\n---\ntext");
        indexVault.run();

        assertThat(warnings).singleElement().asString().contains("lang").contains("klingon");
    }

    @Test
    void whenIndexVault_givenFrontmatterThatIsNotYaml_thenWarnAndIndexTheNoteAnyway() {
        vault.write("Running/Broken.md", "---\ntitle: [unclosed\n---\ntext");
        int indexed = indexVault.run().indexed();

        assertThat(indexed).isEqualTo(1);
        assertThat(warnings).singleElement().asString().contains("Running/Broken.md").contains("YAML");
    }
}
