package net.neoforged.meta.extract;

import net.neoforged.meta.config.SoftwareComponentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangelogExtractorTest {
    static final String FIRST_CHANGELOG_ENTRY = """
            Redo conditional recipe datagen (#250)
            
            - Remove `ConditionalRecipeBuilder`.
            - Add `RecipeOutput.withConditions(...)` to add conditions to recipes.
            - Use `dispatchUnsafe` for the attachment codec to avoid nesting values
              when it's unnecessary.""";
    byte[] changelogBody;
    byte[] oldStyleChangelogBody;

    @BeforeEach
    void setUp() throws IOException {
        try (var in = ChangelogExtractorTest.class.getResourceAsStream("/neoforge-20.2.39-beta-changelog.txt")) {
            changelogBody = in.readAllBytes();
        }
        try (var in = ChangelogExtractorTest.class.getResourceAsStream("/forge-1.20.1-47.1.56-changelog.txt")) {
            oldStyleChangelogBody = in.readAllBytes();
        }
    }

    @Test
    void testExtractMultiLine() {
        var changelogEntry = ChangelogExtractor.extract(changelogBody);
        assertEquals(FIRST_CHANGELOG_ENTRY, changelogEntry);
    }

    @Test
    void testExtractMultiLineFromOldStyle() {
        var changelogEntry = ChangelogExtractor.extract(oldStyleChangelogBody);
        assertEquals("Add an AbstractWidget#onClick extension that provides the clicked button as context to the method (#49)", changelogEntry);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            'Tweak actions for PRs (#217)'|'Tweak actions for PRs ([#217](https://github.com/neoforge/NeoForge/issues/217))'
            '[20.2] Registry rework (#257)

            The Blogpost is available here: https://neoforged.net/news/20.2registry-rework/

            ---------

            Co-authored-by: Minecraftschurli <minecraftschurli@gmail.com>
            Co-authored-by: Dennis C <xfacthd@gmx.de>
            Co-authored-by: Technici4n <13494793+Technici4n@users.noreply.github.com>'|'Registry rework ([#257](https://github.com/neoforge/NeoForge/issues/257))
            
            The Blogpost is available here: [https://neoforged.net/news/20.2registry-rework/](https://neoforged.net/news/20.2registry-rework/)'
            'fixing [MC-73186](https://bugs.mojang.com/browse/MC-73186) for these models.'|'fixing [MC-73186](https://bugs.mojang.com/browse/MC-73186) for these models.'
            """, delimiter = '|')
    void testExtractMarkdownWithGithubRepository(String original, String markdown) {
        var properties = new SoftwareComponentProperties();
        properties.setGithubRepository("neoforge/NeoForge");
        assertEquals(markdown, ChangelogExtractor.extractMarkdown(properties, original));
    }
}
