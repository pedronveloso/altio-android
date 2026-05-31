/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.sdk.client

import app.altio.sdk.contract.error.ApiError
import app.altio.sdk.contract.error.ApiErrorCode
import app.altio.sdk.contract.error.ApiErrorEnvelope
import app.altio.sdk.contract.generate.GenerateRequest
import app.altio.sdk.contract.generate.MessageRole
import app.altio.sdk.contract.generate.PromptMessage
import app.altio.sdk.contract.health.DiagnosticsResponse
import app.altio.sdk.contract.health.HealthResponse
import app.altio.sdk.contract.job.JobStatusResponse
import app.altio.sdk.contract.job.SubmitJobResponse
import app.altio.sdk.contract.model.ModelResponse
import app.altio.sdk.contract.model.ModelsListResponse
import app.altio.sdk.contract.session.CreateSessionRequest
import app.altio.sdk.contract.session.SessionDetailResponse
import app.altio.sdk.contract.session.SessionResponse
import app.altio.sdk.contract.stream.DoneEvent
import app.altio.sdk.contract.stream.SseErrorEvent
import app.altio.sdk.contract.stream.TokenEvent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8

class AltioAiServiceClient(
    port: Int,
    private val bearerToken: String,
    logSink: ((String) -> Unit)? = null,
) {
  private val baseUrl = "http://127.0.0.1:$port/v1"
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
  }
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  private val authInterceptor = Interceptor { chain ->
    logSink?.invoke(
        "AUTH ${chain.request().method} ${chain.request().url.encodedPath} bearer.len=${bearerToken.length} sha256=${bearerToken.fingerprint()}"
    )
    chain.proceed(
        chain.request().newBuilder().addHeader("Authorization", "Bearer $bearerToken").build()
    )
  }

  private val trafficLoggingInterceptor = Interceptor { chain ->
    val request = chain.request()
    logSink?.invoke(request.toLogString())
    val response = chain.proceed(request)
    logSink?.invoke(response.toLogString())
    response
  }

  private val http: OkHttpClient =
      OkHttpClient.Builder()
          .readTimeout(0, TimeUnit.MILLISECONDS)
          .connectTimeout(10, TimeUnit.SECONDS)
          .addInterceptor(authInterceptor)
          .apply {
            if (logSink != null) {
              addNetworkInterceptor(trafficLoggingInterceptor)
            }
          }
          .build()

  init {
    logSink?.invoke(
        "CONFIG baseUrl=$baseUrl bearer.len=${bearerToken.length} sha256=${bearerToken.fingerprint()}"
    )
  }

  suspend fun health(): HealthResponse =
      withContext(Dispatchers.IO) {
        executeJson(
            request = Request.Builder().url("$baseUrl/health").get().build(),
            deserializer = { body -> json.decodeFromString<HealthResponse>(body) },
        )
      }

  suspend fun diagnostics(): DiagnosticsResponse =
      withContext(Dispatchers.IO) {
        executeJson(
            request = Request.Builder().url("$baseUrl/diagnostics").get().build(),
            deserializer = { body -> json.decodeFromString<DiagnosticsResponse>(body) },
        )
      }

  suspend fun listModels(): List<ModelResponse> =
      withContext(Dispatchers.IO) {
        executeJson(
            request = Request.Builder().url("$baseUrl/models").get().build(),
            deserializer = { body -> json.decodeFromString<ModelsListResponse>(body).models },
        )
      }

  suspend fun createSession(
      modelId: String,
      appName: String? = null,
      systemPrompt: String? = null,
  ): SessionResponse =
      createSession(
          CreateSessionRequest(
              modelId = modelId,
              appName = appName,
              systemPrompt = systemPrompt,
          )
      )

  suspend fun createSession(
      appName: String? = null,
      systemPrompt: String? = null,
  ): SessionResponse =
      createSession(CreateSessionRequest(appName = appName, systemPrompt = systemPrompt))

  suspend fun createSession(request: CreateSessionRequest): SessionResponse =
      withContext(Dispatchers.IO) {
        val body = json.encodeToString(request).toRequestBody(jsonMediaType)
        executeJson(
            request = Request.Builder().url("$baseUrl/sessions").post(body).build(),
            deserializer = { responseBody -> json.decodeFromString<SessionResponse>(responseBody) },
        )
      }

  suspend fun getSession(sessionId: String): SessionDetailResponse =
      withContext(Dispatchers.IO) {
        executeJson(
            request = Request.Builder().url("$baseUrl/sessions/$sessionId").get().build(),
            deserializer = { responseBody ->
              json.decodeFromString<SessionDetailResponse>(responseBody)
            },
        )
      }

  suspend fun deleteSession(sessionId: String) =
      withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/sessions/$sessionId").delete().build()
        http.newCall(request).execute().use { response ->
          if (!response.isSuccessful && response.code != 404) {
            throw response.toAltioServiceException(json)
          }
        }
      }

  suspend fun generate(sessionId: String, userMessage: String): SubmitJobResponse =
      generate(
          sessionId = sessionId,
          request =
              GenerateRequest(messages = listOf(PromptMessage(MessageRole.USER, userMessage))),
      )

  suspend fun generate(sessionId: String, request: GenerateRequest): SubmitJobResponse =
      withContext(Dispatchers.IO) {
        val body = json.encodeToString(request).toRequestBody(jsonMediaType)
        executeJson(
            request =
                Request.Builder().url("$baseUrl/sessions/$sessionId/generate").post(body).build(),
            deserializer = { responseBody ->
              json.decodeFromString<SubmitJobResponse>(responseBody)
            },
        )
      }

  suspend fun transcribe(
      sessionId: String,
      audioBytes: ByteArray,
      mimeType: String = "audio/mpeg",
      fileName: String = "recording.mp3",
  ): SubmitJobResponse =
      withContext(Dispatchers.IO) {
        val audioBody = audioBytes.toRequestBody(mimeType.toMediaType())
        val multipart =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audio", fileName, audioBody)
                .build()
        executeJson(
            request =
                Request.Builder()
                    .url("$baseUrl/sessions/$sessionId/transcribe")
                    .post(multipart)
                    .build(),
            deserializer = { responseBody ->
              json.decodeFromString<SubmitJobResponse>(responseBody)
            },
        )
      }

  suspend fun pollJob(jobId: String): JobStatusResponse =
      withContext(Dispatchers.IO) {
        executeJson(
            request = Request.Builder().url("$baseUrl/jobs/$jobId").get().build(),
            deserializer = { body -> json.decodeFromString<JobStatusResponse>(body) },
        )
      }

  suspend fun cancelJob(jobId: String) =
      withContext(Dispatchers.IO) {
        val request =
            Request.Builder().url("$baseUrl/jobs/$jobId/cancel").post("".toRequestBody()).build()
        http.newCall(request).execute().use { response ->
          if (!response.isSuccessful && response.code != 404) {
            throw response.toAltioServiceException(json)
          }
        }
      }

  fun streamJob(jobId: String): Flow<JobStreamEvent> = flow {
    val request =
        Request.Builder()
            .url("$baseUrl/jobs/$jobId/stream")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .build()

    http.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw response.toAltioServiceException(json)
      val source = response.body.source()

      var currentEvent = ""
      var currentData = ""

      while (!source.exhausted()) {
        val line = source.readUtf8Line() ?: break
        when {
          line.startsWith(":") -> Unit
          line.startsWith("event:") -> currentEvent = line.removePrefix("event:").trim()
          line.startsWith("data:") -> currentData = line.removePrefix("data:").trim()
          line.isEmpty() && currentData.isNotEmpty() -> {
            val event = decodeStreamEvent(currentEvent, currentData)
            emit(event)
            currentEvent = ""
            currentData = ""
            if (event is JobStreamEvent.Done || event is JobStreamEvent.Error) return@flow
          }
        }
      }
    }
  }

  private fun decodeStreamEvent(eventName: String, data: String): JobStreamEvent =
      when (eventName) {
        "token" -> {
          val event = json.decodeFromString<TokenEvent>(data)
          JobStreamEvent.Token(text = event.text, index = event.index)
        }
        "done" -> {
          val event = json.decodeFromString<DoneEvent>(data)
          JobStreamEvent.Done(
              finishReason = event.finishReason,
              promptTokens = event.promptTokens,
              completionTokens = event.completionTokens,
          )
        }
        "error" -> {
          val event = json.decodeFromString<SseErrorEvent>(data)
          JobStreamEvent.Error(message = event.message)
        }
        else -> JobStreamEvent.Error(message = "Unknown stream event: $eventName")
      }

  private suspend fun <T> executeJson(request: Request, deserializer: (String) -> T): T =
      withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
          if (!response.isSuccessful) throw response.toAltioServiceException(json)
          deserializer(response.body.string())
        }
      }
}

