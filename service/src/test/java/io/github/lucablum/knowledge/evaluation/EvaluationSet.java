package io.github.lucablum.knowledge.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.note.Topic;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The real questions for one Collection, each with its expected Notes, plus its Negative Questions. It lives in the
 * Collection's Vault, never in the repository, and is read from a YAML file:
 *
 * <pre>
 * questions:
 *   - question: What does the Dependency Rule say?
 *     topic: Software-Engineering            # optional
 *     keywords: Dependency Rule              # optional, exact terms for the full-text search
 *     expected:
 *       - Software-Engineering/Source-Notes/clean-architecture-ch5.md
 * negative-questions:
 *   - question: How should I taper before a marathon?
 *     keywords: Tapering                     # optional, as above
 * </pre>
 */
record EvaluationSet(List<Question> questions, List<NegativeQuestion> negativeQuestions) {

    EvaluationSet {
        questions = List.copyOf(questions);
        negativeQuestions = List.copyOf(negativeQuestions);
    }

    /** The expected Notes that are not among {@code vaultNotes}, such as a misspelt or renamed path. */
    Set<NotePath> missingNotes(Collection<NotePath> vaultNotes) {
        Set<NotePath> missing = questions.stream()
                .flatMap(question -> question.expected().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        missing.removeAll(Set.copyOf(vaultNotes));
        return missing;
    }

    /** @throws IllegalArgumentException if the file does not follow the format or a question has no expected Note */
    static EvaluationSet read(Path file) {
        YamlFile parsed;
        try {
            parsed = new YAMLMapper().readValue(file.toFile(), YamlFile.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Evaluation Set " + file + " is malformed: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new EvaluationSet(
                parsed.questions().stream().map(entry -> entry.toQuestion(file)).toList(),
                parsed.negativeQuestions().stream().map(NegativeEntry::toNegativeQuestion).toList());
    }

    private static Topic topicNamed(String name) {
        return name == null || name.isBlank() ? null : new Topic(name);
    }

    private record YamlFile(List<Entry> questions, @JsonProperty("negative-questions") List<NegativeEntry> negativeQuestions) {

        YamlFile {
            questions = Objects.requireNonNullElse(questions, List.of());
            negativeQuestions = Objects.requireNonNullElse(negativeQuestions, List.of());
        }
    }

    private record Entry(String question, String topic, String keywords, List<String> expected) {

        Question toQuestion(Path file) {
            if (expected == null || expected.isEmpty()) {
                throw new IllegalArgumentException("Question \"%s\" in %s names no expected Note"
                        .formatted(question, file));
            }
            return new Question(question, topicNamed(topic), keywords, expected.stream().map(NotePath::new)
                    .collect(Collectors.toSet()));
        }
    }

    private record NegativeEntry(String question, String topic, String keywords) {

        NegativeQuestion toNegativeQuestion() {
            return new NegativeQuestion(question, topicNamed(topic), keywords);
        }
    }
}
