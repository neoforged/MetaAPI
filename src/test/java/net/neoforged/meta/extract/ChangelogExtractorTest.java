package net.neoforged.meta.extract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChangelogExtractorTest {
    byte[] changelogBody;

    @BeforeEach
    void setUp() throws IOException {
        try (var in = ChangelogExtractorTest.class.getResourceAsStream("/neoforge-20.2.39-beta-changelog.txt")) {
            changelogBody = in.readAllBytes();
        }
    }

    @Test
    void testExtractMultiLine() {
        var changelogEntry = ChangelogExtractor.extract(changelogBody, "20.2.39-beta");
        assertEquals("""
                Redo conditional recipe datagen (#250)
                
                - Remove `ConditionalRecipeBuilder`.
                - Add `RecipeOutput.withConditions(...)` to add conditions to recipes.
                - Use `dispatchUnsafe` for the attachment codec to avoid nesting values
                  when it's unnecessary.""", changelogEntry);
    }

    @Test
    void testExtractMissingVersion() {
        var changelogEntry = ChangelogExtractor.extract(changelogBody, "20.2.999");
        assertNull(changelogEntry);
    }

    @Test
    void testExtractLastVersionInFile() {
        var changelogEntry = ChangelogExtractor.extract(changelogBody, "20.2.0-beta");
        assertEquals("Bump AT and CoreMods", changelogEntry);
    }
}
