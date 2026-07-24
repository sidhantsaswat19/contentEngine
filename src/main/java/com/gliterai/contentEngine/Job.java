package com.gliterai.contentEngine;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)

    private UUID id;
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String status;
    private String resultImgUrl;
    private LocalDateTime createdAt = LocalDateTime.now();

    public UUID getId() {return id;}
    public void setId(UUID id) {this.id = id;}
    public String getProductName() {return title;}
    public void setProductName(String productName) {this.title = productName;}
    public String getDescription() {return description;}
    //public void setDescription(String description) {this.description = description;}
    public String getStatus() {return status;}
    public void setStatus(String status) {this.status = status;}
    public String getResultImgUrl() {return resultImgUrl;}
    //public void setResultImgUrl(String resultImgUrl) {this.resultImgUrl = resultImgUrl;}
    public LocalDateTime getCreatedAt() {return createdAt;}
    public void setCreatedAt(LocalDateTime createdAt) {this.createdAt = createdAt;}

    public void setProductImgUrl(String productImgUrl) {
        this.resultImgUrl = productImgUrl;
    }

    public void setProductDescription(String productDescription) {
        this.description = productDescription;
    }

    @Column(columnDefinition = "TEXT")
    private String prompt;

    public String getPrompt() {return prompt;}
    public void setPrompt(String prompt) {this.prompt = prompt;}
}
