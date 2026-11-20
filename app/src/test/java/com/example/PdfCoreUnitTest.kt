package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PageEditItem
import com.example.pdf.DocxBlock
import com.example.pdf.DocxBuilder
import com.example.pdf.DocxPageModel
import com.example.pdf.PdfProcessor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfCoreUnitTest {

    private fun createBlankPdf(dir: File, name: String, pages: Int): File {
        val file = File(dir, name)
        PDDocument().use { doc ->
            repeat(pages) { doc.addPage(PDPage()) }
            doc.save(file)
        }
        return file
    }

    @Test
    fun parseRanges_supportsArabicNumeralsAndCommas() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val processor = PdfProcessor(context)
        val ranges = processor.parsePageRanges("١-٥، 8-12, 15-20", totalPages = 25)
        assertEquals(listOf(1..5, 8..12, 15..20), ranges)
    }

    @Test
    fun splitExtractMergeOrganizeProtectUnlock_allWorkEndToEnd() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val processor = PdfProcessor(context)
        val tempDir = File(context.cacheDir, "unit_test_pdfs").apply { mkdirs() }
        val sourcePdf = createBlankPdf(tempDir, "sample_10p.pdf", 10)

        // 1. Multi-range split -> 3 parts in ZIP
        val splitRes = processor.splitByRanges(
            inputFile = sourcePdf,
            rangesText = "1-3، 5-6، 9-10",
            saveAsZip = true,
            baseName = "sample_10p.pdf",
            onProgress = { _, _ -> }
        )
        assertTrue(splitRes.primaryOutputFile.exists())
        assertEquals(3, splitRes.additionalFiles.size)
        assertEquals(3, processor.inspectPdf(splitRes.additionalFiles[0]).first)
        assertEquals(2, processor.inspectPdf(splitRes.additionalFiles[1]).first)
        assertEquals(2, processor.inspectPdf(splitRes.additionalFiles[2]).first)

        // 2. Equal parts split (10 pages into 4 parts -> 3, 3, 3, 1)
        val equalRes = processor.splitEqualParts(sourcePdf, 4, "sample_10p.pdf") { _, _ -> }
        assertEquals(4, equalRes.additionalFiles.size)

        // 3. Extract specific pages
        val extractRes = processor.extractPages(sourcePdf, listOf(2, 4, 8), "sample_10p.pdf") { _, _ -> }
        assertEquals(3, processor.inspectPdf(extractRes.primaryOutputFile).first)

        // 4. Merge PDFs (3 + 2 pages = 5 pages)
        val mergeRes = processor.mergePdfs(
            listOf(splitRes.additionalFiles[0], splitRes.additionalFiles[1])
        ) { _, _ -> }
        assertEquals(5, processor.inspectPdf(mergeRes.primaryOutputFile).first)

        // 5. Organize pages (reorder + rotate + delete 1 page from 3-page doc -> 2 pages)
        val organizeRes = processor.organizePages(
            inputFile = extractRes.primaryOutputFile,
            pageEdits = listOf(
                PageEditItem(originalPageIndex = 2, rotationDegrees = 90, isDeleted = false),
                PageEditItem(originalPageIndex = 1, rotationDegrees = 0, isDeleted = true),
                PageEditItem(originalPageIndex = 0, rotationDegrees = 180, isDeleted = false)
            ),
            baseName = "extracted.pdf"
        ) { _, _ -> }
        assertEquals(2, processor.inspectPdf(organizeRes.primaryOutputFile).first)

        // 6. Protect & Unlock PDF
        val protectedRes = processor.protectPdf(
            inputFile = organizeRes.primaryOutputFile,
            userPassword = "secret123",
            ownerPassword = "owner123",
            allowPrinting = true,
            allowCopying = false,
            baseName = "organized.pdf"
        ) { _, _ -> }
        assertTrue(processor.inspectPdf(protectedRes.primaryOutputFile).second)

        val unlockedRes = processor.unlockPdf(
            inputFile = protectedRes.primaryOutputFile,
            password = "secret123",
            baseName = "protected.pdf"
        ) { _, _ -> }
        val (unlockedPages, stillEncrypted) = processor.inspectPdf(unlockedRes.primaryOutputFile)
        assertEquals(2, unlockedPages)
        assertFalse(stillEncrypted)
    }

    @Test
    fun docxBuilder_generatesValidRtlOpenXmlWithTablesAndHeaders() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outFile = File(context.cacheDir, "test_rtl_output.docx")
        val page = DocxPageModel(
            pageNumber = 1,
            headerText = "تقرير موعد اللوز",
            footerText = "صفحة 1",
            isRtl = true,
            blocks = listOf(
                DocxBlock(type = "heading1", text = "العنوان الرئيسي", alignment = "right", fontSizePt = 20, isBold = true, isRtl = true),
                DocxBlock(type = "paragraph", text = "فقرة عربية متكاملة الاتجاه.", alignment = "right", fontSizePt = 12, isRtl = true),
                DocxBlock(
                    type = "table",
                    isRtl = true,
                    tableRows = listOf(
                        listOf("البند", "القيمة"),
                        listOf("الجودة", "100%")
                    )
                )
            )
        )

        DocxBuilder.buildDocx(listOf(page), outFile, includeHeaderFooter = true)
        assertTrue(outFile.exists() && outFile.length() > 500)

        ZipFile(outFile).use { zip ->
            val entries = zip.entries().toList().map { it.name }.toSet()
            assertTrue(entries.contains("[Content_Types].xml"))
            assertTrue(entries.contains("_rels/.rels"))
            assertTrue(entries.contains("word/document.xml"))
            assertTrue(entries.contains("word/styles.xml"))
            assertTrue(entries.contains("word/header1.xml"))
            assertTrue(entries.contains("word/footer1.xml"))

            val docXml = zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText()
            assertTrue(docXml.contains("<w:bidi/>"))
            assertTrue(docXml.contains("<w:tbl>"))
            assertTrue(docXml.contains("العنوان الرئيسي"))
            assertTrue(docXml.contains("الجودة"))
        }
    }
}
