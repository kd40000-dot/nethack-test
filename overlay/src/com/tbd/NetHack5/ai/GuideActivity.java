package com.tbd.NetHack5.ai;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.text.InputType;

import com.tbd.forkfront.ForkFront;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Uses the player's existing ChatGPT web login in Fennec. No OpenAI API key,
 * hidden browser cookies, embedded login interception or automated game input.
 * The prompt is constructed from actual visible NetHack state only.
 */
public final class GuideActivity extends ForkFront {
    private static final String FENNEC_PACKAGE = "org.mozilla.fennec_fdroid";
    private static final String CHATGPT_URL = "https://chatgpt.com/";
    private static final int MAX_PREFILL_URL_BYTES = 7600;

    private static final String QUICK_QUESTION =
        "I have never played NetHack before. Inspect my CURRENT game state and " +
        "proactively decide what I most need help with. Explain what I should do " +
        "in the next several turns to survive and make meaningful progress. " +
        "Explain the exact commands and why they matter.";

    private static final String COACHING_INSTRUCTIONS =
        "You are a patient, expert NetHack 5.0 tutor for a COMPLETE BEGINNER " +
        "playing JodiJodington's Android port. Analyze the real, read-only " +
        "visible game state below. The map is the previously discovered, " +
        "80-column rendered dungeon cache; spaces do NOT necessarily mean " +
        "explored, safe or traversable. The screenshot is NOT included. " +
        "Prioritize imminent threats: HP, hunger, adjacent monsters and " +
        "bad statuses. Distinguish observable facts from guesses. " +
        "Give: (1) situation overview, (2) prioritized next 3–7 safe actions, " +
        "(3) exact NetHack keys/extended commands and how to enter them on " +
        "Android, (4) inventory/status checks, (5) how and where to explore " +
        "next, and (6) useful beginner concepts explained without jargon. " +
        "If you need inventory or a screenshot to be confident, ask for it. " +
        "Never imply you saw unseen terrain, items or monsters; never claim " +
        "to control the game. Avoid actions that can get the character killed " +
        "without clearly warning me. If a command is not certain for " +
        "NetHack 5, say so. You can offer spoilers when needed to prevent " +
        "frustration, but avoid major late-game spoilers unless asked.";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        addHelpBar();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private View gameView(String name) {
        int id = getResources().getIdentifier(name, "id", getPackageName());
        return id == 0 ? null : findViewById(id);
    }

    private Button button(String title) {
        Button view = new Button(this);
        view.setText(title);
        view.setTextSize(12);
        view.setAllCaps(false);
        view.setMinHeight(0);
        view.setMinimumHeight(0);
        view.setPadding(dp(4), 0, dp(4), 0);
        return view;
    }

    private void addHelpBar() {
        View root = gameView("base_frame");
        if (!(root instanceof LinearLayout)) {
            Toast.makeText(this, "The NetHack help toolbar could not be added.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(Color.rgb(24, 30, 38));
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(2), dp(4), dp(2));

        Button quick = button("✦ Quick help");
        quick.setOnClickListener(v -> sendToFennec(QUICK_QUESTION));
        bar.addView(quick, new LinearLayout.LayoutParams(0, dp(42), 1));

        Button ask = button("Ask ChatGPT");
        ask.setOnClickListener(v -> showQuestion());
        bar.addView(ask, new LinearLayout.LayoutParams(0, dp(42), 1));

        ((LinearLayout) root).addView(bar, 0,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void showQuestion() {
        EditText input = new EditText(this);
        input.setHint("e.g. Should I fight? Where are the stairs? What should I eat?");
        input.setMinLines(3);
        input.setMaxLines(7);
        input.setSingleLine(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setPadding(dp(18), dp(10), dp(18), dp(10));
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Ask ChatGPT about NetHack")
                .setMessage("Your discovered map, displayed status and recent game messages will be included automatically.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open in Fennec", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String question = input.getText().toString().trim();
                    if (question.isEmpty()) {
                        input.setError("Enter a question");
                        return;
                    }
                    dialog.dismiss();
                    sendToFennec(question);
                }));
        dialog.show();
    }

    private String makePrompt(String question) {
        return COACHING_INSTRUCTIONS +
                "\n\nMY QUESTION:\n" + question +
                "\n\nCURRENT GAME STATE, CAPTURED AT THIS INSTANT:\n" +
                GuideSnapshot.capture(this) +
                "\n\nRespond with detailed, actionable guidance specific to " +
                "this position and current status. Do not invent missing information.";
    }

    private void sendToFennec(String question) {
        final String fullPrompt = makePrompt(question);
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            showProblem("Android clipboard is unavailable; cannot transfer game state.");
            return;
        }
        // Keep a complete, user-controlled fallback even if the website's
        // undocumented URL-prefill parameter stops working.
        clipboard.setPrimaryClip(ClipData.newPlainText("NetHack game help", fullPrompt));

        String destination = CHATGPT_URL;
        boolean prefilled = false;
        try {
            String encoded = URLEncoder.encode(fullPrompt, "UTF-8");
            String proposed = CHATGPT_URL + "?q=" + encoded;
            // Do not risk exceeding practical deep-link/browser URL limits.
            // In that case we open ChatGPT without query and the full prompt
            // is ready to paste manually.
            if (proposed.getBytes(StandardCharsets.UTF_8).length <= MAX_PREFILL_URL_BYTES) {
                destination = proposed;
                prefilled = true;
            }
        } catch (Exception ignored) {
            // Clipboard route remains available.
        }

        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(destination));
        open.addCategory(Intent.CATEGORY_BROWSABLE);
        open.setPackage(FENNEC_PACKAGE);
        final boolean usedPrefill = prefilled;
        try {
            startActivity(open);
            showOpenedMessage(usedPrefill);
        } catch (ActivityNotFoundException exception) {
            // Some forks have different package IDs. Do not rely on the
            // ChatGPT native app or require Google Play Services.
            open.setPackage(null);
            try {
                startActivity(open);
                showOpenedMessage(usedPrefill);
            } catch (ActivityNotFoundException exception2) {
                showProblem("No web browser could be opened. The full game-state " +
                        "prompt was copied; open https://chatgpt.com/ manually " +
                        "and paste it into a chat.");
            }
        } catch (SecurityException exception) {
            showProblem("Android blocked opening Fennec. Your game-state " +
                    "prompt is on the clipboard; open ChatGPT in your browser " +
                    "and paste it.");
        }
    }

    private void showOpenedMessage(boolean prefilled) {
        Toast.makeText(this, prefilled
                ? "ChatGPT opened with your game-state question. Check it, then tap Send. Full text also copied."
                : "Full game-state prompt copied. Paste in ChatGPT, then tap Send.",
                Toast.LENGTH_LONG).show();
    }

    private void showProblem(String message) {
        new AlertDialog.Builder(this).setTitle("ChatGPT web handoff")
                .setMessage(message).setPositiveButton("OK", null).show();
    }
}