private fun Response.toAltioServiceException(json: Json): AltioServiceException {
  val body = body.string()
  val parsedError =
      runCatching { json.decodeFromString<ApiErrorEnvelope>(body).error }
          .recoverCatching {
            if (body.isBlank()) throw it
            ApiError(code = ApiErrorCode.INTERNAL_ERROR, message = body)
          }
          .getOrNull()
          ?.let { error ->
            if (code == 401 && error.code == ApiErrorCode.INTERNAL_ERROR) {
              error.copy(code = ApiErrorCode.UNAUTHORIZED)
            } else {
              error
            }
          }
          ?: if (code == 401) {
            ApiError(ApiErrorCode.UNAUTHORIZED, "Unauthorized")
          } else {
            null
          }
  return createAltioServiceException(statusCode = code, apiError = parsedError)
}

private fun String.fingerprint(): String = encodeUtf8().sha256().hex().take(12)

internal fun Request.toLogString(): String {
  val body = body
  val headerText =
      headers.joinToString(separator = "\n") { header ->
        val value =
            if (header.first.equals("Authorization", ignoreCase = true)) "██" else header.second
        "${header.first}: $value"
      }
  val preamble = buildString {
    append("--> ")
    append(method)
    append(' ')
    append(url.encodedPath)
    if (headerText.isNotBlank()) {
      append('\n')
      append(headerText)
    }
  }

  if (body == null) return preamble

  val bodySummary =
      when {
        body is MultipartBody -> summarizeMultipartBody(body)
        body.contentType().isTextualForLogging() -> summarizeTextBody(body)
        else -> summarizeBinaryBody(body)
      }

  return "$preamble\n$bodySummary"
}

