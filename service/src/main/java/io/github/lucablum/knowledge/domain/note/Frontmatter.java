package io.github.lucablum.knowledge.domain.note;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * The YAML block between a first line and the next line of {@code ---}; unclosed, it is just text. Only its
 * top-level text values are kept, exactly as written: YAML would otherwise turn {@code 2021-03-14} into a timestamp.
 */
@ValueObject
public record Frontmatter(Map<String, String> fields) {

    private static final Pattern FENCE = Pattern.compile("---[ \\t]*");

    public Frontmatter {
        fields = Map.copyOf(fields);
    }

    /**
     * @throws IllegalArgumentException if the block is not a YAML mapping
     */
    public static Frontmatter of(String markdown) {
        List<String> lines = List.of(markdown.split("\\R", -1));
        int bodyStart = bodyStart(lines);
        if (bodyStart == 0) {
            return new Frontmatter(Map.of());
        }
        return parse(String.join("\n", lines.subList(1, bodyStart - 1)));
    }

    /** The index of the first line after the frontmatter, {@code 0} if there is none. */
    public static int bodyStart(List<String> lines) {
        if (!isFence(lines.getFirst())) {
            return 0;
        }
        for (int i = 1; i < lines.size(); i++) {
            if (isFence(lines.get(i))) {
                return i + 1;
            }
        }
        return 0;
    }

    /** The value of {@code key}, empty if it is missing, blank, null or not plain text (such as a list). */
    public Optional<String> get(String key) {
        return Optional.ofNullable(fields.get(key)).map(String::strip).filter(value -> !value.isEmpty());
    }

    private static Frontmatter parse(String yaml) {
        Node root;
        try {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).compose(new StringReader(yaml));
        } catch (YAMLException e) {
            throw new IllegalArgumentException("Frontmatter is not valid YAML: " + e.getMessage(), e);
        }
        if (root == null) {
            return new Frontmatter(Map.of());
        }
        if (!(root instanceof MappingNode mapping)) {
            throw new IllegalArgumentException("Frontmatter is not a mapping of keys to values");
        }
        Map<String, String> fields = new HashMap<>();
        for (NodeTuple tuple : mapping.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode key
                    && tuple.getValueNode() instanceof ScalarNode value
                    && !value.getTag().equals(Tag.NULL)) {
                fields.putIfAbsent(key.getValue(), value.getValue());
            }
        }
        return new Frontmatter(fields);
    }

    private static boolean isFence(String line) {
        return FENCE.matcher(line).matches();
    }
}
