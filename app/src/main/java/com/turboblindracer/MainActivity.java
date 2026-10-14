package com.turboblindracer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private TextView highScoreText;
    private SharedPreferences prefs;
    private boolean ttsReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("TurboBlindRacer", MODE_PRIVATE);
        highScoreText = findViewById(R.id.highScoreText);
        Button startButton = findViewById(R.id.startButton);
        Button howtoButton = findViewById(R.id.howtoButton);

        updateHighScore();

        tts = new TextToSpeech(this, this);

        startButton.setOnClickListener(v -> {
            speak("شروع بازی");
            Intent intent = new Intent(MainActivity.this, GameActivity.class);
            startActivity(intent);
        });

        howtoButton.setOnClickListener(v -> showHowToPlay());

        // Announce welcome after short delay for TalkBack
        highScoreText.postDelayed(() -> {
            if (ttsReady) {
                speak(getString(R.string.welcome));
            }
        }, 800);
    }

    private void updateHighScore() {
        int hs = prefs.getInt("high_score", 0);
        highScoreText.setText(getString(R.string.high_score, hs));
        highScoreText.setContentDescription(getString(R.string.high_score, hs));
    }

    private void showHowToPlay() {
        String guide = "راهنمای بازی توربو بلایند ریسر:\n\n" +
                "۱. با کشیدن انگشت به چپ یا راست، ماشین را بین سه لایه حرکت دهید.\n" +
                "۲. از ماشین‌های قرمز و موانع دوری کنید.\n" +
                "۳. سکه‌های طلایی را جمع کنید تا امتیاز بگیرید.\n" +
                "۴. آیتم‌های ویژه:\n" +
                "   - سبز: نیترو (سرعت موقت)\n" +
                "   - بنفش: سپر (محافظت از یک تصادف)\n" +
                "   - نارنجی: آهنربا (جمع‌آوری خودکار سکه‌ها)\n" +
                "۵. بازی کاملاً با TalkBack و هشدار صوتی کار می‌کند.\n" +
                "۶. هنگام نزدیک شدن مانع، هشدار صوتی می‌شنوید.\n" +
                "۷. امتیاز و رویدادها با صدای بلند اعلام می‌شوند.";

        new AlertDialog.Builder(this)
                .setTitle("نحوه بازی")
                .setMessage(guide)
                .setPositiveButton("متوجه شدم", (d, w) -> speak("راهنما بسته شد"))
                .show();
        speak(guide);
    }

    private void speak(String text) {
        if (ttsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id");
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("fa", "IR"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US); // fallback
            }
            ttsReady = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHighScore();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
