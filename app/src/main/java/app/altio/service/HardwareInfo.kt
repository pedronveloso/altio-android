/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import android.os.Build
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext

object HardwareInfo {
  fun getCpuInfo(): String {
    val socModel = getSystemProperty("ro.soc.model")
    val boardPlatform = getSystemProperty("ro.board.platform")
    val hardware = Build.HARDWARE
    val board = Build.BOARD

    val chipName =
        listOfNotNull(socModel, boardPlatform, hardware, board).firstOrNull {
          it.isNotBlank() && !it.equals("unknown", ignoreCase = true)
        } ?: "Unknown SoC"

    val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown architecture"
    val cores = Runtime.getRuntime().availableProcessors()

    return "$chipName ($arch, $cores cores)"
  }

  @SuppressLint("PrivateApi")
  private fun getSystemProperty(key: String): String? {
    return try {
      val systemProperties = Class.forName("android.os.SystemProperties")
      val get = systemProperties.getMethod("get", String::class.java)
      val value = get.invoke(null, key) as String
      value.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
      null
    }
  }

  fun getGpuInfo(): String {
    return try {
      val egl = EGLContext.getEGL() as EGL10
      val dpy = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
      val vers = IntArray(2)
      egl.eglInitialize(dpy, vers)
      val configSpec =
          intArrayOf(
              EGL10.EGL_RED_SIZE,
              8,
              EGL10.EGL_GREEN_SIZE,
              8,
              EGL10.EGL_BLUE_SIZE,
              8,
              EGL10.EGL_DEPTH_SIZE,
              0,
              EGL10.EGL_NONE,
          )
      val configs = arrayOfNulls<EGLConfig>(1)
      val numConfig = IntArray(1)
      egl.eglChooseConfig(dpy, configSpec, configs, 1, numConfig)
      val config = configs[0]
      val attribList =
          intArrayOf(
              0x3098,
              2, // EGL_CONTEXT_CLIENT_VERSION = 2
              EGL10.EGL_NONE,
          )
      val ctx = egl.eglCreateContext(dpy, config, EGL10.EGL_NO_CONTEXT, attribList)
      val surfaceAttribs = intArrayOf(EGL10.EGL_WIDTH, 1, EGL10.EGL_HEIGHT, 1, EGL10.EGL_NONE)
      val surf = egl.eglCreatePbufferSurface(dpy, config, surfaceAttribs)
      egl.eglMakeCurrent(dpy, surf, surf, ctx)
      val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
      egl.eglMakeCurrent(dpy, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
      egl.eglDestroySurface(dpy, surf)
      egl.eglDestroyContext(dpy, ctx)
      egl.eglTerminate(dpy)
      renderer ?: "Unknown"
    } catch (e: Exception) {
      "Unknown (Error: ${e.message})"
    }
  }

  fun getRamInfo(context: Context): String {
    return try {
      val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      val memoryInfo = ActivityManager.MemoryInfo()
      activityManager.getMemoryInfo(memoryInfo)
      val totalGb = memoryInfo.totalMem / (1024 * 1024 * 1024.0)
      val availGb = memoryInfo.availMem / (1024 * 1024 * 1024.0)
      String.format(java.util.Locale.US, "Free: %.2f GB / Total: %.2f GB", availGb, totalGb)
    } catch (e: Exception) {
      "Unknown"
    }
  }
}
