package net.neoforged.meta.manifests.version;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.exc.JsonNodeException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.io.IOException;
import java.util.Map;

public class RuleDeserializer extends StdDeserializer<Rule> {
    public RuleDeserializer() {
        super(Rule.class);
    }

    @Override
    public Rule deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        JsonNode node = p.readValueAsTree();

        if (!node.isObject()) {
            throw ctxt.wrongTokenException(p, Rule.class, JsonToken.START_OBJECT, "Rule must be an object.");
        }

        RuleAction action = ctxt.readTreeAsValue(node.get("action"), RuleAction.class);
        Map<String, Boolean> features = null;
        if (node.has("features")) {
            JsonParser featuresParser = node.get("features").traverse(ctxt);
            featuresParser.nextToken(); // Move to START_OBJECT
            features = featuresParser.readValueAs(new TypeReference<Map<String, Boolean>>() {
            });
        }
        OsCondition os = node.has("os") ? ctxt.readTreeAsValue(node.get("os"), OsCondition.class) : null;

        return new Rule(action, features, os);
    }
}
