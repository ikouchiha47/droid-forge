# On-Device AI Inference — Reference

---

## When to use on-device inference

- Privacy: data never leaves the device
- Offline: works without internet
- Latency: no round-trip — inference in 20-200ms depending on model size and hardware
- Cost: no API call charges

When NOT to use: models >500MB, tasks requiring GPT-4-level reasoning, real-time video at >10fps.

---

## TensorFlow Lite — image/vision tasks

Best for: image classification, object detection, pose estimation, OCR.

```kotlin
// Load model from assets
val model = FileUtil.loadMappedFile(context, "model.tflite")
val interpreter = Interpreter(model, Interpreter.Options().apply {
    numThreads = 4
    useNNAPI = true   // uses Android Neural Networks API — hardware acceleration
})

// Run inference
val input = Array(1) { Array(224) { Array(224) { FloatArray(3) } } }  // [1, 224, 224, 3]
val output = Array(1) { FloatArray(1000) }  // ImageNet classes
interpreter.run(input, output)
val topClass = output[0].indices.maxByOrNull { output[0][it] } ?: -1
```

Pre-trained models from TensorFlow Hub:
- MobileNetV3 (image classification, ~4MB)
- EfficientDet Lite (object detection, ~6MB)
- BlazeFace (face detection, ~0.4MB)

### Preprocessing helper

```kotlin
fun bitmapToFloatArray(bitmap: Bitmap, mean: Float = 127.5f, std: Float = 127.5f): ByteBuffer {
    val buf = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4).order(ByteOrder.nativeOrder())
    val pixels = IntArray(224 * 224)
    Bitmap.createScaledBitmap(bitmap, 224, 224, true).getPixels(pixels, 0, 224, 0, 0, 224, 224)
    pixels.forEach { pixel ->
        buf.putFloat(((pixel shr 16 and 0xFF) - mean) / std)
        buf.putFloat(((pixel shr 8 and 0xFF) - mean) / std)
        buf.putFloat(((pixel and 0xFF) - mean) / std)
    }
    return buf
}
```

---

## ONNX Runtime — PyTorch/HuggingFace models

Best for: NLP (classification, embedding, NER), audio, custom models from Python.

Export from Python:
```python
import torch
model = torch.load("model.pt")
torch.onnx.export(model, dummy_input, "model.onnx", opset_version=17)
```

```kotlin
val sessionOptions = OrtSession.SessionOptions().apply {
    addNnapi()  // use Android NNAPI
}
val session = OrtEnvironment.getEnvironment().createSession(
    context.assets.open("model.onnx").readBytes(),
    sessionOptions
)
val input = OnnxTensor.createTensor(env, floatArrayOf(...), longArrayOf(1, 128))
val result = session.run(mapOf("input_ids" to input))
val logits = result["logits"]!!.value as Array<FloatArray>
```

---

## On-device LLM — llama.cpp via JNI

For Phi-3-mini (3.8B, ~2GB Q4), Gemma-2B (~1.3GB Q4), or Mistral-7B (requires 8GB+ RAM device).

This requires building `llama.cpp` as a shared library and loading via JNI. No Maven artifact exists.

### Build steps

```bash
git clone https://github.com/ggerganov/llama.cpp
cd llama.cpp
mkdir build-android && cd build-android
cmake .. \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-26 \
  -DGGML_VULKAN=OFF \
  -DLLAMA_BUILD_EXAMPLES=OFF
make -j$(nproc)
# Copy libllama.so and libggml.so to android/app/src/main/jniLibs/arm64-v8a/
```

### JNI bridge (Kotlin side)

```kotlin
object LlamaJNI {
    init { System.loadLibrary("llama_jni") }

    external fun initModel(modelPath: String, nCtx: Int, nThreads: Int): Long
    external fun generate(ctx: Long, prompt: String, maxTokens: Int): String
    external fun freeModel(ctx: Long)
}

// Usage
val ctx = LlamaJNI.initModel("${context.filesDir}/phi3-mini.gguf", nCtx = 2048, nThreads = 4)
val response = LlamaJNI.generate(ctx, "Summarize: $text", maxTokens = 200)
LlamaJNI.freeModel(ctx)
```

Model download: user downloads `.gguf` file in-app (1-2GB). Store in `context.filesDir/models/`.
Show a progress bar via `BaseWorker`. Only download once.

---

## MediaPipe — Google's on-device ML pipelines

Covers face detection, hand tracking, pose, text classification, image segmentation.

```toml
[versions]
mediapipe = "0.10.14"
[libraries]
mediapipe-tasks-vision = { group = "com.google.mediapipe", name = "tasks-vision", version.ref = "mediapipe" }
mediapipe-tasks-text = { group = "com.google.mediapipe", name = "tasks-text", version.ref = "mediapipe" }
```

```kotlin
// Text classification example
val classifier = TextClassifier.createFromFile(context, "text_classifier.tflite")
val result = classifier.classify(TextClassificationRequest.builder().setText(input).build())
result.classificationResult.classifications[0].categories.forEach { cat ->
    println("${cat.categoryName}: ${cat.score}")
}
```

---

## Model storage

Store models in `context.filesDir/models/`. Never in assets — assets are compressed, making
memory-mapped inference impossible (required by TFLite and llama.cpp for performance).

```kotlin
fun copyModelFromAssets(context: Context, assetName: String): File {
    val outFile = File(context.filesDir, "models/$assetName")
    if (!outFile.exists()) {
        context.assets.open(assetName).use { input ->
            outFile.outputStream().use { input.copyTo(it) }
        }
    }
    return outFile
}
```

For large models (>100MB): download in-app with `BaseWorker` and progress reporting.
