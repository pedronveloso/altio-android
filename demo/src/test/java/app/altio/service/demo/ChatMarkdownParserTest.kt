/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMarkdownParserTest {

  @Test
  fun `parse supports four heading levels`() {
    val blocks =
        ChatMarkdownParser.parse(
            """
            # Title
            ## Section
            ### Detail
            #### Minor detail
            """
                .trimIndent()
        )

    assertEquals(
        listOf(
            MarkdownBlock.Heading(1, listOf(MarkdownSegment("Title"))),
            MarkdownBlock.Heading(2, listOf(MarkdownSegment("Section"))),
            MarkdownBlock.Heading(3, listOf(MarkdownSegment("Detail"))),
            MarkdownBlock.Heading(4, listOf(MarkdownSegment("Minor detail"))),
        ),
        blocks,
    )
  }

  @Test
  fun `parse supports bold and italic emphasis`() {
    val blocks = ChatMarkdownParser.parse("Plain **bold** and *italic* text.")

    assertEquals(
        listOf(
            MarkdownBlock.Paragraph(
                listOf(
                    MarkdownSegment("Plain "),
                    MarkdownSegment("bold", bold = true),
                    MarkdownSegment(" and "),
                    MarkdownSegment("italic", italic = true),
                    MarkdownSegment(" text."),
                )
            )
        ),
        blocks,
    )
  }

  @Test
  fun `parse supports inline code`() {
    val blocks = ChatMarkdownParser.parse("Use `client.pollJob(id)` here.")

    assertEquals(
        listOf(
            MarkdownBlock.Paragraph(
                listOf(
                    MarkdownSegment("Use "),
                    MarkdownSegment("client.pollJob(id)", code = true),
                    MarkdownSegment(" here."),
                )
            )
        ),
        blocks,
    )
  }

  @Test
  fun `parse keeps emphasis markers inside inline code as literal text`() {
    val blocks = ChatMarkdownParser.parse("Use `**bold** and *italic*` literally.")

    assertEquals(
        listOf(
            MarkdownBlock.Paragraph(
                listOf(
                    MarkdownSegment("Use "),
                    MarkdownSegment("**bold** and *italic*", code = true),
                    MarkdownSegment(" literally."),
                )
            )
        ),
        blocks,
    )
  }

  @Test
  fun `parse supports fenced code blocks`() {
    val blocks =
        ChatMarkdownParser.parse(
            """
            Before

            ```
            val answer = 42
            println(answer)
            ```

            After
            """
                .trimIndent()
        )

    assertEquals(
        listOf(
            MarkdownBlock.Paragraph(listOf(MarkdownSegment("Before"))),
            MarkdownBlock.CodeBlock("val answer = 42\nprintln(answer)"),
            MarkdownBlock.Paragraph(listOf(MarkdownSegment("After"))),
        ),
        blocks,
    )
  }

  @Test
  fun `parse groups consecutive bullet lines into one list`() {
    val blocks =
        ChatMarkdownParser.parse(
            """
            - First
            * Second with **bold**

            Tail paragraph
            """
                .trimIndent()
        )

    assertEquals(
        listOf(
            MarkdownBlock.BulletList(
                items =
                    listOf(
                        listOf(MarkdownSegment("First")),
                        listOf(
                            MarkdownSegment("Second with "),
                            MarkdownSegment("bold", bold = true),
                        ),
                    )
            ),
            MarkdownBlock.Paragraph(listOf(MarkdownSegment("Tail paragraph"))),
        ),
        blocks,
    )
  }

  @Test
  fun `parse keeps unmatched markers as plain text`() {
    val blocks = ChatMarkdownParser.parse("A trailing * marker and **open bold and `open code")

    assertEquals(
        listOf(
            MarkdownBlock.Paragraph(
                listOf(MarkdownSegment("A trailing * marker and **open bold and `open code"))
            )
        ),
        blocks,
    )
  }
}
