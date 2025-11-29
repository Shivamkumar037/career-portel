package org.example.fakenews.service;

import com.opencsv.CSVReader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.util.*;

@Service
public class CareerService {

    public Map<String, Object> getCareerDetails(String streamChoice) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, List<String>> yearWiseData = new LinkedHashMap<>();
        String courseDuration = "";

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new ClassPathResource("career_data.csv").getInputStream()))) {

            String[] values;
            boolean headerSkipped = false;

            while ((values = reader.readNext()) != null) {
                if (!headerSkipped) {
                    headerSkipped = true; // skip header row
                    continue;
                }

                if (values.length < 5) {
                    continue; // skip invalid rows
                }

                String stream = values[0].trim().toUpperCase();
                String duration = values[1].trim();
                String year = values[2].trim();
                String subjects = values[3].trim();
                String projects = values[4].trim();

                if (stream.equals(streamChoice.toUpperCase())) {
                    courseDuration = duration;
                    yearWiseData.put(year, Arrays.asList(subjects, projects));
                }
            }

            if (yearWiseData.isEmpty()) {
                response.put("error", "No data found for stream: " + streamChoice.toUpperCase());
            } else {
                response.put("stream", streamChoice.toUpperCase());
                response.put("courseDuration", courseDuration);
                response.put("details", yearWiseData);
            }

        } catch (Exception e) {
            response.put("error", "Error reading CSV file: " + e.getMessage());
        }

        return response;
    }
}