package com.commengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PostCommentRequest {
    @NotBlank(message = "text is required (max 300 chars)")
    @Size(max = 300)
    private String text;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
