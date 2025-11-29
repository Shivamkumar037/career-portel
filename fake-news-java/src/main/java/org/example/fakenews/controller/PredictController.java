package org.example.fakenews.controller;

import org.example.fakenews.model.NewsRequest;
import org.example.fakenews.model.PredictionResponse;
import org.example.fakenews.service.ModelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/predict")
public class PredictController {

    private final ModelService modelService;

    public PredictController(ModelService modelService) {
        this.modelService = modelService;
    }

    @PostMapping
    public ResponseEntity<?> predict(@RequestBody NewsRequest request) {
        try {
            if (request.getText() == null || request.getText().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\":\"No text provided\"}");
            }
            ModelService.Prediction p = modelService.predict(request.getText());
            return ResponseEntity.ok(new PredictionResponse(p.label, p.score));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"error\":\"Prediction failed: " + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/")
    public  String hel(){
        return "helllo shiva ";
    }
}