package cz.mendelu.xmarik.train_manager.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;

import cz.mendelu.xmarik.train_manager.R;
import cz.mendelu.xmarik.train_manager.events.TCPDisconnectEvent;
import cz.mendelu.xmarik.train_manager.storage.TrainDb;
import cz.mendelu.xmarik.train_manager.events.LokAddEvent;
import cz.mendelu.xmarik.train_manager.events.LokChangeEvent;
import cz.mendelu.xmarik.train_manager.events.LokRemoveEvent;
import cz.mendelu.xmarik.train_manager.models.Train;

public class TrainRelease extends NavigationBase {
    Context context;
    ArrayList<String> train_strings;
    ArrayList<Train> trains;
    ArrayAdapter<String> hvs_adapter;
    Integer focused;

    Button b_send;
    ListView lv_trains;
    View lastSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_train_release);
        super.onCreate(savedInstanceState);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        context = this;
        lv_trains = (ListView) findViewById(R.id.acquiredTrains);
        b_send = (Button) findViewById(R.id.trainBoxButton);
        focused = -1;

        trains = new ArrayList<Train>();
        train_strings = new ArrayList<String>();
        hvs_adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, train_strings);
        lv_trains.setAdapter(hvs_adapter);

        lv_trains.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                // ListView Clicked item index
                view.setBackgroundColor(Color.rgb(153,204,255));
                if (lastSelected != null && !lastSelected.equals(view)) {
                    lastSelected.setBackgroundColor(Color.rgb(238,238,238));
                }
                lastSelected = view;
                focused = position;
            }

        });

        if(!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(LokRemoveEvent event) {
        Toast.makeText(getApplicationContext(),
                R.string.trl_loko_released, Toast.LENGTH_LONG)
                .show();

        updateHVList();
        b_send.setEnabled(true);
        b_send.setText(R.string.trl_release);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(LokChangeEvent event) {
        updateHVList();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(TCPDisconnectEvent event) {
        focused = -1;
        if (lastSelected != null) {
            lastSelected.setBackgroundColor(Color.rgb(238, 238, 238));
            lastSelected = null;
        }

        trains.clear();
        train_strings.clear();
        train_strings.add(getString(R.string.ta_no_loks));
        hvs_adapter.notifyDataSetChanged();

        lv_trains.setEnabled(false);
        b_send.setEnabled(true);
        b_send.setText(R.string.trl_release);

        super.onEventMainThread(event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThreadMainThread(LokAddEvent event) {
        updateHVList();
    }

    public void b_releaseClick(View v) {
        if (focused == -1) {
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.trl_no_train_selected))
                    .setCancelable(false)
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {}
                    }).show();
            return;
        }

        lv_trains.setEnabled(false);
        b_send.setEnabled(false);
        b_send.setText(R.string.trl_releasing);
        trains.get(focused).release();
    }

    private void updateHVList() {
        focused = -1;
        if (lastSelected != null) {
            lastSelected.setBackgroundColor(Color.rgb(238, 238, 238));
            lastSelected = null;
        }

        trains.clear();
        train_strings.clear();

        lv_trains.setEnabled(TrainDb.instance.trains.size() > 0);
        if (TrainDb.instance.trains.size() == 0)
            train_strings.add(getString(R.string.ta_no_loks));

        for(Train t : TrainDb.instance.trains.values()) {
            train_strings.add(t.name + " (" + t.label + ") : " + String.valueOf(t.addr));
            trains.add(t);
        }

        hvs_adapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
                if(EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
            super.onBackPressed();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateHVList();
        b_send.setEnabled(true);
        b_send.setText(R.string.trl_release);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
    }
}
