package net.neoforged.meta.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

@Entity
public class SoftwareComponentReleaseNotes {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(unique = true, nullable = false)
    private SoftwareComponentVersion componentVersion;

    @Column(nullable = false)
    private String originalText;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    private String markdown;

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public SoftwareComponentVersion getComponentVersion() {
        return componentVersion;
    }

    public void setComponentVersion(SoftwareComponentVersion componentVersion) {
        this.componentVersion = componentVersion;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }

    public String getHtml() {
        var parser = Parser.builder().build();
        var document = parser.parse(markdown);
        return HtmlRenderer.builder().build().render(document);
    }
}
