package com.gliterai.contentEngine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GenerationService {
    private final JobRepository jobRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${comfyui.api.url}")
    private String comfyuiApiUrl;

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
            jobRepository.save(job);

            String uploadedFileName = uploadImageToComfyUI(job.getResultImgUrl());

            String comfyWorkflowJson = getComfyWorkflowTemplate();
            JsonNode rootNode = new ObjectMapper().readTree(comfyWorkflowJson);

            ((ObjectNode) rootNode.get("5").get("inputs")).put("text",generatedPrompt);
            ((ObjectNode) rootNode.get("3").get("inputs")).put("image",uploadedFileName);

            Map<String,Object> promptRequest = Map.of(
                    "prompt",rootNode,"clientId",jobId.toString()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("ngrok-skip-browser-warning", "true");
            HttpEntity<Map<String,Object>> request = new HttpEntity<>(promptRequest, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(comfyuiApiUrl+"/prompt", request, Map.class);
            String promptId = response.getBody().get("promptId").toString();

            String outputFilename = pollComfyUIForCompletion(promptId);

            job.setResultImgUrl(comfyuiApiUrl+"/view?filename="+outputFilename+"&type = output");
            job.setStatus("completed");
            //Thread.sleep(5000); // Simulate processing time

            //job.setProductImgUrl("img.png");
            //job.setStatus("completed");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Job processing interrupted", e);
        } finally{
            jobRepository.save(job);
        }
    }

    private String uploadImageToComfyUI(String url) {

        byte[] imageBytes = restTemplate.getForObject(url, byte[].class);

        MultiValueMap<String,Object> body = new LinkedMultiValueMap<>();
        body.add("image",new ByteArrayResource(imageBytes){
            @Override
            public String getFilename() {
                return "input.png"+System.currentTimeMillis();
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("ngrok-skip-browser-warning", "true");
        HttpEntity<MultiValueMap<String,Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(comfyuiApiUrl+"/upload", request, Map.class);
        return response.getBody().get("filename").toString();

    }

    private String pollComfyUIForCompletion(String promptId) throws InterruptedException {
        int maxAttempts = 60;

        for(int i=0; i<maxAttempts; i++) {
            Thread.sleep(2000);

            HttpHeaders headers = new HttpHeaders();
            headers.set("ngrok-skip-browser-warning", "true");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    comfyuiApiUrl+"/history/"+promptId, HttpMethod.GET,entity,Map.class
            );
            //ResponseEntity<Map> response = restTemplate.getForEntity(comfyuiApiUrl+"/status/"+promptId, Map.class);
            Map<String,Object> history = response.getBody();

            if(history!=null && history.containsKey(promptId)) {
                Map<String,Object> promptData = (Map<String, Object>) history.get(promptId);
                Map<String,Object> resultData = (Map<String, Object>) promptData.get("outputs");

                Map<String,Object> saveImgNode = (Map<String, Object>) resultData.get("14");
                List<Map<String,Object>> saveImgList = (List<Map<String, Object>>) saveImgNode.get("saveImgList");

                return (String) saveImgList.get(0).get("filename");
            }
        }
        throw new InterruptedException("Timeout waiting for ComfyUI completion");
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

    private String getComfyWorkflowTemplate() {
        return "{\n" +
                "  \"2\": {\n" +
                "    \"inputs\": {\n" +
                "      \"ckpt_name\": \"sd_xl_base_1.0.safetensors\"\n" +
                "    },\n" +
                "    \"class_type\": \"CheckpointLoaderSimple\",\n" +
                "    \"_meta\": {\n" +
                "      \"title\": \"Load Checkpoint\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"3\": {\n" +
                "    \"inputs\": {\n" +
                "      \"image\": \"1_0e96da8e-e4d6-4e42-883c-56c29524d41a (1).jpeg\"\n" +
                "    },\n" +
                "    \"class_type\": \"LoadImage\",\n" +
                "    \"_meta\": {\n" +
                "      \"title\": \"Load Image\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"4\": {\n" +
                "    \"inputs\": {\n" +
                "      \"pixels\": [\n" +
                "        \"3\",\n" +
                "        0\n" +
                "      ],\n" +
                "      \"vae\": [\n" +
                "        \"2\",\n" +
                "        2\n" +
                "      ]\n" +
                "    },\n" +
                "    \"class_type\": \"VAEEncode\",\n" +
                "    \"_meta\": {\n" +
                "      \"title\": \"VAE Encode\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"5\": {\n" +
                "    \"inputs\": {\n" +
                "      \"text\": \"professional aesthetic product photography, handpainted wooden salad bowl, cozy outdoor summer picnic setting on a clean white linen blanket, natural soft sunlight, fresh lemons and green leaves placed nearby, soft background bokeh, photorealistic, cinematic lighting, 8k resolution, crisp focus, high detail, warm vibrant tones\",\n" +
                "      \"clip\": [\n" +
                "        \"2\",\n" +
                "        1\n" +
                "      ]\n" +
                "    },\n" +
                "    \"class_type\": \"CLIPTextEncode\",\n" +
                "    \"_meta\": {\n" +
                "      \"title\": \"CLIP Text Encode (Prompt)\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"6\": {\n" +
                "    \"inputs\": {\n" +
                "      \"text\": \"blurry, low quality, low resolution, distorted geometry, warped bowl, plastic texture, ugly, oversaturated, dark shadows, grainy, noise, watermark, text, signature, bad lighting, overexposed, compressed, artifacts, cheap material\",\n" +
                "      \"clip\": [\n" +
                "        \"2\",\n" +
                "        1\n" +
                "      ]\n" +
                "    },\n" +
                "    \"class_type\": \"CLIPTextEncode\",\n" +
                "    \"_meta\": {\n" +
                "      \"title\": \"CLIP Text Encode (Prompt)\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"7\": {\n" +
                "    \"inputs\": {\n" +
                "      \"seed\": 538981911262816,\n" +
                "      \"steps\": 20,\n" +
                "      \"cfg\": 7,\n" +
                "      \"sampler_name\": \"euler\",\n" +
                "      \"scheduler\": \"simple\",\n" +
                "      \"denoise\": 0.6,\n" +
                "      \"model\": [\n" +
                "        \"2\",\n" +
                "        0\n" +
                "      ],\n" +
                "      \"positive\": [\n" +
                "        \"5\",\n" +
                "        0\n" +
                "      ],\n" +
                "      \"negative\": [\n" +
                "        \"6\",\n" +
                "        0\n" +
                "      ],\n" +
                "      \"latent_image\": [\n" +
                "        \"4\",\n" +
                "        0\n" +
                "      ]\n" +
                "    },\n" +
                "    \"class_type\": \"KSampler\",\n" +
                "    \"_meta\": {\n" +
                "      \"title\": \"KSampler\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"9\": {\n" +
                "    \"inputs\": {\n" +
                "      \"samples\": [\n" +
                "        \"7\",\n" +
                "        0\n" +
                "      ],\n" +
                "      \"vae\": [\n" +
                "        \"2\",\n" +
                "        2\n" +
                "      ]\n" +
                "    },\n" +
                "    \"class_type\": \"VAEDecode\",\n" +
                "    \"_meta\": {\n" +
                "      \"title\": \"VAE Decode\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"10\": {\n" +
                "    \"inputs\": {\n" +
                "      \"model_name\": \"4x-UltraSharp.pth\"\n" +
                "    },\n" +
                "    \"class_type\": \"UpscaleModelLoader\",\n" +
                "    \"_meta\": {\n" +
                "      \"title\": \"Load Upscale Model\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"12\": {\n" +
                "    \"inputs\": {\n" +
                "      \"upscale_model\": [\n" +
                "        \"10\",\n" +
                "        0\n" +
                "      ],\n" +
                "      \"image\": [\n" +
                "        \"9\",\n" +
                "        0\n" +
                "      ]\n" +
                "    },\n" +
                "    \"class_type\": \"ImageUpscaleWithModel\",\n" +
                "    \"_meta\": {\n" +
                "      \"title\": \"Upscale Image (using Model)\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"14\": {\n" +
                "    \"inputs\": {\n" +
                "      \"filename_prefix\": \"ComfyUI\",\n" +
                "      \"images\": [\n" +
                "        \"12\",\n" +
                "        0\n" +
                "      ]\n" +
                "    },\n" +
                "    \"class_type\": \"SaveImage\",\n" +
                "    \"_meta\": {\n" +
                "      \"title\": \"Save Image\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }
}
