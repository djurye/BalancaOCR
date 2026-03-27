package com.balancaocr.camera

import android.graphics.Bitmap
import android.graphics.Matrix
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

        // 1. Converter para Bitmap e Rotacionar corretamente
        val bitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        
        // 2. RECORTAR A ÁREA CENTRAL (Região de Interesse)
        // Pegamos apenas um retângulo central para focar no display da balança
        val croppedBitmap = cropToCenter(bitmap, rotationDegrees)

        // 3. Criar a InputImage a partir do recorte (rotação já aplicada no bitmap)
        val image = InputImage.fromBitmap(croppedBitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val raw = visionText.text
                val parsed = analyzer.parseWeight(raw)
                val result = analyzer.onNewReading(parsed)
                onResult(result, raw)
            }
            .addOnFailureListener { /* ignora falhas de frame */ }
            .addOnCompleteListener { 
                imageProxy.close() 
            }
    }

    private fun cropToCenter(bitmap: Bitmap, rotation: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        
        // Define o tamanho do recorte: 60% da largura e 30% da altura no centro
        val width = rotated.width
        val height = rotated.height
        val cropW = (width * 0.6).toInt()
        val cropH = (height * 0.3).toInt()
        
        return Bitmap.createBitmap(
            rotated,
            (width - cropW) / 2,
            (height - cropH) / 2,
            cropW,
            cropH
        )
    }
}