package com.turboblindracer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Core game engine - highly accessible endless car racer.
 * Three lanes, many power-ups, audio + haptic feedback for blind players.
 */
public class GameView extends SurfaceView implements Runnable {

    private Thread gameThread;
    private volatile boolean playing = false;
    private volatile boolean gameOver = false;

    private SurfaceHolder holder;
    private Paint paint;
    private final GameActivity activity;

    // Screen
    private int screenW, screenH;
    private float laneWidth;
    private final int NUM_LANES = 3;

    // Player
    private int playerLane = 1; // 0 left, 1 center, 2 right
    private float playerY;
    private float playerW, playerH;
    private boolean hasShield = false;
    private boolean hasMagnet = false;
    private long shieldUntil = 0;
    private long magnetUntil = 0;
    private long nitroUntil = 0;

    // World
    private float scrollSpeed = 12f;
    private float baseSpeed = 12f;
    private float roadOffset = 0;
    private int score = 0;
    private int distance = 0;
    private long lastObstacleTime = 0;
    private long lastItemTime = 0;
    private long gameStartTime;

    // Entities
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();
    private final Random random = new Random();

    // Audio / Haptic
    private SoundPool soundPool;
    private int soundEngine, soundCoin, soundCrash, soundPower, soundWarning;
    private Vibrator vibrator;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Touch
    public float lastTouchX = 0;

    // Accessibility state
    private long lastWarningTime = 0;
    private int lastAnnouncedLane = -1;

    public GameView(Context context, GameActivity activity) {
        super(context);
        this.activity = activity;
        holder = getHolder();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        initSounds();
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("صفحه بازی ماشین‌سواری. با کشیدن به چپ و راست ماشین را کنترل کنید.");
    }

    private void initSounds() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(attrs)
                .build();

