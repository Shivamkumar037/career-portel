package org.example.fakenews.model;

public class NewsRequest {
    private String text;

    public NewsRequest() {}

    public NewsRequest(String text) {
        this.text = text;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}