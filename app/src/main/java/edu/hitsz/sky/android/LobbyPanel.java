package edu.hitsz.sky.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import edu.hitsz.sky.core.Difficulty;
import edu.hitsz.sky.core.Mode;
import edu.hitsz.sky.core.StageCatalog;
import edu.hitsz.sky.net.Lobby;
import edu.hitsz.sky.net.RoomOptions;

final class LobbyPanel extends ScrollView {
    interface Actions {
        void hostLocally(RoomOptions options, String name);
        void connect(String address, int port, boolean tls, String name);
        void create(RoomOptions options);
        void join(String code);
        void start();
        void back();
    }

    private final GamePreferences preferences;
    private final SharedPreferences connectionPreferences;
    private final LinearLayout content;
    private final TextView heading, status, roomInfo;
    private final EditText address, port, name, code;
    private final Spinner mode, difficulty, stage;
    private final Switch tls;
    private final Button back, localHost, connect, launch, create, join;
    private boolean connected, joined;
    private int[] selectableStages = {1, 6};

    LobbyPanel(Context context, GamePreferences preferences, Actions actions) {
        super(context);
        this.preferences = preferences;
        connectionPreferences = context.getSharedPreferences("sky-connection", Context.MODE_PRIVATE);
        setFillViewport(true);
        setBackgroundColor(AircraftArt.CREAM);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(14), dp(24), dp(24));
        addView(content);
        back = button("‹ 返回基地", actions::back);
        heading = title("一起出发", 30);
        title("同一 Wi-Fi 可由一台手机开房；异地联机请连接公网服务器。", 14);
        name = field("你的名字", connectionPreferences.getString("name", "飞行员"));
        name.setSingleLine(true);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
        difficulty = spinner(new String[]{"轻松", "标准", "挑战"});
        difficulty.setSelection(preferences.difficulty().ordinal());
        mode = spinner(new String[]{"合作闯关", "比分对战"});
        stage = spinner(new String[]{"1 · 海邮港"});
        refreshStages();
        difficulty.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { refreshStages(); }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        title(preferences.developer() ? "开发者房间 · 无限生命与技能" : "选择已经解锁的航线", 13);
        localHost = button("在这台手机开房", () -> actions.hostLocally(options(), pilotName()));
        title("连接已有服务器", 19);
        address = field("服务器地址，例如 192.168.1.8", connectionPreferences.getString("host", ""));
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        port = field("端口", Integer.toString(connectionPreferences.getInt("port", 8888)));
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        tls = new Switch(context);
        tls.setText("TLS 加密连接");
        tls.setChecked(connectionPreferences.getBoolean("tls", false));
        content.addView(tls);
        connect = button("连接服务器", () -> connect(actions));
        status = title("尚未连接", 15);
        roomInfo = title("", 20);
        roomInfo.setPadding(dp(18), dp(18), dp(18), dp(18));
        roomInfo.setBackground(background(0xffffffff));
        create = button("创建房间", () -> actions.create(options()));
        code = field("朋友的房间码", "");
        code.setSingleLine(true);
        join = button("加入房间", () -> actions.join(code.getText().toString().trim()));
        launch = button("都到齐了，出发", actions::start);
        disconnected("尚未连接");
    }

    private void connect(Actions actions) {
        int number;
        try { number = Integer.parseInt(port.getText().toString()); }
        catch (NumberFormatException error) { setStatus("端口填 1 到 65535 的数字"); return; }
        String host = address.getText().toString().trim();
        if (host.isEmpty() || number < 1 || number > 65535) {
            setStatus("检查一下服务器地址和端口");
            return;
        }
        String pilot = pilotName();
        connectionPreferences.edit().putString("host", host).putInt("port", number)
                .putString("name", pilot).putBoolean("tls", tls.isChecked()).apply();
        actions.connect(host, number, tls.isChecked(), pilot);
    }

    private String pilotName() {
        String value = name.getText().toString().trim();
        return value.isEmpty() ? "飞行员" : value;
    }

    private void refreshStages() {
        int oldIndex = Math.max(0, stage.getSelectedItemPosition());
        int previous = selectableStages[Math.min(oldIndex, selectableStages.length - 1)];
        Difficulty selected = Difficulty.values()[difficulty.getSelectedItemPosition()];
        selectableStages = StageCatalog.selectable(preferences.unlocked(selected), preferences.developer());
        String[] values = new String[selectableStages.length];
        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            int number = selectableStages[i];
            values[i] = number + " · " + StageCatalog.get(number).title;
            if (number == previous) selectedIndex = i;
        }
        stage.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, values));
        stage.setSelection(selectedIndex);
    }

    private RoomOptions options() {
        return new RoomOptions(selectableStages[stage.getSelectedItemPosition()],
                Difficulty.values()[difficulty.getSelectedItemPosition()],
                mode.getSelectedItemPosition() == 0 ? Mode.COOP : Mode.VERSUS,
                preferences.developer(), System.nanoTime());
    }

    void connecting(String message) {
        connected = false;
        joined = false;
        showForm();
        localHost.setEnabled(false);
        connect.setEnabled(false);
        create.setEnabled(false);
        join.setEnabled(false);
        setStatus(message);
    }

    void disconnected(String reason) {
        connected = false;
        joined = false;
        roomInfo.setText("");
        showForm();
        setStatus(reason);
    }

    void update(Lobby lobby, String localAddress) {
        connected = true;
        joined = lobby.code != null && !lobby.code.isEmpty();
        if (!joined) {
            roomInfo.setText("");
            showForm();
            setStatus("已连接，可以创建或加入房间");
            return;
        }
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            child.setVisibility(child == back || child == heading || child == status || child == roomInfo || child == launch ? VISIBLE : GONE);
        }
        StringBuilder pilots = new StringBuilder();
        for (String player : lobby.players) {
            if (pilots.length() > 0) pilots.append("  +  ");
            pilots.append(player);
        }
        roomInfo.setText("房间 " + lobby.code + "\n\n" + pilots + "\n\n"
                + (lobby.options.mode == Mode.COOP ? "合作闯关" : "比分对战") + " · " + lobby.options.difficulty.label
                + "\n第 " + lobby.options.stage + " 关 · " + StageCatalog.get(lobby.options.stage).title
                + (lobby.options.developer ? "\n开发者模式" : ""));
        launch.setVisibility(lobby.host ? VISIBLE : GONE);
        launch.setEnabled(lobby.host && lobby.players.size() == 2);
        String hint = lobby.players.size() < 2 ? "把房间码告诉朋友，等 TA 过来" : lobby.host ? "人齐了，随时可以出发" : "等房主按下出发";
        setStatus(localAddress.isEmpty() ? hint : localAddress + "\n" + hint);
        post(() -> smoothScrollTo(0, 0));
    }

    private void showForm() {
        for (int i = 0; i < content.getChildCount(); i++) content.getChildAt(i).setVisibility(VISIBLE);
        roomInfo.setVisibility(GONE);
        launch.setVisibility(GONE);
        launch.setEnabled(false);
        localHost.setEnabled(true);
        connect.setEnabled(true);
        create.setEnabled(connected && !joined);
        join.setEnabled(connected && !joined);
        post(() -> smoothScrollTo(0, 0));
    }

    void setStatus(String message) { if (status != null) status.setText(message); }

    private TextView title(String text, int size) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(AircraftArt.INK);
        view.setTypeface(Typeface.create("sans-serif-rounded", Typeface.NORMAL));
        view.setPadding(0, dp(8), 0, dp(8));
        content.addView(view);
        return view;
    }

    private EditText field(String hint, String value) {
        EditText view = new EditText(getContext());
        view.setHint(hint);
        view.setText(value);
        view.setTextSize(16);
        view.setMinHeight(dp(52));
        content.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private Spinner spinner(String[] values) {
        Spinner view = new Spinner(getContext());
        view.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, values));
        content.addView(view, new LinearLayout.LayoutParams(-1, dp(52)));
        return view;
    }

    private Button button(String text, Runnable action) {
        Button view = new Button(getContext());
        view.setText(text);
        view.setTextSize(16);
        view.setAllCaps(false);
        view.setTextColor(AircraftArt.INK);
        view.setMinHeight(dp(52));
        view.setBackground(background(0xffffffff));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, dp(54));
        layout.setMargins(0, dp(5), 0, dp(5));
        content.addView(view, layout);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private GradientDrawable background(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), 0xffdde0d7);
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
