package com.tbd.NetHack5.ai;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tbd.forkfront.ForkFront;
import com.tbd.forkfront.CmdPanelLayout;
import com.tbd.forkfront.Input;
import com.tbd.forkfront.NH_State;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Touch-first NetHack 5 HUD, inspired by the information hierarchy of
 * Shattered Pixel Dungeon. Original artwork and game mechanics aren't copied.
 *
 * Native NetHack owns all gameplay decisions: buttons send exactly the normal
 * user commands through ForkFront's existing input router. Item/direction
 * prompts are left to NetHack's interactive touchscreen UI.
 */
final class DungeonHud {
    private static final int BG = Color.rgb(29, 34, 34);
    private static final int FRAME = Color.rgb(119, 111, 83);
    private static final int FACE = Color.rgb(46, 52, 49);
    private static final int TEXT = Color.rgb(244, 235, 205);
    private static final int MUTED = Color.rgb(181, 181, 164);
    private static final int RED = Color.rgb(186, 63, 59);
    private static final int GREEN = Color.rgb(91, 156, 105);

    private final GuideActivity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable update = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refresh();
            handler.postDelayed(this, 1150);
        }
    };
    private boolean resumed;
    private boolean warnedUnready;
    private TextView hpLabel, floorLabel, ailmentLabel, promptLabel;
    private ProgressBar hpBar;
    private Button contextButton;
    private LinearLayout base;
    private String lastContext = "";
    private char contextKey = ':';

    private static final class Action {
        final String icon, label, detail;
        final char command;
        Action(String icon, String label, String detail, char command) {
            this.icon = icon;
            this.label = label;
            this.detail = detail;
            this.command = command;
        }
    }

    DungeonHud(GuideActivity activity) {
        this.activity = activity;
    }

    private int dp(float x) {
        return (int)(x * activity.getResources().getDisplayMetrics().density + .5f);
    }

    private GradientDrawable panel(int background, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(background);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), border);
        return d;
    }

    private TextView text(String text, int size, int color) {
        TextView v = new TextView(activity);
        v.setText(text);
        v.setTextColor(color);
        v.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        v.setTextSize(size);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private Button tile(String glyph, String caption) {
        Button b = new Button(activity);
        b.setText(glyph + "\n" + caption);
        b.setTextColor(TEXT);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(1), 0, dp(1), 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setBackground(panel(FACE, FRAME, 6));
        return b;
    }

    void attach() {
        int rootId = activity.getResources().getIdentifier("base_frame", "id",
                activity.getPackageName());
        View root = rootId == 0 ? null : activity.findViewById(rootId);
        if (!(root instanceof LinearLayout)) {
            Toast.makeText(activity, "Dungeon HUD unavailable on this layout",
                    Toast.LENGTH_LONG).show();
            return;
        }
        base = (LinearLayout) root;
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setBackground(panel(BG, FRAME, 0));
        top.setPadding(dp(7), dp(5), dp(7), dp(5));

        LinearLayout status = new LinearLayout(activity);
        status.setGravity(Gravity.CENTER_VERTICAL);
        TextView portrait = text("♜", 25, TEXT);
        portrait.setGravity(Gravity.CENTER);
        portrait.setBackground(panel(FACE, FRAME, 5));
        status.addView(portrait, new LinearLayout.LayoutParams(dp(39), dp(42)));

        LinearLayout vitals = new LinearLayout(activity);
        vitals.setOrientation(LinearLayout.VERTICAL);
        vitals.setPadding(dp(8), 0, dp(8), 0);
        hpLabel = text("HERO   HP --/--", 13, TEXT);
        vitals.addView(hpLabel);
        hpBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        hpBar.setIndeterminate(false);
        hpBar.setMax(100);
        hpBar.setProgress(100);
        hpBar.setProgressTintList(ColorStateList.valueOf(GREEN));
        hpBar.setProgressBackgroundTintList(ColorStateList.valueOf(FACE));
        vitals.addView(hpBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(9)));
        ailmentLabel = text("No status information yet", 10, MUTED);
        vitals.addView(ailmentLabel);
        status.addView(vitals, new LinearLayout.LayoutParams(0, dp(47), 1f));

        floorLabel = text("DLVL\n--", 12, TEXT);
        floorLabel.setGravity(Gravity.CENTER);
        floorLabel.setBackground(panel(FACE, FRAME, 5));
        status.addView(floorLabel, new LinearLayout.LayoutParams(dp(60), dp(46)));
        top.addView(status);
        // Always-visible equipment quick inspections. These ask the NetHack
        // engine what is currently equipped rather than caching stale guesses.
        LinearLayout equipped = new LinearLayout(activity);
        equipped.setPadding(0, dp(5), 0, 0);
        addSmall(equipped, "⚔ Weapon", ')');
        addSmall(equipped, "▣ Armor", '[');
        addSmall(equipped, "◇ Rings", '=');
        addSmall(equipped, "✦ Amulet", '"');
        top.addView(equipped);
        base.addView(top, 0, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bottom = new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(5), dp(5), dp(5), dp(6));
        bottom.setBackground(panel(BG, FRAME, 0));

        LinearLayout smart = new LinearLayout(activity);
        smart.setGravity(Gravity.CENTER_VERTICAL);
        promptLabel = text("Actions · Native NetHack controls", 11, MUTED);
        promptLabel.setPadding(dp(4), 0, dp(5), 0);
        smart.addView(promptLabel, new LinearLayout.LayoutParams(0, dp(30), 1f));
        contextButton = tile("✧", "Look");
        contextButton.setTextSize(11);
        contextButton.setOnClickListener(v -> send(contextKey));
        smart.addView(contextButton, new LinearLayout.LayoutParams(dp(105), dp(37)));
        bottom.addView(smart);

        LinearLayout actions = new LinearLayout(activity);
        actions.setPadding(0, dp(5), 0, 0);
        addMain(actions, "▤", "Bag", () -> send('i'));
        addMain(actions, "⚔", "Gear", this::gearMenu);
        addMain(actions, "✊", "Act", this::actionMenu);
        addMain(actions, "◇", "Explore", this::exploreMenu);
        addMain(actions, "✦", "Magic", this::magicMenu);
        addMain(actions, "☰", "More", this::moreMenu);
        bottom.addView(actions);
        int slot = Math.max(0, base.getChildCount() - 1);
        base.addView(bottom, slot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // Keep original NetHack D-pad and question dialogs, but hide the
        // old cluttered command-panel button grid. The keyboard remains
        // accessible from More for unusual commands.
        hideLegacyCommandGrid();
    }

    private void addSmall(LinearLayout row, String label, char action) {
        TextView b = text(label, 11, TEXT);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(2), dp(5), dp(2), dp(5));
        b.setBackground(panel(FACE, FRAME, 4));
        b.setOnClickListener(v -> send(action));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        row.addView(b, lp);
    }

    private void addMain(LinearLayout row, String glyph, String caption,
                         Runnable action) {
        Button b = tile(glyph, caption);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(59), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        row.addView(b, lp);
    }

    void resume() {
        resumed = true;
        handler.removeCallbacks(update);
        handler.postDelayed(update, 350);
    }

    void pause() {
        resumed = false;
        handler.removeCallbacks(update);
    }

    private NH_State state() {
        try {
            Field f = ForkFront.class.getDeclaredField("nhState");
            f.setAccessible(true);
            return (NH_State) f.get(null);
        } catch (Exception ex) {
            if (!warnedUnready) {
                warnedUnready = true;
                Toast.makeText(activity, "NetHack controls are not ready",
                        Toast.LENGTH_SHORT).show();
            }
            return null;
        }
    }

    private void send(char cmd) {
        NH_State s = state();
        if (s == null) return;
        // Use the SAME input path as ForkFront's native command panel.
        // NetHack/Android will handle item selection, direction selection and
        // confirmations normally; the UI must never guess item letters.
        EnumSet<Input.Modifier> modifiers = Input.modifiers();
        s.handleKeyDown(cmd, Input.nhKeyFromMod(cmd, modifiers),
                Input.toKeyCode(cmd), modifiers, 0, true);
    }

    private void hideLegacyCommandGrid() {
        int id = activity.getResources().getIdentifier("cmdPanelLayout1",
                "id", activity.getPackageName());
        if (id == 0) return;
        View v = activity.findViewById(id);
        if (v instanceof CmdPanelLayout) ((CmdPanelLayout) v).hide();
    }

    private void refresh() {
        if (base == null || hpLabel == null) return;
        hideLegacyCommandGrid();
        String first = displayText("nh_stat0");
        String second = displayText("nh_stat1");
        String all = first + " " + second;
        Matcher hp = Pattern.compile("\\bHP\\s*:?\\s*(\\d+)\\s*[/\\(]\\s*(\\d+)\\s*\\)?",
                Pattern.CASE_INSENSITIVE).matcher(all);
        if (hp.find()) {
            int current = integer(hp.group(1)), max = integer(hp.group(2));
            hpLabel.setText("HP  " + current + " / " + max);
            hpBar.setMax(Math.max(1, max));
            hpBar.setProgress(Math.min(max, Math.max(0, current)));
            hpBar.setProgressTintList(ColorStateList.valueOf(
                    current * 3 <= max ? RED : GREEN));
        } else {
            hpLabel.setText(first.length() > 34 ? first.substring(0, 34) : first);
        }

        Matcher floor = Pattern.compile("\\b(?:Dlvl|Dlv|Depth)\\s*:?\\s*(\\d+)",
                Pattern.CASE_INSENSITIVE).matcher(all);
        if (floor.find()) floorLabel.setText("DEPTH\n" + floor.group(1));
        else floorLabel.setText("NETHACK\n5.0");

        String warnings = "";
        for (String danger : new String[] { "Fainting", "Starved", "Weak",
                "Hungry", "Satiated", "Blind", "Conf", "Stun", "FoodPois",
                "Ill", "Burdened", "Stressed", "Strained", "Overtaxed" }) {
            if (Pattern.compile("\\b" + danger + "\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(all).find()) {
                if (!warnings.isEmpty()) warnings += " · ";
                warnings += danger;
            }
        }
        if (warnings.isEmpty()) {
            Matcher ac = Pattern.compile("\\bAC\\s*:?\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE).matcher(all);
            warnings = ac.find() ? "Armor Class " + ac.group(1) + " · tap gear to inspect" :
                    "Tap a shortcut to inspect equipment";
        }
        ailmentLabel.setText(warnings);

        ContextAction nearby = getNearbyAction();
        String suggestion = nearby.hint;
        char key = nearby.key;
        if (warnings.toLowerCase(Locale.ROOT).matches(".*\\b(hungry|weak|fainting)\\b.*")) {
            suggestion = "Hungry? Eat something safe.";
            key = 'e';
        } else if (hp.find(0)) {
            int current = integer(hp.group(1)), max = integer(hp.group(2));
            if (max > 0 && current * 3 <= max) {
                suggestion = "Low HP — inspect your options; avoid combat.";
                key = 'i';  // inspect inventory, never blindly rest at low HP
            }
        }
        if (!suggestion.equals(lastContext) || key != contextKey) {
            lastContext = suggestion;
            contextKey = key;
            promptLabel.setText(suggestion);
            contextButton.setText("✧\n" + nearbyLabel(key));
        }
    }

    private static String nearbyLabel(char key) {
        switch (key) {
            case 'o': return "Open";
            case '>': return "Descend";
            case '<': return "Ascend";
            case ',': return "Pick up";
            case 'e': return "Eat";
            case 'i': return "Bag";
            default: return "Look";
        }
    }

    private static final class ContextAction {
        String hint;
        char key;
        ContextAction(String hint, char key) { this.hint = hint; this.key = key; }
    }

    private ContextAction getNearbyAction() {
        try {
            NH_State s = state();
            if (s == null) return new ContextAction("Explore cautiously · tap tiles to move", ':');
            Field mapField = NH_State.class.getDeclaredField("mMap");
            mapField.setAccessible(true);
            Object map = mapField.get(s);
            Field positionField = map.getClass().getDeclaredField("mPlayerPos");
            positionField.setAccessible(true);
            Point p = (Point) positionField.get(map);
            Field tilesField = map.getClass().getDeclaredField("mTiles");
            tilesField.setAccessible(true);
            Object mapTiles = tilesField.get(map);
            if (p == null || p.y < 0 || p.y >= Array.getLength(mapTiles))
                return new ContextAction("Explore and examine unfamiliar tiles", ':');
            char under = glyph(mapTiles, p.x, p.y);
            if (under == '>') return new ContextAction("Stairs down here — descend when ready", '>');
            if (under == '<') return new ContextAction("Stairs up here — return if needed", '<');
            if ("$%!?/)=[\"(*".indexOf(under) >= 0)
                return new ContextAction("There may be an item here — inspect/pick up", ',');
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    if (glyph(mapTiles, p.x + dx, p.y + dy) == '+')
                        return new ContextAction("Door nearby — tap Open, choose a direction", 'o');
                }
            }
        } catch (Exception ignored) {
            // Map layout may change across future upstream releases.
        }
        return new ContextAction("Explore carefully · tap Look to examine your tile", ':');
    }

    private static char glyph(Object map, int x, int y) throws Exception {
        if (y < 0 || y >= Array.getLength(map)) return ' ';
        Object row = Array.get(map, y);
        if (row == null || x < 0 || x >= Array.getLength(row)) return ' ';
        Object tile = Array.get(row, x);
        if (tile == null) return ' ';
        Field f = tile.getClass().getDeclaredField("ch");
        f.setAccessible(true);
        char[] c = (char[]) f.get(tile);
        return c == null || c.length == 0 ? ' ' : c[0];
    }

    private static int integer(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private String displayText(String res) {
        int id = activity.getResources().getIdentifier(res, "id", activity.getPackageName());
        if (id != 0) {
            View v = activity.findViewById(id);
            if (v instanceof TextView) return ((TextView)v).getText().toString();
        }
        return "";
    }

    private void gearMenu() {
        showMenu("Equipment", "Tap a slot to view what NetHack says is currently equipped.",
            new Action("⚔", "Wielded weapon", "See weapon (or empty hands)", ')'),
            new Action("▣", "Worn armor", "See equipped armor and AC", '['),
            new Action("◇", "Worn rings", "Inspect both rings", '='),
            new Action("✧", "Worn amulet", "Inspect your amulet", '"'),
            new Action("⚒", "Used tools", "Inspect worn/used tools", '('),
            new Action("▤", "Full inventory", "View all your carried items", 'i'),
            new Action("⚔", "Wield", "Choose weapon from your bag", 'w'),
            new Action("▣", "Wear armor", "Select clothing or armor to wear", 'W'),
            new Action("◇", "Put on ring", "Select an accessory", 'P'),
            new Action("▣", "Take off armor", "Remove a worn piece", 'T'),
            new Action("◇", "Remove ring", "Remove an accessory", 'R'),
            new Action("✦", "Switch weapon", "Swap primary and alternate weapon", 'x')
        );
    }

    private void actionMenu() {
        showMenu("Common actions", "NetHack will ask you to choose items/directions where needed.",
            new Action("◉", "Pick up", "Take an item from your tile", ','),
            new Action("◈", "Look here", "Examine current tile", ':'),
            new Action("⚒", "Apply tool", "Use a key, lamp, horn, etc.", 'a'),
            new Action("◌", "Search", "Find secret doors and traps", 's'),
            new Action("☷", "Open door", "Choose adjacent door direction", 'o'),
            new Action("▣", "Close door", "Choose adjacent door direction", 'c'),
            new Action("♨", "Eat", "Choose food from inventory", 'e'),
            new Action("◉", "Drink", "Select a potion", 'q'),
            new Action("⌁", "Read", "Read scroll or spellbook", 'r'),
            new Action("▤", "Drop", "Drop an unwanted item", 'd'),
            new Action("⌛", "Wait turn", "Consume one turn — not always safe", '.'),
            new Action("◈", "Inspect map", "Look at visible surroundings", ';')
        );
    }

    private void exploreMenu() {
        showMenu("Explore & navigate", "Walking can be done by tapping the map. Stairs work only when standing on them.",
            new Action("↓", "Down stairs", "Descend from a staircase", '>'),
            new Action("↑", "Up stairs", "Return upstairs", '<'),
            new Action("⌖", "Travel", "Travel to a map location", '_'),
            new Action("◈", "Look nearby", "Identify a symbol or monster", ';'),
            new Action("◌", "Search", "Find hidden passages/traps", 's'),
            new Action("⊕", "Examine tile", "What's underfoot?", ':'),
            new Action("⌛", "Wait", "Pass one turn (may be dangerous)", '.')
        );
    }

    private void magicMenu() {
        showMenu("Fight & magic", "Use a command, then follow NetHack's item and direction prompts.",
            new Action("➶", "Fire", "Fire prepared ammunition", 'f'),
            new Action("↗", "Throw", "Throw a carried item", 't'),
            new Action("✧", "Zap wand", "Choose a wand and direction", 'z'),
            new Action("✦", "Cast spell", "Choose learned spell", 'Z'),
            new Action("♨", "Drink potion", "Quaff a potion", 'q'),
            new Action("⌁", "Read scroll", "Read a scroll/spellbook", 'r'),
            new Action("⚔", "Wield", "Choose a weapon", 'w'),
            new Action("✊", "Kick", "Kick an object/door", '\004')
        );
    }

    private void moreMenu() {
        final String[] labels = {
            "✦ Quick ChatGPT help in Fennec",
            "✎ Ask ChatGPT a question",
            "⌨ Legacy virtual keyboard",
            "⚙ NetHack settings",
            "◈ NetHack help",
            "▤ Inventory",
            "☰ More NetHack commands",
            "⎋ Cancel current NetHack prompt"
        };
        new AlertDialog.Builder(activity).setTitle("More")
            .setItems(labels, (dialog, index) -> {
                switch (index) {
                    case 0: activity.quickHelp(); break;
                    case 1: activity.askHelp(); break;
                    case 2: {
                        NH_State s = state();
                        if (s != null) s.showKeyboard();
                        break;
                    }
                    case 3: {
                        NH_State s = state();
                        if (s != null) s.startPreferences();
                        break;
                    }
                    case 4: send('?'); break;
                    case 5: send('i'); break;
                    case 6: send('#'); break;
                    case 7: send('\033'); break;
                    default: break;
                }
            }).show();
    }

    private void showMenu(String title, String explanation, Action... entries) {
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(8), dp(10), dp(6));
        list.setBackgroundColor(BG);
        TextView heading = text(explanation, 12, MUTED);
        heading.setPadding(dp(8), dp(6), dp(8), dp(10));
        list.addView(heading);

        final AlertDialog[] dialog = new AlertDialog[1];
        for (Action entry : entries) {
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(7), dp(10), dp(7));
            row.setBackground(panel(FACE, FRAME, 5));
            TextView icon = text(entry.icon, 23, TEXT);
            icon.setGravity(Gravity.CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(43), dp(43)));
            LinearLayout label = new LinearLayout(activity);
            label.setOrientation(LinearLayout.VERTICAL);
            label.setPadding(dp(6), 0, 0, 0);
            label.addView(text(entry.label, 15, TEXT));
            label.addView(text(entry.detail, 11, MUTED));
            row.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView arrow = text("›", 22, MUTED);
            row.addView(arrow);
            row.setOnClickListener(v -> {
                if (dialog[0] != null) dialog[0].dismiss();
                send(entry.command);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(5));
            list.addView(row, lp);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(list);
        dialog[0] = new AlertDialog.Builder(activity)
                .setTitle(title).setView(scroll)
                .setNegativeButton("Back", null).create();
        dialog[0].show();
    }
}
