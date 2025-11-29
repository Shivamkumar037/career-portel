package org.example.fakenews.model;

public class PredictionResponse {
    private String label;
    private Double score;

    public PredictionResponse() {}

    public PredictionResponse(String label, Double score) {
        this.label = label;
        this.score = score;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
}