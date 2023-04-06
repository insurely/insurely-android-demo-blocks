package com.insurely.blocks

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.insurely.blocks.R

class MainActivity : AppCompatActivity() {
    private lateinit var dcButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dcButton = findViewById(R.id.dcButton)
        dcButton.setOnClickListener {
            val intent = Intent(this@MainActivity, BlocksActivity::class.java)
            intent.putExtra(
                "config", """
                {
                    config: {
                        customerId: 'replace-me',
                        configName: 'replace-me'
                    }
                }
            """.trimIndent()
            )
            startActivity(intent)
        }
    }
}
