package com.example.pdf

import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DocxBlock(
    val type: String, // "heading1", "heading2", "paragraph", "list_item", "table"
    val text: String = "",
    val alignment: String = "right", // "right", "center", "left"
    val colorHex: String? = null,
    val fontSizePt: Int = 12,
    val isBold: Boolean = false,
    val isRtl: Boolean = true,
    val tableRows: List<List<String>> = emptyList()
)

data class DocxPageModel(
    val pageNumber: Int,
    val headerText: String? = null,
    val footerText: String? = null,
    val isRtl: Boolean = true,
    val blocks: List<DocxBlock> = emptyList(),
    val embeddedImagesPng: List<ByteArray> = emptyList()
)

/**
 * Generates a valid, standards-compliant Microsoft Word (.docx) OpenXML package
 * supporting Arabic RTL directionality, headings, custom fonts/sizes/colors,
 * formatted tables, page headers/footers, margins, and embedded images.
 */
object DocxBuilder {

    fun buildDocx(
        pages: List<DocxPageModel>,
        outputFile: File,
        includeHeaderFooter: Boolean = true
    ) {
        val headerText = if (includeHeaderFooter) {
            pages.firstNotNullOfOrNull { it.headerText?.takeIf { h -> h.isNotBlank() } }
        } else null
        val footerText = if (includeHeaderFooter) {
            pages.firstNotNullOfOrNull { it.footerText?.takeIf { f -> f.isNotBlank() } }
        } else null

        val mediaEntries = mutableListOf<Triple<String, String, ByteArray>>() // rId, filename, pngBytes
        var nextRelId = 4 // rId1=styles, rId2=header, rId3=footer
        val pageImageRelIds = mutableMapOf<Int, List<Pair<String, Pair<Long, Long>>>>()

        for (page in pages) {
            val relsForPage = mutableListOf<Pair<String, Pair<Long, Long>>>()
            page.embeddedImagesPng.forEachIndexed { idx, bytes ->
                val rId = "rId${nextRelId++}"
                val fileName = "image_p${page.pageNumber}_${idx + 1}.png"
                mediaEntries.add(Triple(rId, fileName, bytes))

                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                val pxW = opts.outWidth.coerceAtLeast(100)
                val pxH = opts.outHeight.coerceAtLeast(100)
                // Fit within 5.5 inches wide (~5029200 EMU)
                val maxWidthEmu = 5_029_200L
                val rawWidthEmu = pxW * 9525L
                val widthEmu = rawWidthEmu.coerceAtMost(maxWidthEmu)
                val heightEmu = (widthEmu.toDouble() * pxH / pxW).toLong().coerceAtLeast(450_000L)
                relsForPage.add(rId to (widthEmu to heightEmu))
            }
            pageImageRelIds[page.pageNumber] = relsForPage
        }

        val hasHeader = !headerText.isNullOrBlank()
        val hasFooter = !footerText.isNullOrBlank()
        val docIsRtl = pages.any { it.isRtl }

        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            writeEntry(zip, "[Content_Types].xml", buildContentTypesXml(hasHeader, hasFooter, mediaEntries.isNotEmpty()))
            writeEntry(zip, "_rels/.rels", buildRootRelsXml())
            writeEntry(zip, "word/_rels/document.xml.rels", buildDocumentRelsXml(hasHeader, hasFooter, mediaEntries))
            writeEntry(zip, "word/styles.xml", buildStylesXml(docIsRtl))

            if (hasHeader) {
                writeEntry(zip, "word/header1.xml", buildHeaderFooterXml("hdr", headerText!!, docIsRtl))
            }
            if (hasFooter) {
                writeEntry(zip, "word/footer1.xml", buildHeaderFooterXml("ftr", footerText!!, docIsRtl))
            }

            for ((_, fileName, bytes) in mediaEntries) {
                zip.putNextEntry(ZipEntry("word/media/$fileName"))
                zip.write(bytes)
                zip.closeEntry()
            }

            writeEntry(
                zip,
                "word/document.xml",
                buildDocumentXml(pages, pageImageRelIds, hasHeader, hasFooter, docIsRtl)
            )
        }
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildContentTypesXml(hasHeader: Boolean, hasFooter: Boolean, hasImages: Boolean): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        sb.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        sb.append("""<Default Extension="xml" ContentType="application/xml"/>""")
        if (hasImages) {
            sb.append("""<Default Extension="png" ContentType="image/png"/>""")
        }
        sb.append("""<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""")
        sb.append("""<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>""")
        if (hasHeader) {
            sb.append("""<Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>""")
        }
        if (hasFooter) {
            sb.append("""<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>""")
        }
        sb.append("""</Types>""")
        return sb.toString()
    }

    private fun buildRootRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private fun buildDocumentRelsXml(
        hasHeader: Boolean,
        hasFooter: Boolean,
        mediaEntries: List<Triple<String, String, ByteArray>>
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        sb.append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        if (hasHeader) {
            sb.append("""<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>""")
        }
        if (hasFooter) {
            sb.append("""<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>""")
        }
        for ((rId, fileName, _) in mediaEntries) {
            sb.append("""<Relationship Id="$rId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/$fileName"/>""")
        }
        sb.append("""</Relationships>""")
        return sb.toString()
    }

    private fun buildStylesXml(isRtl: Boolean): String {
        val bidiTag = if (isRtl) "<w:bidi/>" else ""
        val rtlTag = if (isRtl) "<w:rtl/>" else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:cs="Arial"/>
        <w:sz w:val="24"/>
        <w:szCs w:val="24"/>
        $rtlTag
      </w:rPr>
    </w:rPrDefault>
    <w:pPrDefault>
      <w:pPr>
        $bidiTag
        <w:spacing w:after="140" w:line="276" w:lineRule="auto"/>
      </w:pPr>
    </w:pPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
  </w:style>
</w:styles>"""
    }

    private fun buildHeaderFooterXml(rootTag: String, text: String, isRtl: Boolean): String {
        val bidi = if (isRtl) "<w:bidi/>" else ""
        val rtl = if (isRtl) "<w:rtl/>" else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:$rootTag xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:p>
    <w:pPr>$bidi<w:jc w:val="center"/></w:pPr>
    <w:r>
      <w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:cs="Arial"/><w:color w:val="666666"/><w:sz w:val="18"/><w:szCs w:val="18"/>$rtl</w:rPr>
      <w:t xml:space="preserve">${escapeXml(text)}</w:t>
    </w:r>
  </w:p>
</w:$rootTag>"""
    }

    private fun buildDocumentXml(
        pages: List<DocxPageModel>,
        pageImageRelIds: Map<Int, List<Pair<String, Pair<Long, Long>>>>,
        hasHeader: Boolean,
        hasFooter: Boolean,
        docIsRtl: Boolean
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append(
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" """ +
                """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" """ +
                """xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" """ +
                """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" """ +
                """xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">"""
        )
        sb.append("<w:body>")

        var drawingId = 1
        pages.forEachIndexed { pageIdx, page ->
            for (block in page.blocks) {
                if (block.type == "table" && block.tableRows.isNotEmpty()) {
                    sb.append(buildTableXml(block.tableRows, block.isRtl))
                } else {
                    sb.append(buildParagraphXml(block))
                }
            }

            // Append embedded images extracted from this PDF page
            val images = pageImageRelIds[page.pageNumber].orEmpty()
            for ((rId, dims) in images) {
                sb.append(buildImageParagraphXml(rId, drawingId++, dims.first, dims.second))
            }

            // Insert page break between pages
            if (pageIdx < pages.lastIndex) {
                sb.append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            }
        }

        // Section properties: A4 paper, balanced margins, RTL section if Arabic, headers/footers
        sb.append("<w:sectPr>")
        if (hasHeader) {
            sb.append("""<w:headerReference w:type="default" r:id="rId2"/>""")
        }
        if (hasFooter) {
            sb.append("""<w:footerReference w:type="default" r:id="rId3"/>""")
        }
        if (docIsRtl) {
            sb.append("<w:bidi/>")
        }
        sb.append("""<w:pgSz w:w="11906" w:h="16838"/>""")
        sb.append("""<w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="708" w:footer="708" w:gutter="0"/>""")
        sb.append("</w:sectPr>")
        sb.append("</w:body></w:document>")
        return sb.toString()
    }

    private fun buildParagraphXml(block: DocxBlock): String {
        val text = block.text.trim()
        if (text.isEmpty()) return ""

        val isHeading1 = block.type == "heading1"
        val isHeading2 = block.type == "heading2"
        val isListItem = block.type == "list_item"

        val halfPoints = when {
            isHeading1 -> (block.fontSizePt.coerceIn(16, 28) * 2)
            isHeading2 -> (block.fontSizePt.coerceIn(14, 22) * 2)
            else -> (block.fontSizePt.coerceIn(10, 18) * 2)
        }
        val boldTag = if (isHeading1 || isHeading2 || block.isBold) "<w:b/><w:bCs/>" else ""
        val cleanColor = block.colorHex
            ?.removePrefix("#")
            ?.takeIf { it.length == 6 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }
            ?: when {
                isHeading1 -> "8C1D2C"
                isHeading2 -> "5A2D1A"
                else -> "1F1917"
            }

        val jc = when (block.alignment.lowercase()) {
            "center" -> "center"
            "left" -> "left"
            "right" -> "right"
            else -> if (block.isRtl) "right" else "left"
        }

        val bidiTag = if (block.isRtl) "<w:bidi/>" else ""
        val rtlTag = if (block.isRtl) "<w:rtl/>" else ""
        val spaceBefore = when {
            isHeading1 -> "220"
            isHeading2 -> "160"
            else -> "40"
        }
        val spaceAfter = when {
            isHeading1 -> "160"
            isHeading2 -> "120"
            else -> "120"
        }
        val prefix = if (isListItem) "• " else ""

        return """<w:p>
  <w:pPr>
    $bidiTag
    <w:jc w:val="$jc"/>
    <w:spacing w:before="$spaceBefore" w:after="$spaceAfter" w:line="276" w:lineRule="auto"/>
  </w:pPr>
  <w:r>
    <w:rPr>
      <w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:cs="Arial"/>
      $boldTag
      <w:color w:val="$cleanColor"/>
      <w:sz w:val="$halfPoints"/>
      <w:szCs w:val="$halfPoints"/>
      $rtlTag
    </w:rPr>
    <w:t xml:space="preserve">${escapeXml(prefix + text)}</w:t>
  </w:r>
</w:p>"""
    }

    private fun buildTableXml(rows: List<List<String>>, isRtl: Boolean): String {
        val sb = StringBuilder()
        sb.append("<w:tbl><w:tblPr>")
        sb.append("""<w:tblW w:w="9638" w:type="dxa"/>""")
        if (isRtl) sb.append("<w:bidiVisual/>")
        sb.append(
            """<w:tblBorders>
              <w:top w:val="single" w:sz="6" w:space="0" w:color="BFA99E"/>
              <w:left w:val="single" w:sz="6" w:space="0" w:color="BFA99E"/>
              <w:bottom w:val="single" w:sz="6" w:space="0" w:color="BFA99E"/>
              <w:right w:val="single" w:sz="6" w:space="0" w:color="BFA99E"/>
              <w:insideH w:val="single" w:sz="4" w:space="0" w:color="D8C8BE"/>
              <w:insideV w:val="single" w:sz="4" w:space="0" w:color="D8C8BE"/>
            </w:tblBorders>"""
        )
        sb.append("</w:tblPr>")

        val bidiTag = if (isRtl) "<w:bidi/>" else ""
        val rtlTag = if (isRtl) "<w:rtl/>" else ""
        val jc = if (isRtl) "right" else "left"

        rows.forEachIndexed { rIdx, row ->
            val isHeaderRow = rIdx == 0
            sb.append("<w:tr>")
            for (cell in row) {
                sb.append("<w:tc><w:tcPr>")
                if (isHeaderRow) {
                    sb.append("""<w:shd w:val="clear" w:color="auto" w:fill="F4E6E8"/>""")
                }
                sb.append(
                    """<w:tcMar>
                      <w:top w:w="80" w:type="dxa"/>
                      <w:left w:w="120" w:type="dxa"/>
                      <w:bottom w:w="80" w:type="dxa"/>
                      <w:right w:w="120" w:type="dxa"/>
                    </w:tcMar>"""
                )
                sb.append("</w:tcPr><w:p><w:pPr>$bidiTag<w:jc w:val=\"$jc\"/></w:pPr><w:r><w:rPr>")
                sb.append("""<w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:cs="Arial"/>""")
                if (isHeaderRow) {
                    sb.append("""<w:b/><w:bCs/><w:color w:val="8C1D2C"/>""")
                } else {
                    sb.append("""<w:color w:val="1F1917"/>""")
                }
                sb.append("""<w:sz w:val="22"/><w:szCs w:val="22"/>$rtlTag</w:rPr>""")
                sb.append("""<w:t xml:space="preserve">${escapeXml(cell.trim())}</w:t></w:r></w:p></w:tc>""")
            }
            sb.append("</w:tr>")
        }

        sb.append("</w:tbl><w:p/>")
        return sb.toString()
    }

    private fun buildImageParagraphXml(rId: String, docPrId: Int, widthEmu: Long, heightEmu: Long): String {
        return """<w:p>
  <w:pPr><w:jc w:val="center"/></w:pPr>
  <w:r>
    <w:drawing>
      <wp:inline distT="0" distB="0" distL="0" distR="0">
        <wp:extent cx="$widthEmu" cy="$heightEmu"/>
        <wp:docPr id="$docPrId" name="Image $docPrId"/>
        <a:graphic>
          <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
            <pic:pic>
              <pic:nvPicPr>
                <pic:cNvPr id="$docPrId" name="Image $docPrId"/>
                <pic:cNvPicPr/>
              </pic:nvPicPr>
              <pic:blipFill>
                <a:blip r:embed="$rId"/>
                <a:stretch><a:fillRect/></a:stretch>
              </pic:blipFill>
              <pic:spPr>
                <a:xfrm><a:off x="0" y="0"/><a:ext cx="$widthEmu" cy="$heightEmu"/></a:xfrm>
                <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
              </pic:spPr>
            </pic:pic>
          </a:graphicData>
        </a:graphic>
      </wp:inline>
    </w:drawing>
  </w:r>
</w:p>"""
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                '\u0000', '\u000B', '\u000C' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
