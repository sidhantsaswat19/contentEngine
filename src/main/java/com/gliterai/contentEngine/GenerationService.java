package com.gliterai.contentEngine;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GenerationService {
    private final JobRepository jobRepository;

    public GenerationService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Async
    public void processGenerationJob(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));

        try{
            job.setStatus("processing");
            jobRepository.save(job);

            Thread.sleep(5000); // Simulate processing time

            job.setResultImgUrl("img.png");
            job.setStatus("completed");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Job processing interrupted", e);
        } finally{
            jobRepository.save(job);
        }
    }
}
