package com.gliterai.contentEngine;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class GenerateController {

    private final JobRepository jobRepository;
    private final GenerationService generationService;

    public GenerateController(JobRepository jobRepository, GenerationService generationService) {
        this.jobRepository = jobRepository;
        this.generationService = generationService;
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestBody GenerateRequest request) {

        Job job = new Job();
        job.setProductName(request.getProductName());
        job.setProductDescription(request.getProductDescription());
        job.setStatus("pending");
        job =  jobRepository.save(job);

        generationService.processGenerationJob(job.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", job.getId());
        response.put("status", "pending");
        response.put("message","Job queued successfully");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable UUID id) {
        return jobRepository.findById(id).map(job -> {)
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", job.getId());
            response.put("status", job.getStatus());
            if("completed".equals(job.getStatus()) && job.getResultImgUrl() != null)
                response.put("resultImgUrl", job.getResultImgUrl());
            return ResponseEntity.ok(response);
        }).orElseGet(() -> {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Job not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        });
    }
}
