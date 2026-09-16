package com.redplay.url;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private EditText urlField, userField, passField;
    private ProgressBar progress;
    private LinearLayout root;
    private String baseUrl, username, password;
    private final ArrayList<Channel> channels = new ArrayList<>();
    private ExoPlayer player;

    static class Channel {
        final String name;
        final int id;
        final String ext;
        Channel(String name, int id, String ext) {
            this.name = name;
            this.id = id;
            this.ext = (ext == null || ext.isEmpty()) ? "ts" : ext;
        }
        @Override public String toString() { return name; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showLogin();
    }

    private TextView title(String text, int sp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        t.setPadding(12, 12, 12, 12);
        return t;
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0xffaaaaaa);
        e.setSingleLine(true);
        e.setInputType(inputType);
        e.setPadding(24, 16, 24, 16);
        e.setBackgroundColor(0xff242424);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 10, 0, 10);
        e.setLayoutParams(lp);
        return e;
    }

    private void showLogin() {
        releasePlayer();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 30, 48, 30);
        root.setBackgroundColor(0xff101010);

        TextView logo = title("RED PLAY", 28);
        root.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView sub = title("Entre com os dados do seu servidor", 16);
        sub.setTextColor(0xffbdbdbd);
        root.addView(sub);

        urlField = field("URL do servidor  •  Ex.: http://servidor.com:8080", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        userField = field("Usuário", InputType.TYPE_CLASS_TEXT);
        passField = field("Senha", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(urlField);
        root.addView(userField);
        root.addView(passField);

        Button login = new Button(this);
        login.setText("ENTRAR");
        login.setTextSize(18);
        login.setAllCaps(true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(0, 18, 0, 8);
        root.addView(login, blp);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress);

        login.setOnClickListener(v -> doLogin());
        setContentView(root);
    }

    private void doLogin() {
        String rawUrl = urlField.getText().toString().trim();
        username = userField.getText().toString().trim();
        password = passField.getText().toString().trim();
        if (rawUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Preencha URL, usuário e senha", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) rawUrl = "http://" + rawUrl;
        while (rawUrl.endsWith("/")) rawUrl = rawUrl.substring(0, rawUrl.length() - 1);
        baseUrl = rawUrl;
        progress.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                String u = URLEncoder.encode(username, StandardCharsets.UTF_8.name());
                String p = URLEncoder.encode(password, StandardCharsets.UTF_8.name());
                String loginJson = get(baseUrl + "/player_api.php?username=" + u + "&password=" + p);
                JSONObject obj = new JSONObject(loginJson);
                JSONObject ui = obj.optJSONObject("user_info");
                boolean ok = ui != null && (ui.optInt("auth", 0) == 1 || "1".equals(ui.optString("auth")));
                if (!ok) throw new Exception("Usuário, senha ou URL inválidos");

                String live = get(baseUrl + "/player_api.php?username=" + u + "&password=" + p + "&action=get_live_streams");
                JSONArray arr = new JSONArray(live);
                channels.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    int id = c.optInt("stream_id", -1);
                    if (id < 0) continue;
                    String name = c.optString("name", "Canal " + id);
                    String ext = c.optString("container_extension", "ts");
                    channels.add(new Channel(name, id, ext));
                }
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    if (channels.isEmpty()) Toast.makeText(this, "Login aceito, mas nenhum canal foi encontrado", Toast.LENGTH_LONG).show();
                    showChannels();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Erro de conexão: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "RedPlay/1.0 Android");
        int code = c.getResponseCode();
        InputStream in = (code >= 200 && code < 400) ? c.getInputStream() : c.getErrorStream();
        if (in == null) throw new Exception("HTTP " + code);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        c.disconnect();
        if (code < 200 || code >= 400) throw new Exception("HTTP " + code);
        return sb.toString();
    }

    private void showChannels() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff101010);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(12, 8, 12, 8);
        Button logout = new Button(this);
        logout.setText("SAIR");
        logout.setOnClickListener(v -> showLogin());
        TextView t = title("CANAIS  •  " + channels.size(), 20);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(logout);
        root.addView(bar);

        ListView list = new ListView(this);
        ArrayAdapter<Channel> adapter = new ArrayAdapter<Channel>(this, android.R.layout.simple_list_item_1, channels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(Color.WHITE);
                v.setTextSize(17);
                v.setPadding(24, 18, 24, 18);
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> play(channels.get(position)));
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void play(Channel ch) {
        releasePlayer();
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        PlayerView pv = new PlayerView(this);
        pv.setUseController(true);
        frame.addView(pv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Button back = new Button(this);
        back.setText("VOLTAR");
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        bp.setMargins(16, 16, 16, 16);
        frame.addView(back, bp);
        back.setOnClickListener(v -> { releasePlayer(); showChannels(); });
        setContentView(frame);

        player = new ExoPlayer.Builder(this).build();
        pv.setPlayer(player);
        String streamUrl = baseUrl + "/live/" + Uri.encode(username) + "/" + Uri.encode(password) + "/" + ch.id + "." + ch.ext;
        player.setMediaItem(MediaItem.fromUri(streamUrl));
        player.prepare();
        player.play();
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (player != null) {
            releasePlayer();
            showChannels();
        } else {
            super.onBackPressed();
        }
    }
}
