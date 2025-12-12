package net.neoforged.meta.extract;

import org.junit.jupiter.api.Test;
import tools.jackson.core.PrettyPrinter;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerArgFileExtractorTest {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            // we use this to make the diff easier to read (one array item per line)
            .defaultPrettyPrinter(makePrettyPrinter())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private static PrettyPrinter makePrettyPrinter() {
        var prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        return prettyPrinter;
    }

    @Test
    void test204() throws IOException {
        var unixArgFile = readTestFile("unix_args_20.4.txt");
        var windowsArgFile = readTestFile("windows_args_20.4.txt");

        String expectedString = readTestFile("expected_args_20.4.json");
        var expected = MAPPER.writeValueAsString(MAPPER.readTree(expectedString));
        var consolidated = ServerArgFileExtractor.consolidateServerArgs(unixArgFile, windowsArgFile);
        var actualSerialized = MAPPER.writeValueAsString(consolidated);
        assertEquals(expected, actualSerialized);
    }

    private String readTestFile(String filename) throws IOException {
        try (var in = ServerArgFileExtractorTest.class.getResourceAsStream("/" + filename)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