internal fun Response.toLogString(): String {
  val headerText = headers.joinToString(separator = "\n") { "${it.first}: ${it.second}" }
  val preamble = buildString {
    append("<-- ")
    append(code)
    append(' ')
    append(request.method)
    append(' ')
    append(request.url.encodedPath)
    if (headerText.isNotBlank()) {
      append('\n')
      append(headerText)
    }
  }

  val body = body
  if (body.contentLength() == 0L) return preamble

  val bodySummary =
      when {
        body.contentType().isTextualForLogging() -> {
          val peek = peekBody(MAX_LOG_PEEK_BYTES)
          val text = peek.string().trim()
          if (text.isBlank()) {
            "<empty response body>"
          } else {
            text
          }
        }
        else -> "<binary response body omitted>"
      }

  return "$preamble\n$bodySummary"
}

private fun summarizeMultipartBody(body: MultipartBody): String {
  val contentLength = body.contentLength()
  val parts =
      body.parts.joinToString(separator = "; ") { part ->
        val disposition = part.headers?.get("Content-Disposition").orEmpty()
        val contentType = part.body.contentType()?.toString().orEmpty()
        val name = disposition.substringAfter("name=\"", "").substringBefore('"').ifBlank { "part" }
        val fileName = disposition.substringAfter("filename=\"", "").substringBefore('"', "")
        val prefix = if (fileName.isBlank()) name else "$name=$fileName"
        if (part.body.contentType().isTextualForLogging()) {
          "$prefix (${contentType.ifBlank { "text" }})"
        } else {
          "$prefix (${contentType.ifBlank { "binary" }}, binary omitted)"
        }
      }
  val lengthLabel = if (contentLength >= 0) "$contentLength bytes" else "unknown size"
  return "<multipart body omitted: $lengthLabel; $parts>"
}

private fun summarizeTextBody(body: okhttp3.RequestBody): String {
  val buffer = Buffer()
  body.writeTo(buffer)
  val text = buffer.readUtf8().trim()
  return if (text.isBlank()) "<empty request body>" else text
}

private fun summarizeBinaryBody(body: okhttp3.RequestBody): String {
  val contentType = body.contentType()?.toString() ?: "application/octet-stream"
  val contentLength = body.contentLength()
  val lengthLabel = if (contentLength >= 0) "$contentLength bytes" else "unknown size"
  return "<binary request body omitted: $contentType, $lengthLabel>"
}

private fun okhttp3.MediaType?.isTextualForLogging(): Boolean {
  if (this == null) return false
  return type == "text" ||
      subtype.contains("json", ignoreCase = true) ||
      subtype.contains("xml", ignoreCase = true) ||
      subtype.contains("x-www-form-urlencoded", ignoreCase = true)
}

private const val MAX_LOG_PEEK_BYTES = 256 * 1024L
