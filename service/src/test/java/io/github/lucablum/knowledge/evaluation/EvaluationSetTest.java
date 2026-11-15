package io.github.lucablum.knowledge.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lucablum.knowledge.domain.note.NotePath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationSetTest {

    @TempDir
    Path folder;

    @Test
    void whenRead_givenQuestionsAndNegativeQuestions_thenReturnBoth() throws IOException {
        Path file = write("""
                questions:
                  - question: What does the Dependency Rule say?
                    category: definition
                    topic: Software-Engineering
                    expected:
                      - Software-Engineering/Source-Notes/clean-architecture-ch5.md
                  - question: How do I run a kata?
                    category: multi-note
                    expected: [Coaching/Kata.md, Coaching/Dojo.md]
                negative-questions:
                  - question: How should I taper before a marathon?
                    category: other-collection
                """);

        EvaluationSet set = EvaluationSet.read(file);

        assertThat(set.questions()).containsExactly(
                new Question("What does the Dependency Rule say?", Category.DEFINITION, "Software-Engineering",
                        Set.of(new NotePath("Software-Engineering/Source-Notes/clean-architecture-ch5.md"))),
                new Question("How do I run a kata?", Category.MULTI_NOTE, null,
                        Set.of(new NotePath("Coaching/Kata.md"), new NotePath("Coaching/Dojo.md"))));
        assertThat(set.negativeQuestions()).containsExactly(new NegativeQuestion(
                "How should I taper before a marathon?", NegativeKind.OTHER_COLLECTION, null));
    }

    @Test
    void whenRead_givenKeywords_thenQuestionsAndNegativeQuestionsCarryThem() throws IOException {
        Path file = write("""
                questions:
                  - question: How is the index stored?
                    category: definition
                    keywords: PostgresIndex pgvector
                    expected: [Software-Engineering/Index.md]
                negative-questions:
                  - question: How should I taper before a marathon?
                    category: near-miss
                    keywords: Tapering
                """);

        EvaluationSet set = EvaluationSet.read(file);

        assertThat(set.questions()).containsExactly(new Question("How is the index stored?", Category.DEFINITION,
                null, "PostgresIndex pgvector", Set.of(new NotePath("Software-Engineering/Index.md"))));
        assertThat(set.negativeQuestions()).containsExactly(new NegativeQuestion(
                "How should I taper before a marathon?", NegativeKind.NEAR_MISS, null, "Tapering"));
    }

    @Test
    void whenRead_givenQuestionWithoutExpectedNotes_thenRejectNamingTheQuestion() throws IOException {
        Path file = write("""
                questions:
                  - question: What does the Dependency Rule say?
                    category: definition
                """);

        assertThatThrownBy(() -> EvaluationSet.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("What does the Dependency Rule say?")
                .hasMessageContaining("expected");
    }

    @Test
    void whenRead_givenQuestionWithoutCategory_thenRejectNamingTheQuestion() throws IOException {
        Path file = write("""
                questions:
                  - question: What does the Dependency Rule say?
                    expected: [Software-Engineering/Index.md]
                """);

        assertThatThrownBy(() -> EvaluationSet.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("What does the Dependency Rule say?")
                .hasMessageContaining("category");
    }

    @Test
    void whenRead_givenNegativeQuestionWithoutCategory_thenRejectNamingTheQuestion() throws IOException {
        Path file = write("""
                negative-questions:
                  - question: How should I taper before a marathon?
                """);

        assertThatThrownBy(() -> EvaluationSet.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("How should I taper before a marathon?")
                .hasMessageContaining("category");
    }

    @Test
    void whenRead_givenUnknownCategory_thenRejectNamingIt() throws IOException {
        Path file = write("""
                questions:
                  - question: What does the Dependency Rule say?
                    category: trivia
                    expected: [Software-Engineering/Index.md]
                """);

        assertThatThrownBy(() -> EvaluationSet.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trivia");
    }

    @Test
    void whenRead_givenNegativeQuestionWithQuestionCategory_thenRejectNamingIt() throws IOException {
        Path file = write("""
                negative-questions:
                  - question: How should I taper before a marathon?
                    category: definition
                """);

        assertThatThrownBy(() -> EvaluationSet.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definition");
    }

    @Test
    void whenRead_givenNegativeQuestionWithExpectedNotes_thenReject() throws IOException {
        Path file = write("""
                negative-questions:
                  - question: How should I taper before a marathon?
                    category: near-miss
                    expected: [Training/Taper.md]
                """);

        assertThatThrownBy(() -> EvaluationSet.read(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whenFindMissingNotes_givenExpectedNoteNotInVault_thenReturnIt() {
        EvaluationSet set = new EvaluationSet(List.of(
                new Question("How do I run a kata?", Category.DEFINITION, null,
                        Set.of(new NotePath("Coaching/Kata.md"))),
                new Question("What is a dojo?", Category.DEFINITION, null,
                        Set.of(new NotePath("Coaching/Dojo (renamed).md")))), List.of());

        Set<NotePath> missing = set.missingNotes(List.of(new NotePath("Coaching/Kata.md"), new NotePath("Index.md")));

        assertThat(missing).containsExactly(new NotePath("Coaching/Dojo (renamed).md"));
    }

    private Path write(String content) throws IOException {
        return Files.writeString(folder.resolve("questions.yaml"), content);
    }
}
