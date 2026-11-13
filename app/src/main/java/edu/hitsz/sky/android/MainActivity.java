package edu.hitsz.sky.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import edu.hitsz.sky.core.BattleEngine;
import edu.hitsz.sky.core.Difficulty;
import edu.hitsz.sky.core.GameState;
import edu.hitsz.sky.core.InputCommand;
import edu.hitsz.sky.core.Mode;
import edu.hitsz.sky.core.Phase;
import edu.hitsz.sky.core.StageCatalog;
import edu.hitsz.sky.net.Lobby;
import edu.hitsz.sky.net.NetClient;
import edu.hitsz.sky.net.RoomOptions;
import edu.hitsz.sky.net.RoomServer;

/** Composes screens and sessions. Combat rules live in shared/core. */
public final class MainActivity extends Activity implements MenuScreens.Actions {
    private FrameLayout root;
    private GamePreferences preferences;
    private MenuScreens menus;
    private SoundRack sound;
    private GameSurface surface;
    private LocalSession local;
    private volatile NetClient network;
    private volatile RoomServer hostedServer;
    private LobbyPanel lobbyPanel;
    private final ExecutorService connections=Executors.newSingleThreadExecutor();
    private AlertDialog pauseDialog, resultDialog;
    private volatile boolean destroyed;
    private boolean online, ended, background, bossMusic;
    private boolean inRoom, roomHost, roomPaused, waitingTransition, scoreHandled;
    private String pauseReason = "";
    private int stage=1, playerId, effectId=-1;
    private Difficulty difficulty=Difficulty.NORMAL;
    private Mode mode=Mode.SOLO;
    private GameState latest;
    private RoomOptions pendingCreate;
    private String hostedAddress="";
    private volatile long sessionVersion;
    private long sceneVersion;
    private final AtomicLong connectionSerial = new AtomicLong();
    private final Object connectionOwnership = new Object();
    private Runnable detachBackNavigation;
    private boolean homeScreen;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(0xfff8f3e8);
        getWindow().setNavigationBarColor(0xfff8f3e8);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=new FrameLayout(this);root.setBackgroundColor(0xfff8f3e8);
        if(android.os.Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view,insets)->{
                android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return android.view.WindowInsets.CONSUMED;
            });
        }else root.setFitsSystemWindows(true);
        setContentView(root);
        preferences=new GamePreferences(this);sound=new SoundRack(this,preferences);
        menus=new MenuScreens(this,preferences,this);
        if(android.os.Build.VERSION.SDK_INT>=33)detachBackNavigation=ModernBackNavigation.attach(this,this::navigateBack);
        showHome();
    }
    private void screen(View view) {
        sceneVersion++;
        homeScreen=false;
        if(surface!=null && view!=surface) { surface.dispose();surface=null; }
        root.removeAllViews();root.addView(view,new FrameLayout.LayoutParams(-1,-1));
    }
    private void leaveSession() {
        NetClient old;
        RoomServer oldServer;
        synchronized(connectionOwnership){
            sessionVersion++;
            old=network;network=null;
            oldServer=hostedServer;hostedServer=null;
        }
        RoomConnectionService.stop(this);
        if(pauseDialog!=null){pauseDialog.dismiss();pauseDialog=null;}
        dismissResult();
        if(local!=null){local.close();local=null;}
        if(old!=null)old.close();
        if(oldServer!=null)oldServer.close();
        online=false;ended=false;latest=null;pendingCreate=null;hostedAddress="";
        inRoom=false;roomHost=false;roomPaused=false;waitingTransition=false;scoreHandled=false;
    }
    @Override public void showHome(){leaveSession();screen(menus.home());homeScreen=true;sound.startMusic(false);}
    @Override public void showCampaign(){leaveSession();screen(menus.campaign());sound.startMusic(false);}
    @Override public void showSettings(){leaveSession();screen(menus.settings());}
    @Override public void showScores(){leaveSession();screen(menus.scores());}

    @Override public void startSolo(int number,Difficulty selected,boolean endless) {
        leaveSession(); stage=number;difficulty=selected;mode=endless?Mode.ENDLESS:Mode.SOLO;
        final long version=sessionVersion;
        if(!StageCatalog.available(number,preferences.unlocked(selected),preferences.developer())) {
            toast("先完成前面的航线吧");showCampaign();return;
        }
        playStory(number,()->{
            if(version!=sessionVersion)return;
            buildSurface(0);ended=false;
            local=new LocalSession(new BattleEngine(stage,difficulty,mode,preferences.developer(),System.nanoTime()),
                    frame->runOnUiThread(()->{if(version==sessionVersion)acceptFrame(frame,null);}));
            local.start();
        });
    }
    @Override public void replayStory(int number){playStory(number,this::showCampaign);}
    private void playStory(int number,Runnable finished) {
        sound.startMusic(false);bossMusic=false;
        long storyScene=sceneVersion+1;
        screen(new StoryView(this,number,()->{if(!destroyed&&sceneVersion==storyScene)finished.run();}));
    }
    private void buildSurface(int localId) {
        if(surface!=null)surface.dispose();
        surface=new GameSurface(this,new GameSurface.Callback(){
            public void onInput(InputCommand input){MainActivity.this.onInput(input);}
            public void onPause(){pauseGame();}
            public void onRadio(){MainActivity.this.onRadio();}
        });surface.setLocalPlayer(localId);
        surface.setLowEffects(!preferences.effects());
        surface.setConnectionLabel(online?"联机中":"");
        screen(surface);effectId=-1;
    }
    private void onInput(InputCommand input) {
        if(ended||waitingTransition)return;
        if(online && network!=null)network.send(input);
        else if(local!=null)local.send(input);
    }
    private void acceptFrame(GameState frame,GameState rival) {
        if(destroyed || surface==null)return;
        GameState previous=latest;latest=frame;
        surface.setFrame(frame,rival);
        boolean boss=false;
        for(GameState.Body enemy:frame.enemies)if(enemy.kind==4){boss=true;break;}
        if(boss!=bossMusic){bossMusic=boss;sound.startMusic(boss);}
        int played=0;
        for(GameState.Body effect:frame.effects) {
            if(effect.id<=effectId)continue;
            effectId=Math.max(effectId,effect.id);
            if(played++<2)sound.play(effectSound(effect.kind));
        }
        if(previous!=null && preferences.vibration()) {
            int i=Math.min(playerId,frame.pilots.size()-1);
            if(i>=0 && i<previous.pilots.size() && frame.pilots.get(i).hp<previous.pilots.get(i).hp) {
                Vibrator vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);
                if(vibrator!=null && vibrator.hasVibrator())vibrator.vibrate(VibrationEffect.createOneShot(25,VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
        if(frame.phase!=Phase.RUNNING && !ended && (mode!=Mode.VERSUS || rival==null || rival.phase!=Phase.RUNNING)) {
            ended=true;showResult(frame,rival);
        }
    }
    private void showResult(GameState frame,GameState rival) {
        if(pauseDialog!=null){pauseDialog.dismiss();pauseDialog=null;}
        dismissResult();
        boolean cleared=frame.phase==Phase.VICTORY;
        if(cleared)preferences.complete(stage,difficulty,frame.developer);
        sound.play(cleared?SoundRack.WIN:SoundRack.FAIL);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        int padding=dp(22);body.setPadding(padding,padding,padding,padding);
        TextView summary=new TextView(this);summary.setTextSize(18);
        summary.setText((stage==6?"机库夜班":"第"+stage+"关 · "+StageCatalog.get(stage).title)+"\n得分 "+frame.score+
                (rival==null?"":"\n朋友得分 "+rival.score)+(frame.developer?"\n开发者记录":""));body.addView(summary);
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("飞行员名字");
        name.setText(getSharedPreferences("sky-profile",MODE_PRIVATE).getString("name","飞行员"));body.addView(name);
        CheckBox save=new CheckBox(this);save.setText("记到排行榜");save.setChecked(true);body.addView(save);
        Runnable record=()->{
            if(scoreHandled)return;
            scoreHandled=true;
            String pilot=name.getText().toString().trim();if(pilot.isEmpty())pilot="飞行员";
            getSharedPreferences("sky-profile",MODE_PRIVATE).edit().putString("name",pilot).apply();
            if(save.isChecked())preferences.record(pilot,frame.score,stage,difficulty,frame.developer,mode);
        };
        String title=cleared?(stage==5?"航线恢复了！":stage==6?"早安，机库！":"这一程，完成！"):
                (stage==6?"机器人来串门了":"再试一次？");
        if(mode==Mode.VERSUS && rival!=null)title=frame.score==rival.score?"这回打平了":frame.score>rival.score?"这回你领先":"下回再比！";
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(title).setView(body).setCancelable(false)
                .setNegativeButton("返回基地",(d,w)->{record.run();showHome();});
        boolean nextStage=cleared&&stage<5&&(mode!=Mode.VERSUS||rival!=null&&rival.phase==Phase.VICTORY);
        if(online)dialog.setPositiveButton(nextStage?"下一关":"再来一局",(d,w)->{
            record.run();
            if(network==null){showOnline();return;}
            waitingTransition=true;
            showTransitionWait("等朋友准备好，一起出发");
            network.ready();
        });
        else {
            dialog.setPositiveButton(cleared && stage<5 && mode!=Mode.ENDLESS?"下一关":"再飞一次",(d,w)->{
                record.run();startSolo(cleared&&stage<5&&mode!=Mode.ENDLESS?stage+1:stage,difficulty,mode==Mode.ENDLESS);
            });
            if(cleared && stage==5)dialog.setNeutralButton("看看结局",(d,w)->{
                record.run();showEnding();
            });
        }
        resultDialog=dialog.create();resultDialog.show();
    }
    private void dismissResult(){if(resultDialog!=null){resultDialog.dismiss();resultDialog=null;}}
    private void showTransitionWait(String message){
        dismissResult();
        resultDialog=new AlertDialog.Builder(this).setTitle("准备出发").setMessage(message)
                .setCancelable(false).setNegativeButton("返回基地",(d,w)->showHome()).create();
        resultDialog.show();
    }
    private void showEnding() {
        new AlertDialog.Builder(this).setTitle("下一趟，明天见")
                .setMessage("小夏：五枚航标，一个没少。\n豆丁：航线恢复，今天可以准点收工了。\n小夏：那机库谁看着？\n豆丁：……我已经替你报名了。\n\n航线页里，还能去机库值个夜班。")
                .setCancelable(false).setPositiveButton("回基地",(d,w)->showHome()).show();
    }
    private static int effectSound(int kind) {
        if(kind==2)return SoundRack.PICKUP;
        if(kind>=3)return SoundRack.SKILL;
        return kind==0?SoundRack.EXPLOSION:SoundRack.HIT;
    }
    private void pauseGame(){
        if(surface==null || ended||waitingTransition)return;
        if(online && network!=null)network.pause(true);
        if(local!=null)local.pause();
        openPauseDialog();
    }
    private void openPauseDialog() {
        if(background || destroyed || ended || waitingTransition || pauseDialog!=null)return;
        boolean canRetry=!online||(roomHost&&latest!=null);
        String[] choices=canRetry?new String[]{"继续飞行","重打这一关","音乐电台","声音设置","返回基地"}:
                new String[]{"继续飞行","音乐电台","声音设置","返回基地"};
        pauseDialog=new AlertDialog.Builder(this).setTitle(roomPaused&&!pauseReason.isEmpty()?pauseReason:"歇一会儿")
                .setItems(choices,(dialog,index)->{
                    pauseDialog=null;
                    if(!canRetry&&index>0)index++;
                    if(index==0)resumeGame();
                    else if(index==1){
                        if(online&&network!=null){waitingTransition=true;showTransitionWait("正在重打这一关……");network.retry();}
                        else startSolo(stage,difficulty,mode==Mode.ENDLESS);
                    }
                    else if(index==2)showRadio(true);
                    else if(index==3)showSoundSettings();
                    else showHome();
                }).setCancelable(false).create();pauseDialog.show();
    }
    private void resumeGame() {
        if(online && network!=null){network.suspend(false);network.pause(false);}
        if(local!=null)local.resume();
        sound.resumeMusic();
    }
    private void onRadio(){showRadio(false);}
    @Override public void showRadio(){showRadio(false);}
    private void showRadio(boolean returnToPause) {
        TextView track=new TextView(this);track.setText(sound.trackName());track.setTextSize(20);track.setPadding(dp(22),dp(16),dp(22),dp(16));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("音乐电台").setView(track)
                .setPositiveButton("下一首",null).setNegativeButton("上一首",null)
                .setNeutralButton("收起",(d,w)->{if(returnToPause)openPauseDialog();}).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{sound.nextTrack();track.setText(sound.trackName());});
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{sound.previousTrack();track.setText(sound.trackName());});
        });dialog.show();
    }
    private void showSoundSettings() {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(8),dp(20),dp(12));
        CheckBox mute=new CheckBox(this);mute.setText("静音");mute.setChecked(preferences.muted());box.addView(mute);
        mute.setOnCheckedChangeListener((button,checked)->{preferences.setMuted(checked);sound.refresh();});
        volumeSlider(box,"音乐",preferences.bgmVolume(),value->{preferences.setBgmVolume(value);sound.refresh();});
        volumeSlider(box,"音效",preferences.sfxVolume(),value->{preferences.setSfxVolume(value);sound.refresh();});
        new AlertDialog.Builder(this).setTitle("声音设置").setView(box).setCancelable(false)
                .setPositiveButton("好了",(d,w)->openPauseDialog()).show();
    }
    private interface VolumeChange {void change(float value);}
    private void volumeSlider(LinearLayout box,String label,float value,VolumeChange change) {
        TextView text=new TextView(this);text.setText(label);box.addView(text);
        SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(Math.round(value*100));box.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){if(user)change.change(progress/100f);}
            public void onStartTrackingTouch(SeekBar bar){} public void onStopTrackingTouch(SeekBar bar){}
        });
    }

    @Override public void showOnline() {
        leaveSession();sound.startMusic(false);
        final long version=sessionVersion;
        lobbyPanel=new LobbyPanel(this,preferences,new LobbyPanel.Actions(){
            public void hostLocally(RoomOptions options,String name){
                if(!keepRoomConnection())return;
                long attempt=connectionSerial.incrementAndGet();
                pendingCreate=options;lobbyPanel.connecting("正在开房……");
                connections.execute(()->{
                    try {
                        RoomServer server=hostedServer;
                        if(server==null){
                            server=new RoomServer(8888);server.start();
                            boolean owned;
                            synchronized(connectionOwnership){
                                owned=currentConnection(version,attempt);
                                if(owned)hostedServer=server;
                            }
                            if(!owned){server.close();return;}
                        }
                        hostedAddress=localAddresses()+" : "+server.port();
                        connectTo("127.0.0.1",server.port(),false,name,version,attempt);
                    }catch(Exception error){runOnUiThread(()->{
                        if(!currentConnection(version,attempt))return;
                        connectionSerial.incrementAndGet();
                        stopRoomConnection();
                        NetClient abandoned=network;network=null;
                        if(abandoned!=null)abandoned.close();
                        lobbyPanel.disconnected("开房失败："+readable(error));
                    });}
                });
            }
            public void connect(String host,int port,boolean tls,String name){
                if(!keepRoomConnection())return;
                long attempt=connectionSerial.incrementAndGet();
                pendingCreate=null;lobbyPanel.connecting("正在连接……");connections.execute(()->connectTo(host,port,tls,name,version,attempt));
            }
            public void create(RoomOptions options){if(network!=null)network.create(options);}
            public void join(String code){if(network!=null)network.join(code);}
            public void start(){if(network!=null)network.start();}
            public void back(){showHome();}
        });screen(lobbyPanel);
    }
    private boolean keepRoomConnection(){
        try{RoomConnectionService.start(this);return true;}
        catch(RuntimeException error){
            android.util.Log.w("Sky-room","Unable to keep the room connection active",error);
            toast("暂时不能保留房间，请回到游戏后再试");return false;
        }
    }
    private void stopRoomConnection(){
        RoomConnectionService.stop(this);
        RoomServer abandoned;
        synchronized(connectionOwnership){
            abandoned=hostedServer;hostedServer=null;
        }
        if(abandoned!=null)abandoned.close();
    }
    private boolean currentConnection(long version,long attempt){return !destroyed&&version==sessionVersion&&attempt==connectionSerial.get();}
    private void connectTo(String host,int port,boolean tls,String name,long version,long attempt) {
        if(!currentConnection(version,attempt))return;
        NetClient client=new NetClient(new NetClient.Listener(){
            public void onLobby(Lobby lobby){runOnUiThread(()->{
                if(!currentConnection(version,attempt))return;
                playerId=lobby.yourId;
                inRoom=lobby.code!=null&&!lobby.code.isEmpty();roomHost=lobby.host;
                if(lobby.options!=null){stage=lobby.options.stage;difficulty=lobby.options.difficulty;mode=lobby.options.mode;}
                if(lobbyPanel!=null)lobbyPanel.update(lobby,hostedAddress);
                if(pendingCreate!=null && (lobby.code==null||lobby.code.isEmpty())) {
                    RoomOptions create=pendingCreate;pendingCreate=null;if(network!=null)network.create(create);
                }
            });}
            public void onStory(RoomOptions options){runOnUiThread(()->{
                if(!currentConnection(version,attempt))return;
                if(pauseDialog!=null){pauseDialog.dismiss();pauseDialog=null;}
                dismissResult();waitingTransition=false;scoreHandled=false;roomPaused=false;latest=null;
                stage=options.stage;difficulty=options.difficulty;mode=options.mode;online=true;ended=false;
                if(background&&network!=null)network.suspend(true);
                playStory(stage,()->{
                    if(!currentConnection(version,attempt))return;
                    buildSurface(mode==Mode.VERSUS?0:playerId);
                    surface.setConnectionLabel("等朋友准备好……");
                    if(network!=null)network.ready();
                });
            });}
            public void onFrame(GameState state,GameState rival){runOnUiThread(()->{
                if(!currentConnection(version,attempt)||waitingTransition)return;
                if(surface!=null)surface.setConnectionLabel(roomPaused?pauseReason:"联机中");acceptFrame(state,rival);
            });}
            public void onError(String message){runOnUiThread(()->{
                if(!currentConnection(version,attempt))return;
                if(waitingTransition&&resultDialog!=null){resultDialog.setTitle("这次没能出发");resultDialog.setMessage(message);}
                networkError(message,version);
            });}
            public void onDisconnected(String message){runOnUiThread(()->{
                if(!currentConnection(version,attempt))return;
                connectionSerial.incrementAndGet();
                stopRoomConnection();
                network=null;inRoom=false;roomHost=false;roomPaused=false;
                if(lobbyPanel!=null)lobbyPanel.disconnected(message);
                if(surface!=null)surface.setConnectionLabel("连接已断开");
                if(online && (!ended||waitingTransition)){
                    ended=true;waitingTransition=false;dismissResult();
                    if(pauseDialog!=null){pauseDialog.dismiss();pauseDialog=null;}
                    resultDialog=new AlertDialog.Builder(MainActivity.this).setTitle("连接断开了")
                        .setMessage(message).setCancelable(false).setPositiveButton("回大厅",(d,w)->showOnline()).create();
                    resultDialog.show();
                }
            });}
            public void onPaused(boolean paused,String reason){runOnUiThread(()->{
                if(!currentConnection(version,attempt))return;
                roomPaused=paused;pauseReason=reason;
                if(surface==null||ended||waitingTransition)return;
                surface.setConnectionLabel(paused?reason:"联机中");
                if(paused&&pauseDialog!=null)pauseDialog.setTitle(reason);
                if(paused)openPauseDialog();else if(pauseDialog!=null){pauseDialog.dismiss();pauseDialog=null;}
            });}
        });
        NetClient old;
        synchronized(connectionOwnership){
            if(!currentConnection(version,attempt)){client.close();return;}
            old=network;network=client;
        }
        if(old!=null)old.close();
        try{client.connect(host,port,tls,name);if(!currentConnection(version,attempt))client.close();}
        catch(Exception error){runOnUiThread(()->{
            if(!currentConnection(version,attempt))return;
            connectionSerial.incrementAndGet();
            stopRoomConnection();
            network=null;inRoom=false;roomHost=false;
            String message="连接失败："+readable(error);
            if(lobbyPanel!=null)lobbyPanel.disconnected(message);
            toast(message);
        });}
    }
    private void networkError(String message,long version){runOnUiThread(()->{
        if(destroyed||version!=sessionVersion)return;
        if(lobbyPanel!=null)lobbyPanel.setStatus(message);toast(message);
    });}
    private static String readable(Exception error){String text=error.getMessage();return text==null?error.getClass().getSimpleName():text;}
    private static String localAddresses(){
        StringBuilder result=new StringBuilder();
        try{for(NetworkInterface network:Collections.list(NetworkInterface.getNetworkInterfaces()))
            for(java.net.InetAddress address:Collections.list(network.getInetAddresses()))
                if(address instanceof Inet4Address && !address.isLoopbackAddress()){
                    if(result.length()>0)result.append(" / ");result.append(address.getHostAddress());
                }}catch(Exception ignored){return "在手机网络设置中查看本机地址";}
        return result.length()==0?"本机":result.toString();
    }
    @Override protected void onPause(){
        background=true;
        if(local!=null)local.pause();
        if(inRoom&&network!=null)network.suspend(true);
        if(sound!=null)sound.pauseMusic();super.onPause();
    }
    @Override protected void onResume(){super.onResume();background=false;
        if(sound!=null)sound.resumeMusic();
        if(surface!=null&&!ended)openPauseDialog();
        else if(inRoom&&network!=null)network.suspend(false);
    }
    // Android 26–32 use this entry; Android 33+ use ModernBackNavigation.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){navigateBack();}
    private void navigateBack(){
        if(surface!=null&&!ended)pauseGame();else if(homeScreen)finish();else showHome();
    }
    @Override protected void onDestroy(){
        destroyed=true;leaveSession();if(surface!=null)surface.dispose();
        if(detachBackNavigation!=null)detachBackNavigation.run();
        if(sound!=null)sound.close();connections.shutdownNow();super.onDestroy();
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
