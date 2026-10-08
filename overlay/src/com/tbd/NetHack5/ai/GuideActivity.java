package com.tbd.NetHack5.ai;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.tbd.forkfront.ForkFront;
import java.io.ByteArrayOutputStream;

/**
 * In-game, opt-in NetHack teacher. NetHack itself is never interrupted or
 * advanced: the help dialog merely covers the screen while the engine waits
 * for the player's next normal command.
 */
public final class GuideActivity extends ForkFront {
    private static final String QUICK_QUESTION =
            "I am new to NetHack. From my CURRENT situation, what should I do next " +
            "to survive and progress? Proactively guess the most useful things I " +
            "need help with. Give clear next actions and explain exactly how to " +
            "perform them on Android.";
    private boolean requestInFlight = false;
    private String lastQuestion = "";
    private String lastAnswer = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addHelpBar();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private View getGameView(String idName) {
        int id = getResources().getIdentifier(idName, "id", getPackageName());
        return id == 0 ? null : findViewById(id);
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(5), 0, dp(5), 0);
        return button;
    }

    private void addHelpBar() {
        View root = getGameView("base_frame");
        if (!(root instanceof LinearLayout)) {
            Toast.makeText(this, "AI toolbar unavailable: missing game layout",
                    Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.rgb(25, 30, 38));
        bar.setPadding(dp(3), dp(2), dp(3), dp(2));

        Button quick = button("✦ Quick help");
        quick.setOnClickListener(v -> startHelp(QUICK_QUESTION));
        bar.addView(quick, new LinearLayout.LayoutParams(
                0, dp(41), 1f));

        Button ask = button("Ask ChatGPT");
        ask.setOnClickListener(v -> showAskDialog());
        bar.addView(ask, new LinearLayout.LayoutParams(
                0, dp(41), 1f));

        Button config = button("AI key");
        config.setOnClickListener(v -> showKeyDialog(null));
        bar.addView(config, new LinearLayout.LayoutParams(
                dp(72), dp(41)));

        ((LinearLayout) root).addView(bar, 0,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void showAskDialog() {
        EditText question = new EditText(this);
        question.setHint("e.g. Am I in danger? Where should I go? What does this item do?");
        question.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        question.setSingleLine(false);
        question.setMinLines(3);
        question.setMaxLines(7);
        question.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("Ask about your current game")
                .setMessage("Your visible dungeon, status, and messages will be included automatically.")
                .setView(question)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Ask", (dialog, which) -> {
                    String text = question.getText().toString().trim();
                    if (text.isEmpty()) {
                        Toast.makeText(this, "Please enter a question",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        startHelp(text);
                    }
                }).show();
    }

    private void showKeyDialog(Runnable afterSave) {
        EditText keyEntry = new EditText(this);
        keyEntry.setSingleLine(true);
        keyEntry.setHint("sk-...");
        keyEntry.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyEntry.setPadding(dp(18), dp(10), dp(18), dp(10));
        String message = "Enter an OpenAI API key from platform.openai.com. " +
                "This is separate from ChatGPT Plus and may use paid API credits. " +
                "The key is encrypted using Android Keystore. " +
                "Game screenshots and state are uploaded ONLY when you request help.";
        if (GuideKeyStore.hasKey(this)) message +=
                "\n\nAn encrypted key is already saved. Enter a new one to replace it.";
        new AlertDialog.Builder(this)
                .setTitle("ChatGPT connection")
                .setMessage(message)
                .setView(keyEntry)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Forget key", (dialog, which) -> {
                    GuideKeyStore.forget(this);
                    Toast.makeText(this, "API key removed", Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        GuideKeyStore.save(this, keyEntry.getText().toString());
                        Toast.makeText(this, "API key securely saved",
                                Toast.LENGTH_SHORT).show();
                        if (afterSave != null) afterSave.run();
                    } catch (Exception e) {
                        new AlertDialog.Builder(this).setTitle("Could not save key")
                                .setMessage(safeError(e)).setPositiveButton("OK", null).show();
                    }
                }).show();
    }

    private void startHelp(String question) {
        if (requestInFlight) {
            Toast.makeText(this, "A help request is already running",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!GuideKeyStore.hasKey(this)) {
            showKeyDialog(() -> startHelp(question));
            return;
        }

        final String snapshot = GuideSnapshot.capture(this);
        final byte[] jpeg;
        try {
            jpeg = takeScreenshot();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Could not capture game")
                    .setMessage(safeError(e)).setPositiveButton("OK", null).show();
            return;
        }
        final String previous = lastAnswer.isEmpty() ? "" :
                "\n\nOPTIONAL CONVERSATION CONTEXT (from an earlier moment):\n" +
                "Previous question: " + truncate(lastQuestion, 700) +
                "\nPrevious answer: " + truncate(lastAnswer, 3500) +
                "\nThe new game screenshot/status take priority over this previous advice.";
        final String actualQuestion = question + previous;

        requestInFlight = true;
        ProgressBar progress = new ProgressBar(this);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(30), dp(26), dp(30), dp(26));
        wrap.setGravity(Gravity.CENTER);
        wrap.addView(progress);
        AlertDialog waiting = new AlertDialog.Builder(this)
                .setTitle("Analyzing your NetHack game")
                .setMessage("Reading the dungeon, player status, and recent messages…")
                .setView(wrap)
                .setCancelable(false)
                .create();
        waiting.show();

        new Thread(() -> {
            String reply = null;
            Exception failure = null;
            try {
                String apiKey = GuideKeyStore.load(getApplicationContext());
                reply = GuideClient.request(apiKey, actualQuestion, snapshot, jpeg);
            } catch (Exception exception) {
                failure = exception;
            }
            final String answer = reply;
            final Exception error = failure;
            runOnUiThread(() -> {
                requestInFlight = false;
                waiting.dismiss();
                if (isFinishing() || isDestroyed()) return;
                if (error != null) {
                    new AlertDialog.Builder(this).setTitle("ChatGPT request failed")
                            .setMessage(safeError(error))
                            .setNegativeButton("Close", null)
                            .setPositiveButton("Retry", (d, w) -> startHelp(question))
                            .show();
                } else {
                    lastQuestion = question;
                    lastAnswer = answer;
                    showAnswer(answer);
                }
            });
        }, "nethack-guide-api").start();
    }

    private static String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    private byte[] takeScreenshot() throws Exception {
        View mapAndHud = getGameView("dlg_frame");
        if (mapAndHud == null || mapAndHud.getWidth() < 1 || mapAndHud.getHeight() < 1) {
            throw new IllegalStateException("Game display not ready yet");
        }
        // The frontend renders its map through a custom View, so ordinary
        // View.draw() captures its content, unlike a SurfaceView snapshot.
        int width = mapAndHud.getWidth();
        int height = mapAndHud.getHeight();
        double scale = Math.min(1.0, Math.min(1080.0 / width, 1600.0 / height));
        int w = Math.max(1, (int) Math.round(width * scale));
        int h = Math.max(1, (int) Math.round(height * scale));
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.BLACK);
            canvas.scale((float) w / width, (float) h / height);
            mapAndHud.draw(canvas);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 76, out)) {
                throw new IllegalStateException("Screenshot encoding failed");
            }
            return out.toByteArray();
        } finally {
            bitmap.recycle();
        }
    }

    private void showAnswer(String answer) {
        TextView content = new TextView(this);
        content.setText(answer);
        content.setTextSize(16);
        content.setTextColor(Color.WHITE);
        content.setTypeface(Typeface.DEFAULT);
        content.setTextIsSelectable(true);
        content.setPadding(dp(16), dp(12), dp(16), dp(20));
        ScrollView scrolling = new ScrollView(this);
        scrolling.setFillViewport(true);
        scrolling.addView(content);
        new AlertDialog.Builder(this)
                .setTitle("NetHack guide")
                .setView(scrolling)
                .setNegativeButton("Close", null)
                .setPositiveButton("Ask follow-up", (d, w) -> showAskDialog())
                .show();
    }

    private static String safeError(Exception e) {
        // Never display an Authorization header or echo the API key.
        String message = e.getMessage();
        if (message == null || message.isEmpty()) message = e.getClass().getSimpleName();
        return message.length() > 650 ? message.substring(0, 650) : message;
    }
}
