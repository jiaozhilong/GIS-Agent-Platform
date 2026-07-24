package com.gisagent.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class PptTemplateDto {
    private Long id;
    private String name;
    private String description;
    private Long fileSize;
    private Boolean isDefault;
    private Instant createdAt;
    private Instant updatedAt;
}
