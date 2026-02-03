package com.example.flatflex;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsExportHelper {

    private static final String PREFS = "flatflex_prefs";

    public static void exportSettings(Context ctx) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            String name = (user != null && user.getDisplayName() != null) ? user.getDisplayName() : "";
            String email = (user != null && user.getEmail() != null) ? user.getEmail() : "";

            String flatName = prefs.getString("flat_name", "");
            String joinCode = prefs.getString("join_code", "");
            String theme = prefs.getString("theme_mode", "system");
            boolean notifs = prefs.getBoolean("notifs_enabled", false);
            int hour = prefs.getInt("notif_hour", 19);
            int min = prefs.getInt("notif_min", 0);

            String csv =
                    "key,value\n" +
                    "name," + escape(name) + "\n" +
                    "email," + escape(email) + "\n" +
                    "flat_name," + escape(flatName) + "\n" +
                    "join_code," + escape(joinCode) + "\n" +
                    "theme_mode," + escape(theme) + "\n" +
                    "notifications_enabled," + notifs + "\n" +
                    "notification_time," + String.format(Locale.getDefault(), "%02d:%02d", hour, min) + "\n";

            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File out = new File(ctx.getFilesDir(), "flatflex_settings_" + ts + ".csv");

            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(csv.getBytes(StandardCharsets.UTF_8));
            }

            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", out);

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/csv");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            ctx.startActivity(Intent.createChooser(share, "Export settings"));
        } catch (Exception e) {
            Toast.makeText(ctx, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String escape(String s) {
        if (s == null) return "";

        // CSV escaping (RFC 4180-style):
        // - Double any embedded quotes
        // - Wrap in quotes if the field contains comma, quote, or newline
        String t = s.replace("\"", "\"\"");
        if (t.contains(",") || t.contains("\"") || t.contains("\n") || t.contains("\r")) {
            return "\"" + t + "\"";
        }
        return t;
    }
}