        soundEngine = 0;
        soundCoin = 0;
        soundCrash = 0;
        soundPower = 0;
        soundWarning = 0;
    }

    @Override
    public void run() {
        gameStartTime = System.currentTimeMillis();
        long lastFrame = System.nanoTime();
        while (playing) {
            long now = System.nanoTime();
            float delta = (now - lastFrame) / 1_000_000_000f;
            lastFrame = now;

            if (!gameOver) {
                update(delta);
            }
            draw();
            try {
                Thread.sleep(16);
            } catch (InterruptedException ignored) {}
        }
    }

    private void update(float delta) {
        boolean nitroActive = System.currentTimeMillis() < nitroUntil;
        scrollSpeed = nitroActive ? baseSpeed * 1.8f : baseSpeed;
        baseSpeed += 0.003f;

        roadOffset += scrollSpeed;
        if (roadOffset > 80) roadOffset -= 80;
        distance += (int) scrollSpeed;
        score = distance / 10 + collectedBonus;

        long now = System.currentTimeMillis();
        if (hasShield && now > shieldUntil) {
            hasShield = false;
            activity.speak("سپر تمام شد");
        }
        if (hasMagnet && now > magnetUntil) {
            hasMagnet = false;
            activity.speak("آهنربا تمام شد");
        }

        if (now - lastObstacleTime > Math.max(700, 1800 - (int)(baseSpeed * 40))) {
            spawnObstacle();
            lastObstacleTime = now;
        }

        if (now - lastItemTime > 2200) {
            spawnItem();
            lastItemTime = now;
        }

        Iterator<Obstacle> oIt = obstacles.iterator();
        while (oIt.hasNext()) {
            Obstacle o = oIt.next();
            o.y += scrollSpeed;
            if (o.y > screenH + 100) {
                oIt.remove();
                continue;
            }
            if (o.lane == playerLane && o.y > playerY - 350 && o.y < playerY - 80) {
                if (now - lastWarningTime > 1200) {
                    activity.speakImportant("مانع نزدیک!");
                    vibrateShort();
                    lastWarningTime = now;
                }
            }
            if (checkCollision(o)) {
                if (hasShield) {
                    hasShield = false;
                    activity.speak("سپر نجات داد!");
                    oIt.remove();
                    vibrateShort();
                } else {
                    gameOver = true;
                    vibrateLong();
                    activity.onGameOver(score);
                    return;
                }
            }
        }

        Iterator<Item> iIt = items.iterator();
        while (iIt.hasNext()) {
            Item it = iIt.next();
            it.y += scrollSpeed;
            if (it.y > screenH + 50) {
                iIt.remove();
                continue;
            }
            if (hasMagnet && Math.abs(it.lane - playerLane) <= 1) {
                it.lane = playerLane;
            }
            if (it.lane == playerLane && Math.abs(it.y - playerY) < playerH) {
                collectItem(it);
                iIt.remove();
            }
        }

        if (playerLane != lastAnnouncedLane) {
            lastAnnouncedLane = playerLane;
        }
    }

    private int collectedBonus = 0;

    private void collectItem(Item it) {
        switch (it.type) {
            case COIN:
                collectedBonus += 50;
                activity.speak("سکه. امتیاز " + score);
                vibrateShort();
                break;
            case NITRO:
                nitroUntil = System.currentTimeMillis() + 4000;
                activity.speakImportant("نیترو فعال!");
                vibrateShort();
                break;
            case SHIELD:
                hasShield = true;
                shieldUntil = System.currentTimeMillis() + 6000;
                activity.speakImportant("سپر فعال!");
                vibrateShort();
                break;
            case MAGNET:
                hasMagnet = true;
                magnetUntil = System.currentTimeMillis() + 7000;
                activity.speakImportant("آهنربا فعال!");
                vibrateShort();
                break;
        }
    }

    private void spawnObstacle() {
        int lane = random.nextInt(NUM_LANES);
        if (random.nextFloat() < 0.3f) lane = playerLane;
        obstacles.add(new Obstacle(lane, -150));
    }

    private void spawnItem() {
        int lane = random.nextInt(NUM_LANES);
        Item.Type type;
        float r = random.nextFloat();
        if (r < 0.55f) type = Item.Type.COIN;
        else if (r < 0.75f) type = Item.Type.NITRO;
        else if (r < 0.90f) type = Item.Type.SHIELD;
        else type = Item.Type.MAGNET;
        items.add(new Item(lane, -80, type));
    }

    private boolean checkCollision(Obstacle o) {
        if (o.lane != playerLane) return false;
        float playerTop = playerY;
        float playerBottom = playerY + playerH;
        float oTop = o.y;
        float oBottom = o.y + playerH * 0.9f;
        return playerBottom > oTop && playerTop < oBottom;
    }

    private void draw() {
        if (!holder.getSurface().isValid()) return;
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) return;

        try {
            canvas.drawColor(Color.parseColor("#1A1A2E"));

            float roadLeft = screenW * 0.1f;
            float roadRight = screenW * 0.9f;
            paint.setColor(Color.parseColor("#2C2C2C"));
            canvas.drawRect(roadLeft, 0, roadRight, screenH, paint);

            paint.setColor(Color.parseColor("#FFEB3B"));
            paint.setStrokeWidth(6);
            for (int i = 1; i < NUM_LANES; i++) {
                float x = roadLeft + i * laneWidth;
                for (float y = -80 + (roadOffset % 80); y < screenH; y += 80) {
                    canvas.drawLine(x, y, x, y + 40, paint);
                }
            }

            paint.setColor(Color.parseColor("#FF6B00"));
            paint.setStrokeWidth(10);
            canvas.drawLine(roadLeft, 0, roadLeft, screenH, paint);
            canvas.drawLine(roadRight, 0, roadRight, screenH, paint);

            for (Item it : items) {
                float cx = laneCenterX(it.lane);
                paint.setColor(it.color());
                if (it.type == Item.Type.COIN) {
                    canvas.drawCircle(cx, it.y, 28, paint);
                } else {
                    RectF r = new RectF(cx - 30, it.y - 25, cx + 30, it.y + 25);
                    canvas.drawRoundRect(r, 12, 12, paint);
                }
            }

            for (Obstacle o : obstacles) {
                float cx = laneCenterX(o.lane);
                paint.setColor(Color.parseColor("#F44336"));
                RectF body = new RectF(cx - playerW / 2, o.y, cx + playerW / 2, o.y + playerH * 0.85f);
                canvas.drawRoundRect(body, 16, 16, paint);
                paint.setColor(Color.parseColor("#B71C1C"));
                canvas.drawRect(cx - playerW / 3, o.y + 15, cx + playerW / 3, o.y + 40, paint);
            }

            float pcx = laneCenterX(playerLane);
            paint.setColor(hasShield ? Color.parseColor("#9C27B0") : Color.parseColor("#2196F3"));
            RectF pBody = new RectF(pcx - playerW / 2, playerY, pcx + playerW / 2, playerY + playerH);
            canvas.drawRoundRect(pBody, 18, 18, paint);
            paint.setColor(Color.parseColor("#1565C0"));
            canvas.drawRect(pcx - playerW / 3, playerY + 20, pcx + playerW / 3, playerY + 55, paint);
            if (System.currentTimeMillis() < nitroUntil) {
                paint.setColor(Color.parseColor("#00E676"));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(8);
                canvas.drawRoundRect(pBody, 18, 18, paint);
                paint.setStyle(Paint.Style.FILL);
            }

            paint.setColor(Color.WHITE);
            paint.setTextSize(42);
            paint.setFakeBoldText(true);
            canvas.drawText("امتیاز: " + score, 40, 70, paint);
            canvas.drawText("سرعت: " + (int) scrollSpeed, 40, 120, paint);

            float indY = 180;
            if (hasShield) {
                paint.setColor(Color.parseColor("#9C27B0"));
                canvas.drawText("سپر", 40, indY, paint);
                indY += 45;
            }
            if (hasMagnet) {
                paint.setColor(Color.parseColor("#FF5722"));
                canvas.drawText("آهنربا", 40, indY, paint);
                indY += 45;
            }
            if (System.currentTimeMillis() < nitroUntil) {
                paint.setColor(Color.parseColor("#00E676"));
                canvas.drawText("نیترو", 40, indY, paint);
            }

            if (gameOver) {
                paint.setColor(Color.argb(180, 0, 0, 0));
                canvas.drawRect(0, 0, screenW, screenH, paint);
                paint.setColor(Color.WHITE);
                paint.setTextSize(64);
                canvas.drawText("بازی تمام!", screenW / 2f - 160, screenH / 2f, paint);
                paint.setTextSize(48);
                canvas.drawText("امتیاز: " + score, screenW / 2f - 120, screenH / 2f + 80, paint);
            }

        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private float laneCenterX(int lane) {
        float roadLeft = screenW * 0.1f;
        return roadLeft + lane * laneWidth + laneWidth / 2f;
    }

    public void moveLeft() {
        if (playerLane > 0) {
            playerLane--;
            vibrateShort();
        }
    }

    public void moveRight() {
        if (playerLane < NUM_LANES - 1) {
            playerLane++;
            vibrateShort();
        }
    }

    private void vibrateShort() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private void vibrateLong() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenW = w;
        screenH = h;
        laneWidth = (screenW * 0.8f) / NUM_LANES;
        playerW = laneWidth * 0.55f;
        playerH = playerW * 1.6f;
        playerY = screenH * 0.72f;
    }

    public void resume() {
        playing = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    public void pause() {
        playing = false;
        try {
            if (gameThread != null) gameThread.join(300);
        } catch (InterruptedException ignored) {}
    }

    static class Obstacle {
        int lane;
        float y;
        Obstacle(int lane, float y) {
            this.lane = lane;
            this.y = y;
        }
    }

    static class Item {
        enum Type { COIN, NITRO, SHIELD, MAGNET }
        int lane;
        float y;
        Type type;
        Item(int lane, float y, Type type) {
            this.lane = lane;
            this.y = y;
            this.type = type;
        }
        int color() {
            switch (type) {
                case COIN: return Color.parseColor("#FFD700");
                case NITRO: return Color.parseColor("#00E676");
                case SHIELD: return Color.parseColor("#9C27B0");
                case MAGNET: return Color.parseColor("#FF5722");
                default: return Color.WHITE;
            }
        }
    }
}
