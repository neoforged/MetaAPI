package net.neoforged.meta.manifests.version;


import com.fasterxml.jackson.annotation.JsonValue;

enum RuleAction {
    ALLOWED("allow"),
    DISALLOWED("disallow");

    private final String value;

    RuleAction(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    boolean isAllowed() {
        return ALLOWED == this;
    }

    boolean isDisallowed() {
        return DISALLOWED == this;
    }
}
