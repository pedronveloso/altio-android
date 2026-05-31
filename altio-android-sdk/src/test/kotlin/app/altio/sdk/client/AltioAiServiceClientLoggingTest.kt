/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.client

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AltioAiServiceClientLoggingTest {

  @Test
  fun `request log replaces binary multipart payload with summary`() {
    val request =
        Request.Builder()
            .url("http://127.0.0.1:52731/v1/sessions/test/transcribe")
            .post(
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "audio",
                        "recording.mp3",
                        byteArrayOf(1, 2, 3, 4).toRequestBody("audio/mpeg".toMediaType()),
                    )
                    .build()
            )
            .build()

    val log = request.toLogString()

    assertTrue(log.contains("<multipart body omitted:"))
    assertTrue(log.contains("audio=recording.mp3 (audio/mpeg, binary omitted)"))
    assertFalse(log.contains("\u0001"))
  }

  @Test
  fun `response log keeps textual json body`() {
    val request = Request.Builder().url("http://127.0.0.1:52731/v1/health").get().build()
    val response =
        Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("""{"status":"ok"}""".toResponseBody("application/json".toMediaType()))
            .build()

    val log = response.toLogString()

    assertTrue(log.contains("""{"status":"ok"}"""))
  }
}
