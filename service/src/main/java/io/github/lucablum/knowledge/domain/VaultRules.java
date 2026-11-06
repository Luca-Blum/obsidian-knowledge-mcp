package io.github.lucablum.knowledge.domain;

/** Which files in a Vault are Notes: only {@code .md} files outside folders prefixed with {@code _} or {@code .}. */
public final class VaultRules {

    private VaultRules() {}

    public static boolean skipsFolder(String folderName) {
        return folderName.startsWith("_") || folderName.startsWith(".");
    }

    public static boolean isNoteFile(String fileName) {
        return fileName.endsWith(".md");
    }
}
