package com.poshanforlife.android.core.utils

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * The backend's report-upload endpoint hard-requires application/pdf (see
 * ReportStorageService.uploadPdf) — its extraction pipeline is PDFBox-based,
 * built for the web app's PDF-upload flow. AN-10's camera capture produces a
 * JPEG, so this wraps it as a single full-page PDF client-side before upload
 * rather than requiring a change to that shared pipeline. Uses the platform
 * android.graphics.pdf.PdfDocument API — no extra dependency needed.
 */
fun wrapImageAsPdf(imageFile: File, outputDir: File): File {
    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        ?: error("Could not decode captured image at ${imageFile.absolutePath}")

    val pdfDocument = PdfDocument()
    try {
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        val pdfFile = File(outputDir, "inbody_report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(pdfFile).use { pdfDocument.writeTo(it) }
        return pdfFile
    } finally {
        pdfDocument.close()
        bitmap.recycle()
    }
}
