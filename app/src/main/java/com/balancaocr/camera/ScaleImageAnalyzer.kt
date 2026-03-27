package com.balancaocr.camera

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.balancaocr.ocr.WeightAnalyzer

@ExperimentalGetImage
class ScaleImageAnalyzer(
    private val analyzer: WeightAnalyzer,
    private val onResult: (result: WeightAnalyzer.AnalysisResult, rawText: String) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val raw = visionText.text
                val parsed = analyzer.parseWeight(raw)
                val result = analyzer.onNewReading(parsed)
                onResult(result, raw)
            }
            .addOnFailureListener { /* ignora frames com erro de OCR */ }
            .addOnCompleteListener { imageProxy.close() }
    }
}
