package com.insurely.blocks

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.Error

class BlocksActivity : AppCompatActivity() {
    private val baseUrl =
        "https://blocks.test.insurely.com/"

    lateinit var webView: WebView

    private val instructionsHandler = InstructionsHandler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocks)

        webView = findViewById(R.id.webview)
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            setupWebView()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(true)
        val config = intent.getStringExtra("config")
        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.setSupportZoom(true)
        webView.settings.defaultTextEncodingName = "utf-8"
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return false

                //you can do checks here e.g. url.host equals to target one
                startActivity(Intent(Intent.ACTION_VIEW, url))
                return true
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                webView.loadUrl(
                    """
                    javascript:(function() {
                        const bootstrapScript = document.createElement('script');
                        console.log(bootstrapScript);
                        bootstrapScript.id = "insurely-bootstrap-script";
                        bootstrapScript.type = "text/javascript";
                        bootstrapScript.src = "${baseUrl}assets/mobile-bootstrap.js";
                        document.head.appendChild(bootstrapScript);
                    
                        window.insurely = ${config};
                        
                        if (window.Android) {
                            const originalPostMessage = window.postMessage;
                            window.postMessage = (data, ...rest) => {
                                originalPostMessage(data, ...rest);
                                Android.postMessage(JSON.stringify(data));
                            }
                         }
                   })()
                """.trimIndent()
                )

                super.onPageCommitVisible(view, url)
            }

        }

        webView.addJavascriptInterface(MessageHandler(blocksActivity = this), "Android")
        webView.loadUrl(baseUrl)
    }

    fun actOnMessage(value: Response) {
        val jsCode = """
        (function() {
            window.postMessage({ name: 'SUPPLEMENTAL_INFORMATION', value: ${value.toJsonString()} })
        })()
        """.trimIndent()
        Log.i("Postmessage out", jsCode)
        webView.evaluateJavascript(jsCode, null)
    }

    fun initBankID(autostartToken: String = "") {
        val intent = Intent()
        intent.setAction(Intent.ACTION_VIEW)
        intent.setData(
            Uri.parse("https://app.bankid.com/?autostarttoken=" + autostartToken + "&redirect=null")
        )
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    fun handleExtraInformation(jsonObject: JSONObject) {
        if (!jsonObject.has("INSTRUCTIONS")) {
            return
        }

        val instruction = jsonObject.getJSONObject("INSTRUCTIONS").toInstruction()
        lifecycleScope.launch {
            when (val result =
                withContext(Dispatchers.IO) { instructionsHandler.executeRequest(instruction) }) {
                is RequestResult.Success -> {
                    // Handle the successful response
                    Log.d("Response", result.response.toJsonString())
                    actOnMessage(result.response)
                }
                RequestResult.NotUniqueETag -> {
                    // Handle the case when the ETag is not unique
                    Log.d("Response", "ETag is not unique")
                }
                RequestResult.RequestFailed -> {
                    // Handle the case when the request fails
                    Log.d("Response", "Request failed")
                }
            }
        }
    }
}

internal class MessageHandler(blocksActivity: BlocksActivity) {
    private var activity: BlocksActivity = blocksActivity

    @JavascriptInterface
    fun postMessage(data: String?) {
        if (data == null) {
            return
        }
        try {
            val jObject = JSONObject(data)
            when (val name = jObject.getString("name")) {
                "APP_CLOSE" -> this.activity.finish()
                "OPEN_SWEDISH_BANKID" -> activity.initBankID(jObject.getString("value"))
                "COLLECTION_STATUS" -> {
                    if (jObject.has("extraInformation")) {
                        activity.handleExtraInformation(jObject.getJSONObject("extraInformation"))
                    }
                }
                else -> {
                    Log.i("postMessage", "No action associated with $name")
                }
            }
        } catch (e: Error) {
            e.message?.let { Log.i("postMessage: ", it) }
        }

        Log.i("postMessage", "postMessage data=$data")
    }
}
