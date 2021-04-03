package cz.mendelu.xmarik.train_manager.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;

import cz.kudlav.scomview.ScomView;
import cz.mendelu.xmarik.train_manager.events.DccEvent;
import cz.mendelu.xmarik.train_manager.events.TCPDisconnectEvent;
import cz.mendelu.xmarik.train_manager.network.TCPClientApplication;
import cz.mendelu.xmarik.train_manager.storage.TrainDb;
import cz.mendelu.xmarik.train_manager.models.TrainFunction;
import cz.mendelu.xmarik.train_manager.adapters.FunctionCheckBoxAdapter;
import cz.mendelu.xmarik.train_manager.R;
import cz.mendelu.xmarik.train_manager.events.LokAddEvent;
import cz.mendelu.xmarik.train_manager.events.LokChangeEvent;
import cz.mendelu.xmarik.train_manager.events.LokRemoveEvent;
import cz.mendelu.xmarik.train_manager.events.LokRespEvent;
import cz.mendelu.xmarik.train_manager.models.Train;


public class TrainHandler extends NavigationBase {
    private Train train;
    private boolean updating;
    private Context context;
    private boolean confSpeedVolume;
    private boolean confAvailableFunctions;
    private Toolbar toolbar;


    private SeekBar sb_speed;
    private SwitchCompat s_direction;
    private CheckBox chb_total;
    private Button b_stop;
    private Button b_idle;
    private CheckBox chb_group;
    private ImageButton ib_status;
    private ImageButton ib_dcc;
    private ListView lv_functions;
    private TextView tv_kmhSpeed;
    private TextView tv_expSpeed;
    private TextView tv_expSignalBlock;
    private ScomView scom_expSignal;
    private ImageButton ib_release;

    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!updating && train != null && train.total && !train.stolen && train.stepsSpeed != sb_speed.getProgress()) {
                if (train.multitrack) {
                    for (Train t : TrainDb.instance.trains.values())
                        if (t.multitrack && !t.stolen)
                            t.setSpeedSteps(sb_speed.getProgress());
                } else
                    train.setSpeedSteps(sb_speed.getProgress());
            }
            timerHandler.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_train_handler);
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        updating = false;
        context = this;

        sb_speed = findViewById(R.id.speedkBar1);
        s_direction = findViewById(R.id.handlerDirection1);
        b_idle = findViewById(R.id.startButton1);
        b_stop = findViewById(R.id.stopButton1);
        chb_group = findViewById(R.id.goupManaged1);
        ib_status = findViewById(R.id.ib_status);
        ib_dcc = findViewById(R.id.ib_dcc);
        ib_release = findViewById(R.id.ib_release);
        lv_functions = findViewById(R.id.checkBoxView1);
        tv_kmhSpeed = findViewById(R.id.kmh1);
        tv_expSpeed = findViewById(R.id.expSpeed);
        tv_expSignalBlock = findViewById(R.id.expSignalBlock);
        scom_expSignal = findViewById(R.id.scom_view);
        chb_total = findViewById(R.id.totalManaged);

        // select train
        int train_addr = getIntent().getIntExtra("train_addr", -1);
        if (train_addr != -1) {
            train = TrainDb.instance.trains.get(train_addr);
        }

        this.fillHVs();

        // GUI events:
        s_direction.setOnCheckedChangeListener((buttonView, isChecked) ->
                onDirectionChange(!isChecked)
        );

        chb_group.setOnCheckedChangeListener((compoundButton, value) -> {
            if (updating) return;
            train.multitrack = value;
        });

        chb_total.setOnCheckedChangeListener((compoundButton, b) -> {
            if (!updating) train.setTotal(b);
        });

        ib_release.setOnClickListener(this::ib_ReleaseClick);
    }

    @Override
    public void onStart() {
        super.onStart();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        confSpeedVolume = preferences.getBoolean("SpeedVolume", false);
        confAvailableFunctions = preferences.getBoolean("OnlyAvailableFunctions", true);
        // show DCC stop when DCC status is known and different from GO
        Boolean dccState = TCPClientApplication.getInstance().dccState;
        updateDccState(dccState == null || dccState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // select train
        int train_addr = intent.getIntExtra("train_addr", -1);
        if (train_addr != -1) {
            train = TrainDb.instance.trains.get(train_addr);
            this.fillHVs();
        }
    }

    private void fillHVs() {
        if (TrainDb.instance.trains.isEmpty()) {
            this.finish();
            return;
        }

        if (train == null || !TrainDb.instance.trains.containsValue(train)) {
            int min_addr = Integer.MAX_VALUE;
            for (Train t: TrainDb.instance.trains.values())
                if (min_addr > t.addr) min_addr = t.addr;
            train = TrainDb.instance.trains.get(min_addr);
        }

        toolbar.setTitle(train.getTitle());

        this.updateGUTtoHV();
    }

    private void onDirectionChange(boolean newDir) {
        if (updating) return;

        if (!newDir)
            s_direction.setText(R.string.ta_direction_forward);
        else
            s_direction.setText(R.string.ta_direction_backwards);

        train.setDirection(newDir);

        if (train.multitrack) {
            for (Train t : TrainDb.instance.trains.values()) {
                if (t.multitrack) {
                    if (t == train)
                        t.setDirection(newDir);
                    else if (!t.stolen)
                        t.setDirection(!t.direction);
                }
            }
        } else {
            train.setDirection(newDir);
        }
    }

    private void updateGUTtoHV() {
        if (train == null) {
            this.finish();
            return;
        }

        this.updating = true;

        chb_group.setEnabled(TrainDb.instance.trains.size() >= 2);
        chb_total.setEnabled(!train.stolen);
        lv_functions.setEnabled(train != null && !train.stolen);
        if (TrainDb.instance.trains.size() < 2) chb_group.setChecked(false);

        sb_speed.setProgress(train.stepsSpeed);
        s_direction.setChecked(!train.direction);
        if (!train.direction)
            s_direction.setText(R.string.ta_direction_forward);
        else
            s_direction.setText(R.string.ta_direction_backwards);

        chb_group.setChecked(train.multitrack);
        tv_kmhSpeed.setText(String.format("%s km/h", train.kmphSpeed));
        chb_total.setChecked(train.total);

        if (train.expSpeed != -1)
            tv_expSpeed.setText(String.format("%s km/h", train.expSpeed));
        else tv_expSpeed.setText("- km/h");

        scom_expSignal.setCode(train.expSignalCode);
        tv_expSignalBlock.setText( (train.expSignalCode != -1) ? train.expSignalBlock : "" );

        this.setEnabled(train.total);

        //set custom adapter with check boxes to list view
        ArrayList<TrainFunction> functions;
        if (confAvailableFunctions) {
            // just own filter
            functions = new ArrayList<>();
            for (int i = 0; i < train.function.length; i++)
                if (!train.function[i].name.equals(""))
                    functions.add(train.function[i]);
        } else {
            functions = new ArrayList<>(Arrays.asList(train.function));
        }
        FunctionCheckBoxAdapter dataAdapter = new FunctionCheckBoxAdapter(context,
                R.layout.lok_function, functions, true);
        lv_functions.setAdapter(dataAdapter);

        if (train.stolen)
            ib_status.setImageResource(R.drawable.ic_circle_yellow);
        else
            ib_status.setImageResource(R.drawable.ic_circle_green);

        this.updating = false;
    }

    private void updateDccState(boolean enabled) {
        if (enabled) {
            ib_dcc.clearAnimation();
            ib_dcc.setAlpha(0.0f);
        }
        else {
            Animation blink = new AlphaAnimation(0.0f, 1.0f);
            blink.setDuration(250);
            blink.setRepeatMode(Animation.REVERSE);
            blink.setRepeatCount(Animation.INFINITE);
            ib_dcc.setAlpha(1.0f);
            ib_dcc.startAnimation(blink);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!confSpeedVolume) {
            return super.dispatchKeyEvent(event);
        }

        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN && sb_speed.getProgress() < sb_speed.getMax()) {
                    sb_speed.setProgress(sb_speed.getProgress()+1);
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN && sb_speed.getProgress() > 0) {
                    sb_speed.setProgress(sb_speed.getProgress()-1);
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    public void onFuncChanged(int index, Boolean newState) {
        if (train != null && !train.stolen)
            train.setFunc(index, newState);
    }

    public void b_stopClick(View view) {
        sb_speed.setProgress(0);
        if (train.multitrack) {
            for (Train t : TrainDb.instance.trains.values())
                if (t.multitrack) t.emergencyStop();
        } else {
            train.emergencyStop();
        }
    }

    public void b_idleClick(View view) {
        sb_speed.setProgress(0);

        if (train == null) return;
        if (train.multitrack) {
            for (Train t : TrainDb.instance.trains.values())
                if (t.multitrack && !t.stolen) t.setSpeedSteps(0);
        } else {
            if (train.total && !train.stolen) train.setSpeedSteps(0);
        }
    }

    public void ib_StatusClick(View v) {
        if (train != null && train.stolen)
            train.please();
    }

    public void ib_ReleaseClick(View v) {
        if (train == null) return;

        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.ta_release_really) + " " + train.name + "?")
                .setPositiveButton(getString(R.string.yes), (dialog, which) -> train.release())
                .setNegativeButton(getString(R.string.no), (dialog, which) -> {}).show();
    }

    public void setTrain(Train t) {
        train = t;
        this.fillHVs();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(LokChangeEvent event) {
        if (train != null && event.getAddr() == train.addr)
            this.updateGUTtoHV();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(LokAddEvent event) {
        super.onEventMainThread(event);
        this.fillHVs();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(LokRemoveEvent event) {
        super.onEventMainThread(event);
        this.fillHVs();

        Toast.makeText(getApplicationContext(),
                R.string.ta_release_ok, Toast.LENGTH_LONG)
                .show();

        if (TrainDb.instance.trains.size() == 0)
            startActivity(new Intent(this, TrainRequest.class));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(TCPDisconnectEvent event) {
        updating = true;

        train = null;
        this.updateGUTtoHV();

        updating = false;
        super.onEventMainThread(event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(LokRespEvent event) {
        if (train == null) return;
        if (Integer.parseInt(event.getParsed().get(2)) != train.addr) return;

        tv_kmhSpeed.setText(String.format("%s km/h", train.kmphSpeed));

        if (event.getParsed().get(4).toUpperCase().equals("OK"))
            ib_status.setImageResource(R.drawable.ic_circle_green);
        else
            ib_status.setImageResource(R.drawable.ic_circle_red);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(DccEvent event) {
        updateDccState(event.getParsed().get(2).toUpperCase().equals("GO"));
    }

    private void setEnabled(boolean enabled) {
        s_direction.setEnabled(enabled);
        sb_speed.setEnabled(enabled);
        b_stop.setEnabled(enabled);
        b_idle.setEnabled(enabled);
    }

    @Override
    protected void onPause() {
        b_idleClick(findViewById(R.id.startButton1));
        timerHandler.removeCallbacks(timerRunnable);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            b_idleClick(findViewById(R.id.startButton1));
            super.onBackPressed();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        this.fillHVs();
        timerHandler.postDelayed(timerRunnable, 100);
        if(!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        if(EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

}
