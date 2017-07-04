package cz.mendelu.xmarik.train_manager.activities;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.Toolbar;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cz.mendelu.xmarik.train_manager.events.TCPDisconnectEvent;
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
    private List<Train> managed;
    private List<Train> multitrack;
    private List<String> managed_str;
    private ArrayAdapter<String> managed_adapter;
    private Train train;
    private boolean updating;
    private Context context;

    private SeekBar sb_speed;
    private Switch s_direction;
    private CheckBox chb_total;
    private Spinner s_spinner;
    private Button b_stop;
    private Button b_idle;
    private CheckBox chb_group;
    private ImageButton ib_status;
    private ListView lv_functions;
    private TextView tv_kmhSpeed;

    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!updating && train != null && train.total && !train.stolen && train.stepsSpeed != sb_speed.getProgress()) {
                if (multitrack.contains(train)) {
                    for (Train s : multitrack)
                        if (!s.stolen)
                            s.setSpeedSteps(sb_speed.getProgress());
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

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        updating = false;
        context = this;

        managed = new ArrayList<>();
        multitrack = new ArrayList<>();

        sb_speed = (SeekBar) findViewById(R.id.speedkBar1);
        s_direction = (Switch) findViewById(R.id.handlerDirection1);
        b_idle = (Button) findViewById(R.id.startButton1);
        b_stop = (Button) findViewById(R.id.stopButton1);
        chb_group = (CheckBox) findViewById(R.id.goupManaged1);
        ib_status = (ImageButton) findViewById(R.id.ib_status);
        lv_functions = (ListView) findViewById(R.id.checkBoxView1);
        tv_kmhSpeed = (TextView) findViewById(R.id.kmh1);
        chb_total = (CheckBox) findViewById(R.id.totalManaged);

        // fill spinner
        s_spinner = (Spinner)findViewById(R.id.spinner1);
        managed_str = new ArrayList<String>();
        managed_adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, managed_str);
        s_spinner.setAdapter(managed_adapter);
        this.fillHVs();

        // GUI events:

        lv_functions.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                if (updating) return;
                CheckBox chb = (CheckBox)view.findViewById(R.id.checkBox1);
                chb.toggle();
                train.setFunc(position, chb.isChecked());
            }
        });

        s_direction.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onDirectionChange(!isChecked);
            }
        });

        s_spinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }

            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                if (position >= managed.size()) return;
                train = managed.get(position);
                updateGUTtoHV();
            }

        });

        chb_group.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (updating) return;

                if (b) {
                    if (!multitrack.contains(train))
                        multitrack.add(train);
                } else {
                    if (multitrack.contains(train))
                        multitrack.remove(train);
                }
            }
        });

        chb_total.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (updating) return;
                train.setTotal(b);
            }
        });
    }

    private void fillHVs() {
        managed_str.clear();
        managed.clear();
        int index = 0;

        s_spinner.setEnabled(TrainDb.instance.trains.size() > 0);
        if (TrainDb.instance.trains.size() == 0)
            managed_str.add(getString(R.string.ta_no_loks));

        int i = 0;
        for(Train t : TrainDb.instance.trains.values()) {
            managed.add(t);
            managed_str.add(t.name + " (" + t.label + ") : " + String.valueOf(t.addr));
            if (t == train) index = i;
            i++;
        }

        // update multitraction
        for (i = multitrack.size()-1; i >= 0; i--)
            if (!managed.contains(multitrack.get(i)))
                multitrack.remove(i);

        s_spinner.setSelection(index);
        if (TrainDb.instance.trains.size() > 0)
            train = managed.get(0);
        else
            train = null;

        managed_adapter.notifyDataSetChanged();
        this.updateGUTtoHV();
    }

    private void onDirectionChange(boolean newDir) {
        if (updating) return;

        if (!newDir)
            s_direction.setText(R.string.ta_direction_forward);
        else
            s_direction.setText(R.string.ta_direction_backwards);

        train.setDirection(newDir);

        if (multitrack.contains(train)) {
            for (Train s : multitrack) {
                if (s == train)
                    s.setDirection(newDir);
                else if (!s.stolen)
                    s.setDirection(!s.direction);
            }
        } else {
            train.setDirection(newDir);
        }
    }

    private void updateGUTtoHV() {
        this.updating = true;
        try {
            chb_group.setEnabled(train != null && managed.size() >= 2);
            chb_total.setEnabled(train != null && !train.stolen);
            lv_functions.setEnabled(train != null && !train.stolen);
            if (managed.size() < 2) chb_group.setChecked(false);

            if (train == null) {
                sb_speed.setProgress(0);
                s_direction.setChecked(false);
                s_direction.setText("-");

                chb_group.setChecked(false);
                tv_kmhSpeed.setText("- km/h");
                chb_total.setChecked(false);

                this.setEnabled(false);

                //set custom adapter with check boxes to list view
                FunctionCheckBoxAdapter dataAdapter = new FunctionCheckBoxAdapter(context,
                        R.layout.lok_function, new ArrayList<>(Arrays.asList(TrainFunction.DEF_FUNCTION)));
                lv_functions.setAdapter(dataAdapter);

                ib_status.setImageResource(R.drawable.ic_circle_gray);

            } else {
                sb_speed.setProgress(train.stepsSpeed);
                s_direction.setChecked(!train.direction);
                if (!train.direction)
                    s_direction.setText(R.string.ta_direction_forward);
                else
                    s_direction.setText(R.string.ta_direction_backwards);

                chb_group.setChecked(multitrack.contains(train));
                tv_kmhSpeed.setText(String.format("%s km/h", Integer.toString(train.kmphSpeed)));
                chb_total.setChecked(train.total);

                this.setEnabled(train.total);

                //set custom adapter with check boxes to list view
                FunctionCheckBoxAdapter dataAdapter = new FunctionCheckBoxAdapter(context,
                        R.layout.lok_function, new ArrayList<>(Arrays.asList(train.function)));
                lv_functions.setAdapter(dataAdapter);

                if (train.stolen)
                    ib_status.setImageResource(R.drawable.ic_circle_yellow);
                else
                    ib_status.setImageResource(R.drawable.ic_circle_green);
            }
        }
        finally {
            this.updating = false;
        }
    }

    public void onFuncChanged(int index, Boolean newState) {
        if (train != null && !train.stolen)
            train.setFunc(index, newState);
    }

    public void b_stopClick(View view) {
        sb_speed.setProgress(0);
        if (multitrack.contains(train)) {
            for (Train t : multitrack)
                t.emergencyStop();
        } else {
            train.emergencyStop();
        }
    }

    public void b_idleClick(View view) {
        sb_speed.setProgress(0);

        if (train == null) return;
        if (multitrack.contains(train)) {
            for (Train t : multitrack)
                if (!t.stolen)
                    t.setSpeedSteps(0);
        } else {
            train.setSpeedSteps(0);
        }
    }

    public void ib_StatusClick(View v) {
        if (train != null && train.stolen)
            train.please();
    }

    @Subscribe
    public void onEvent(LokChangeEvent event) {
        if (train != null && event.getAddr() == train.addr)
            this.updateGUTtoHV();
    }

    @Subscribe
    public void onEvent(LokAddEvent event) {
        this.fillHVs();
    }

    @Subscribe
    public void onEvent(LokRemoveEvent event) {
        this.fillHVs();
    }

    @Subscribe
    public void onEvent(TCPDisconnectEvent event) {
        updating = true;
        managed_str.clear();
        managed.clear();

        s_spinner.setEnabled(false);
        managed_str.add(getString(R.string.ta_no_loks));

        train = null;
        this.updateGUTtoHV();

        updating = false;
        super.onEvent(event);
    }

    @Subscribe
    public void onEvent(LokRespEvent event) {
        if (train == null) return;
        if (Integer.valueOf(event.getParsed().get(2)) != train.addr) return;

        tv_kmhSpeed.setText(String.format("%s km/h", Integer.toString(train.kmphSpeed)));

        if (event.getParsed().get(4).toUpperCase().equals("OK"))
            ib_status.setImageResource(R.drawable.ic_circle_green);
        else
            ib_status.setImageResource(R.drawable.ic_circle_red);
    }

    private void setEnabled(boolean enabled) {
        s_direction.setEnabled(enabled);
        sb_speed.setEnabled(enabled);
        b_stop.setEnabled(enabled);
        b_idle.setEnabled(enabled);
    }

    @Override
    protected void onPause() {
        b_idleClick((Button)findViewById(R.id.startButton1));
        timerHandler.removeCallbacks(timerRunnable);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            b_idleClick((Button)findViewById(R.id.startButton1));
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
