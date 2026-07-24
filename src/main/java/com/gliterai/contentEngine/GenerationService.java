package com.gliterai.contentEngine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GenerationService {
    private final JobRepository jobRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String apiKey;

    public GenerationService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Async
    public void processGenerationJob(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));

        try{
            job.setStatus("processing");
            jobRepository.save(job);

            String llmInstruction  = "Convert this product description into a detailed prompt for image generation. Product: " + job.getProductName()+", Description: " + job.getDescription();

            String generatedPrompt = callGeminiAPI(llmInstruction);
            job.setPrompt(generatedPrompt);

            Thread.sleep(5000); // Simulate processing time

            job.setProductImgUrl("img.png");
            job.setStatus("completed");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Job processing interrupted", e);
        } finally{
            jobRepository.save(job);
        }
    }
    private String callGeminiAPI(String promptText) {
        String url = "https://api.gemini.com/api/v1/products/" + apiKey + "/images/";

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts",List.of(
                                Map.of("text",promptText))
                ))
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try{
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            Map<String, Object> body = response.getBody();
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) body.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");
        }catch (Exception e){
            System.err.println("Error calling Gemini API: " + e.getMessage());
            return "High quality photo of "+promptText;
        }
    }
}
