package com.turboblindracer;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class GameActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private GameView gameView;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SharedPreferences prefs;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        prefs = getSharedPreferences("TurboBlindRacer", MODE_PRIVATE);
        tts = new TextToSpeech(this, this);

        FrameLayout root = new FrameLayout(this);
        gameView = new GameView(this, this);
        root.addView(gameView);
        setContentView(root);

        // Swipe controls for accessibility
        gameView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                gameView.lastTouchX = event.getX();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - gameView.lastTouchX;
                if (Math.abs(dx) > 80) {
                    if (dx < 0) {
                        gameView.moveLeft();
                        speak("چپ");
                    } else {
                        gameView.moveRight();
                        speak("راست");
                    }
                }
            }
            return true;
        });
    }

    public void speak(String text) {
        if (ttsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "game_tts");
        }
    }

    public void speakImportant(String text) {
        if (ttsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "game_important");
        }
    }

    public void onGameOver(int score) {
        int high = prefs.getInt("high_score", 0);
        if (score > high) {
            prefs.edit().putInt("high_score", score).apply();
            speakImportant(getString(R.string.new_high_score, score));
        } else {
            speakImportant(getString(R.string.crash, score));
        }
        // Return to menu after a short delay
        gameView.postDelayed(this::finish, 3500);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("fa", "IR"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US);
            }
            ttsReady = true;
            speakImportant(getString(R.string.game_started));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) gameView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.resume();
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
