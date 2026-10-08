package com.tbd.NetHack5.ai;

import android.app.Activity;
import android.graphics.Point;
import android.view.View;
import android.widget.TextView;
import com.tbd.forkfront.ForkFront;
import java.lang.reflect.Array;
import java.lang.reflect.Field;

/**
 * Read-only snapshot of the real running NetHack frontend. The pinned
 * ForkFront 2.8 implementation stores the complete *already-seen* map in
 * NHW_Map.mTiles, player location in mPlayerPos, and recent message history
 * in NHW_Message. No game commands are injected.
 *
 * Reflection intentionally degrades gracefully if a newer frontend changes
 * these private fields; visible status + screenshot still work.
 */
final class GuideSnapshot {
    private GuideSnapshot() {}

    static String capture(Activity activity) {
        StringBuilder state = new StringBuilder();
        state.append("NetHack version: 5.0\n");
        appendText(state, activity, "nh_stat0", "Status row 1");
        appendText(state, activity, "nh_stat1", "Status row 2");
        appendText(state, activity, "nh_message", "Visible game message");

        try {
            Field stateField = ForkFront.class.getDeclaredField("nhState");
            stateField.setAccessible(true);
            Object nhState = stateField.get(null);
            if (nhState == null) {
                state.append("\nThe game has not started yet.\n");
                return state.toString();
            }
            appendMessageHistory(state, get(nhState, "mMessage"));
            appendDungeonMap(state, get(nhState, "mMap"));
        } catch (Exception error) {
            state.append("\nExtended structured state unavailable: ")
                    .append(error.getClass().getSimpleName())
                    .append(". Use screenshot + visible status only.\n");
        }
        return state.toString();
    }

    private static void appendText(StringBuilder out, Activity activity,
                                   String resourceName, String label) {
        int id = activity.getResources().getIdentifier(resourceName, "id",
                activity.getPackageName());
        View v = id == 0 ? null : activity.findViewById(id);
        if (v instanceof TextView && ((TextView) v).length() > 0) {
            out.append(label).append(": ")
                    .append(((TextView) v).getText())
                    .append('\n');
        }
    }

    private static Object get(Object object, String fieldName) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void appendMessageHistory(StringBuilder out,
                                              Object message) throws Exception {
        if (message == null) return;
        String[] log = (String[]) get(message, "mLog");
        int count = ((Number) get(message, "mLogCount")).intValue();
        int current = ((Number) get(message, "mCurrentIdx")).intValue();
        if (log == null || log.length == 0 || count <= 0) return;
        int length = Math.min(Math.min(count, 30), log.length);
        out.append("\nMost recent messages (oldest first):\n");
        for (int i = length - 1; i >= 0; i--) {
            int index = (current - i + log.length) % log.length;
            String line = log[index];
            if (line != null && !line.trim().isEmpty()) {
                out.append("- ").append(line).append('\n');
            }
        }
    }

    private static void appendDungeonMap(StringBuilder out,
                                          Object map) throws Exception {
        if (map == null) return;
        Object tiles = get(map, "mTiles");
        if (tiles == null) return;
        Point player = (Point) get(map, "mPlayerPos");
        if (player != null) {
            out.append("\nPlayer map coordinates (0-based): ")
                    .append(player.x).append(", ").append(player.y).append('\n');
        }
        out.append("\n80-column dungeon glyph map (the current discovered state; ");
        out.append("spaces/unseen cells are NOT necessarily empty terrain):\n");
        int rows = Math.min(21, Array.getLength(tiles));
        for (int y = 0; y < rows; y++) {
            Object row = Array.get(tiles, y);
            if (row == null) continue;
            int columns = Math.min(80, Array.getLength(row));
            for (int x = 0; x < columns; x++) {
                Object tile = Array.get(row, x);
                char glyph = ' ';
                if (tile != null) {
                    char[] charArray = (char[]) get(tile, "ch");
                    if (charArray != null && charArray.length > 0) glyph = charArray[0];
                }
                out.append(Character.isISOControl(glyph) ? ' ' : glyph);
            }
            out.append('\n');
        }
        out.append("IMPORTANT: the text map is an internal display cache, not a ");
        out.append("source of omniscient dungeon information. Tile glyphs may ");
        out.append("be ambiguous; consult screenshot and messages too.\n");
    }
}
