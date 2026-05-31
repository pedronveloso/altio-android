/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Immutable
internal sealed interface MarkdownBlock {
  @Immutable data class Heading(val level: Int, val segments: List<MarkdownSegment>) : MarkdownBlock

  @Immutable data class Paragraph(val segments: List<MarkdownSegment>) : MarkdownBlock

  @Immutable data class BulletList(val items: List<List<MarkdownSegment>>) : MarkdownBlock

  @Immutable data class CodeBlock(val text: String) : MarkdownBlock
}

@Immutable
internal data class MarkdownSegment(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
)

@Immutable internal data class MarkdownBlocksList(val items: List<MarkdownBlock>)

@Immutable internal data class MarkdownSegmentsList(val items: List<MarkdownSegment>)

internal object ChatMarkdownParser {

  fun parse(markdown: String): List<MarkdownBlock> {
    val normalized = markdown.replace("\r\n", "\n")
    val lines = normalized.split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphLines = mutableListOf<String>()
    val bulletItems = mutableListOf<List<MarkdownSegment>>()
    val codeBlockLines = mutableListOf<String>()
    var inCodeBlock = false

    fun flushParagraph() {
      if (paragraphLines.isEmpty()) return
      blocks += MarkdownBlock.Paragraph(parseInline(paragraphLines.joinToString("\n")))
      paragraphLines.clear()
    }

    fun flushBullets() {
      if (bulletItems.isEmpty()) return
      blocks += MarkdownBlock.BulletList(items = bulletItems.toList())
      bulletItems.clear()
    }

    lines.forEach { line ->
      val trimmed = line.trim()
      when {
        inCodeBlock && trimmed.startsWith("```") -> {
          blocks += MarkdownBlock.CodeBlock(codeBlockLines.joinToString("\n"))
          codeBlockLines.clear()
          inCodeBlock = false
        }
        inCodeBlock -> codeBlockLines += line
        trimmed.startsWith("```") -> {
          flushParagraph()
          flushBullets()
          inCodeBlock = true
        }
        trimmed.isBlank() -> {
          flushParagraph()
          flushBullets()
        }
        trimmed.startsWithHeading() -> {
          flushParagraph()
          flushBullets()
          val level = trimmed.takeWhile { it == '#' }.length
          val content = trimmed.drop(level).trimStart()
          blocks += MarkdownBlock.Heading(level = level, segments = parseInline(content))
        }
        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
          flushParagraph()
          bulletItems += parseInline(trimmed.drop(2).trimStart())
        }
        else -> {
          flushBullets()
          paragraphLines += line
        }
      }
    }

    flushParagraph()
    flushBullets()
    if (inCodeBlock) {
      blocks += MarkdownBlock.CodeBlock(codeBlockLines.joinToString("\n"))
    }
    return blocks
  }

  private fun parseInline(
      text: String,
      bold: Boolean = false,
      italic: Boolean = false,
  ): List<MarkdownSegment> {
    if (text.isEmpty()) return emptyList()

    val segments = mutableListOf<MarkdownSegment>()
    val plainText = StringBuilder()
    var index = 0

    fun flushPlainText() {
      if (plainText.isEmpty()) return
      segments += MarkdownSegment(text = plainText.toString(), bold = bold, italic = italic)
      plainText.clear()
    }

    while (index < text.length) {
      when {
        text[index] == '`' -> {
          val closingIndex = text.indexOf('`', startIndex = index + 1)
          if (closingIndex == -1) {
            plainText.append('`')
            index += 1
          } else {
            flushPlainText()
            segments +=
                MarkdownSegment(
                    text = text.substring(index + 1, closingIndex),
                    code = true,
                )
            index = closingIndex + 1
          }
        }
        text.startsWith("**", index) -> {
          val closingIndex = text.indexOf("**", startIndex = index + 2)
          if (closingIndex == -1) {
            plainText.append("**")
            index += 2
          } else {
            flushPlainText()
            segments +=
                parseInline(
                    text = text.substring(index + 2, closingIndex),
                    bold = true,
                    italic = italic,
                )
            index = closingIndex + 2
          }
        }
        text[index] == '*' -> {
          val closingIndex = findClosingSingleAsterisk(text = text, startIndex = index + 1)
          if (closingIndex == -1) {
            plainText.append('*')
            index += 1
          } else {
            flushPlainText()
            segments +=
                parseInline(
                    text = text.substring(index + 1, closingIndex),
                    bold = bold,
                    italic = true,
                )
            index = closingIndex + 1
          }
        }
        else -> {
          plainText.append(text[index])
          index += 1
        }
      }
    }

    flushPlainText()
    return segments
  }

  private fun String.startsWithHeading(): Boolean {
    val level = takeWhile { it == '#' }.length
    return level in 1..4 && getOrNull(level) == ' '
  }

  private fun findClosingSingleAsterisk(text: String, startIndex: Int): Int {
    var index = startIndex
    while (index < text.length) {
      if (text[index] == '*') {
        val partOfDoubleMarker =
            (index > 0 && text[index - 1] == '*') ||
                (index + 1 < text.length && text[index + 1] == '*')
        if (!partOfDoubleMarker) return index
      }
      index += 1
    }
    return -1
  }
}

@Composable
internal fun MarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
  MarkdownBlocks(
      blocks = MarkdownBlocksList(ChatMarkdownParser.parse(markdown)),
      color = color,
      modifier = modifier,
  )
}

@Composable
private fun MarkdownBlocks(
    blocks: MarkdownBlocksList,
    color: Color,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    blocks.items.forEach { block ->
      when (block) {
        is MarkdownBlock.BulletList -> BulletList(block = block, color = color)
        is MarkdownBlock.CodeBlock -> CodeBlock(text = block.text, color = color)
        is MarkdownBlock.Heading ->
            RichText(
                segments = MarkdownSegmentsList(block.segments),
                color = color,
                style =
                    when (block.level) {
                      1 -> MaterialTheme.typography.headlineSmall
                      2 -> MaterialTheme.typography.titleLarge
                      3 -> MaterialTheme.typography.titleMedium
                      else -> MaterialTheme.typography.titleSmall
                    },
            )
        is MarkdownBlock.Paragraph ->
            RichText(
                segments = MarkdownSegmentsList(block.segments),
                color = color,
                style = MaterialTheme.typography.bodyMedium,
            )
      }
    }
  }
}

@Composable
private fun BulletList(
    block: MarkdownBlock.BulletList,
    color: Color,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    block.items.forEach { item ->
      Row {
        Text(
            text = "\u2022",
            color = color,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.width(8.dp))
        RichText(
            segments = MarkdownSegmentsList(item),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}

@Composable
private fun CodeBlock(text: String, color: Color) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.surface,
      contentColor = color,
      shape = MaterialTheme.shapes.extraSmall,
  ) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        color = color,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
  }
}

@Composable
private fun RichText(
    segments: MarkdownSegmentsList,
    color: Color,
    style: androidx.compose.ui.text.TextStyle,
) {
  Text(
      text =
          buildAnnotatedString {
            segments.items.forEach { segment ->
              pushStyle(
                  SpanStyle(
                      fontWeight = if (segment.bold && !segment.code) FontWeight.Bold else null,
                      fontStyle = if (segment.italic && !segment.code) FontStyle.Italic else null,
                      fontFamily = if (segment.code) FontFamily.Monospace else null,
                      background =
                          if (segment.code) {
                            MaterialTheme.colorScheme.surface
                          } else {
                            Color.Unspecified
                          },
                  )
              )
              append(segment.text)
              pop()
            }
          },
      color = color,
      style = style,
  )
}
