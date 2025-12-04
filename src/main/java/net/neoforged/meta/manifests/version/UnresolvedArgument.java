package net.neoforged.meta.manifests.version;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.ser.std.StdSerializer;

import java.util.List;

@JsonSerialize(using = UnresolvedArgument.Serializer.class)
@JsonDeserialize(using = UnresolvedArgument.Deserializer.class)
public sealed interface UnresolvedArgument {

    class Serializer extends StdSerializer<UnresolvedArgument> {
        protected Serializer() {
            super(UnresolvedArgument.class);
        }

        @Override
        public void serialize(UnresolvedArgument value, JsonGenerator gen, SerializationContext provider) throws JacksonException {
            switch (value) {
                case ConditionalValue conditionalValue -> provider.writeValue(gen, conditionalValue);
                case Value(String stringValue) -> gen.writeString(stringValue);
                case null -> gen.writeNull();
            }
        }
    }

    class Deserializer extends StdDeserializer<UnresolvedArgument> {
        protected Deserializer() {
            super(UnresolvedArgument.class);
        }

        @Override
        public UnresolvedArgument deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            JsonToken token = p.currentToken();

            if (token == JsonToken.VALUE_NULL) {
                return null;
            } else if (token == JsonToken.VALUE_STRING) {
                return new Value(p.getValueAsString());
            } else if (token == JsonToken.START_OBJECT) {
                // Parse as tree to handle value normalization
                var obj = (tools.jackson.databind.node.ObjectNode) p.readValueAsTree();

                // Coerce value into an array to make parsing easier later
                if (obj.has("value") && obj.get("value").isString()) {
                    var currentValue = obj.get("value");
                    obj.putArray("value").add(currentValue);
                }

                // Manually deserialize ConditionalValue to avoid infinite recursion
                // (readTreeAsValue would trigger this deserializer again)
                List<String> values = ctxt.readTreeAsValue(obj.get("value"),
                        ctxt.getTypeFactory().constructCollectionType(List.class, String.class));
                List<Rule> rules = ctxt.readTreeAsValue(obj.get("rules"),
                        ctxt.getTypeFactory().constructCollectionType(List.class, Rule.class));

                return new ConditionalValue(values, rules);
            }

            throw ctxt.wrongTokenException(p, UnresolvedArgument.class, JsonToken.START_OBJECT, "Expected string, null or object.");
        }
    }

    record Value(String value) implements UnresolvedArgument {
    }

    record ConditionalValue(List<String> value, List<Rule> rules) implements UnresolvedArgument {
    }
}
