package com.netralabs.report.pac;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PAC's {@code documentInformation} block: metadata sourced from the PDF itself
 * (title, author, language, tag count, file size, ...).
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({
        "pageCount", "title", "subject", "author", "keywords",
        "creationDate", "modificationDate", "creator", "producer",
        "language", "ids", "isTagged", "reportLanguage",
        "numberOfTags", "sizeInKb"
})
public class DocumentInformationDTO {
    private Integer pageCount;
    private String title;
    private String subject;
    private String author;
    private String keywords;
    private String creationDate;
    private String modificationDate;
    private String creator;
    private String producer;
    private String language;
    private String ids;
    private Boolean isTagged;
    private String reportLanguage;
    private Integer numberOfTags;
    private Long sizeInKb;
}
