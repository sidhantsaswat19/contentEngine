# Mini AI Content Engine

An asynchronous, full-stack AI content engine built with **Spring Boot**, **PostgreSQL**, **Google Gemini API**, and **ComfyUI**. This application automates product lifestyle image generation by taking product details and a reference image, generating context-aware prompts using an LLM, and executing an **Img2Img + Upscaling** workflow powered by SDXL on a remote GPU server.

---

## 🚀 Live Links & Assignment Artifacts

* **Live Hosted Application:** [https://contentengine-e94p.onrender.com/](https://contentengine-e94p.onrender.com/)
* **ComfyUI Saved Workflow (JSON):** [`./comfyui_workflow.json`](./comfyui_workflow.json)
* **Sample Output 1:** [`./output_1.png`](./Output_1.png)
* **Sample Output 2:** [`./output_2.png`](./Output_2.png)

---

## ✨ Key Features

### 1. Asynchronous Job Processing & Status Polling
* **Non-Blocking Architecture:** Accepts job submissions via `POST /generate` and returns a job `UUID` immediately.
* **Async Task Execution:** Uses Spring Boot `@Async` background threads to manage LLM API calls, image downloads, and image generation without holding open HTTP requests.
* **Real-time Status Polling:** Client frontend polls `GET /jobs/{id}` every 2 seconds to dynamically transition UI state through `pending` $\rightarrow$ `processing` $\rightarrow$ `completed`.

### 2. Dynamic Prompt Generation (Google Gemini API)
* Programmatically parses product names and descriptions into high-quality, UGC-style photorealistic prompts tailored for Stable Diffusion.
* Features integrated error handling and fallback logic to guarantee pipeline continuity across API quota or regional restriction scenarios.

### 3. Open-Source AI Diffusion Pipeline (ComfyUI Img2Img)
* **SDXL Base Model:** Generates high-resolution lifestyle scenes while preserving core product appearance.
* **Controlled Denoising:** Utilizes a tuned `denoise` parameter (`0.60`) on the `KSampler` node to preserve base geometry while generating new aesthetic background environments.
* **Model-Based Upscaling:** Incorporates the `4x-UltraSharp` upscaler node to produce crisp, production-grade output images ($4000 \times 5000$ resolution).
* **Remote GPU Execution:** Hosted via Google Colab T4 GPU instance, exposed over a secure tunnel.

### 4. API Resilience & Enterprise Handling
* Inject custom `ngrok-skip-browser-warning: true` headers on outbound Spring Boot requests to bypass Ngrok interstitial warnings.
* Automated file conversion and multipart form-data upload to ComfyUI's `/upload/image` endpoint.

---

## 🛠️ Tech Stack

* **Backend:** Java 17, Spring Boot, Spring Data JPA, PostgreSQL, Jackson, RestTemplate
* **Frontend:** HTML5, CSS3, JavaScript (Fetch API with `setInterval` polling)
* **AI & Workflows:**
  * **LLM:** Google Gemini API (`generativelanguage.googleapis.com`)
  * **Diffusion Model:** Stable Diffusion XL Base 1.0 (`sd_xl_base_1.0.safetensors`)
  * **Upscaler:** `4x-UltraSharp.pth`
  * **Workflow Orchestration:** ComfyUI (running on Google Colab)
* **Infrastructure & Tunneling:** Render (Web Service & Managed PostgreSQL Database), Ngrok

---

## 📊 System Architecture
[ User Frontend UI ]
│
│ 1. POST /generate (Product Name, Desc, Image URL)
▼
[ Spring Boot API ] ─────────► [ PostgreSQL Database ] (Status: 'pending')
│
│ 2. Background Task Triggered (@Async)
├──────► 3. Call Gemini API ──► Generate SDXL Prompt
├──────► 4. Download Input Image & Upload to ComfyUI (/upload/image)
├──────► 5. Dispatch Workflow JSON to ComfyUI (/prompt) via Ngrok
│
▼
[ ComfyUI on Colab (T4 GPU) ]
│
├─► VAE Encode ──► KSampler (denoise: 0.60, cfg: 7.0) ──► VAE Decode
└─► Image Upscale (4x-UltraSharp)
│
▼
[ Spring Boot API ] ◄──────── Polling ComfyUI History (/history/{id})
│
│ 6. Store Result Image URL & Mark Status 'completed'
▼
[ PostgreSQL Database ] ◄───── Frontend Polling GET /jobs/{id}

---

## 🔌 API Reference

### Submit Generation Job
```http
POST /generate
Content-Type: application/json
```
## Request Body:
JSON{
  "productName": "Florentine Wooden Salad Bowl",
  "description": "A match made in summer - salads and wooden bowls...",
  "productImageUrl": "[https://www.chumbak.com/cdn/shop/files/1_0e96da8e-e4d6-4e42-883c-56c29524d41a.jpg](https://www.chumbak.com/cdn/shop/files/1_0e96da8e-e4d6-4e42-883c-56c29524d41a.jpg)"
}

## Response (202 Accepted):
JSON{
  "jobId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "pending"
}

## Fetch Job Status HTTPGET /jobs/{jobId}

## Response (200 OK - Processing):
JSON{
  "jobId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "processing",
  "promptUsed": "professional aesthetic product photography, handpainted wooden salad bowl..."
}

## Response (200 OK - Completed):
JSON{
  "jobId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "completed",
  "resultImageUrl": "[https://xxxx.ngrok-free.app/view?filename=ComfyUI_00001_.png&type=output](https://xxxx.ngrok-free.app/view?filename=ComfyUI_00001_.png&type=output)",
  "promptUsed": "professional aesthetic product photography, handpainted wooden salad bowl..."
}


## 🏃 Local Setup Instructions
Clone the repository:Bashgit clone [https://github.com/your-username/content-engine.git](https://github.com/your-username/content-engine.git)
cd content-engine

## Setup ComfyUI on Colab:
Open the provided notebook in Google Colab with a GPU (T4) hardware accelerator.Enter your Ngrok Authtoken and execute all cells.
Copy the public Ngrok endpoint URL (e.g., https://xxxx.ngrok-free.app).

## Configure Environment Variables:
Update src/main/resources/application.properties with your database credentials, Gemini API key, and the Ngrok URL.Build and run the application:Bash./mvnw spring-boot:run
Access the application:Open http://localhost:8080 in your web browser.
