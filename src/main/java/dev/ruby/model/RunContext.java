package dev.ruby.model;

public record RunContext(
    String branch,
    String sha
) {
    public String shortSha() {
        if (sha == null || sha.length() < 7)
            return sha;
        return sha.substring(0, 7);
    }
}
