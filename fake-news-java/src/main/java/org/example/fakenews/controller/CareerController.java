package org.example.fakenews.controller;

import org.example.fakenews.service.CareerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/career")
public class CareerController {

    @Autowired
    private CareerService careerService;

    @GetMapping("/{stream}")
    public Map<String, Object> getCareer(@PathVariable("stream") String stream) {
        return careerService.getCareerDetails(stream);
    }
}