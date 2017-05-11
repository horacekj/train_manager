package cz.mendelu.xmarik.train_manager.activities;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.Toolbar;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;

import cz.mendelu.xmarik.train_manager.HelpServices;
import cz.mendelu.xmarik.train_manager.R;
import cz.mendelu.xmarik.train_manager.events.ServerReloadEvent;
import cz.mendelu.xmarik.train_manager.events.TCPErrorEvent;
import cz.mendelu.xmarik.train_manager.models.Server;
import cz.mendelu.xmarik.train_manager.ServerList;
import cz.mendelu.xmarik.train_manager.UdpDiscover;
import cz.mendelu.xmarik.train_manager.events.CriticalErrorEvent;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

public class ServerSelect extends NavigationBase {

    private final int port = 5880;
    ServerSocket serverSocket;
    Button lButton;
    ArrayList<String> array;
    ArrayList<String> array1;
    ArrayAdapter<String> fAdapter;
    ArrayAdapter<String> lAdapter;
    Context context;
    Object obj;
    SharedPreferences sharedpreferences;
    private ListView lServers;
    private ListView fServers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //tady nacist ulozeny data
        setContentView(R.layout.activity_server_select);
        super.onCreate(savedInstanceState);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        get();
        obj = this;
        context = this.getApplicationContext();
        lButton = (Button) findViewById(R.id.serverButton);
        fServers = (ListView) findViewById(R.id.farServers);
        lServers = (ListView) findViewById(R.id.localServers);
        EventBus.getDefault().register(this);
        sharedpreferences = getDefaultSharedPreferences(getApplicationContext());
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (mWifi.isConnected()) {
            UdpDiscover udp = new UdpDiscover(context, port, this);
            udp.execute();
            this.lButton.setClickable(false);
        } else Toast.makeText(getApplicationContext(),
                R.string.conn_wifi_unavailable, Toast.LENGTH_LONG)
                .show();

        array = ServerList.getInstance().getServersString();
        lAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, array);
        array1 = ServerList.getInstance().getStoredServersString();
        fAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, array1);
        lServers.setAdapter(lAdapter);
        fServers.setAdapter(fAdapter);
        registerForContextMenu(fServers);
        // ListView Item Click Listener
        fServers.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                // ListView Clicked item value
                String itemValue = (String) fServers.getItemAtPosition(position);
                AuthorizeServer(itemValue);
            }

        });

        lButton.setOnClickListener(
                new View.OnClickListener() {

                    public void onClick(View view) {
                        ServerList.getInstance().clearLocalServers();
                        array = ServerList.getInstance().getServersString();

                        lAdapter = new ArrayAdapter<>(context,
                                android.R.layout.simple_list_item_1, android.R.id.text1, array);
                        lServers.setAdapter(lAdapter);
                        lAdapter.notifyDataSetChanged();

                        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                        if (mWifi.isConnected()) {
                            new UdpDiscover(context, port, (ServerSelect) obj).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            System.out.print("button activated");
                            float deg = lButton.getRotation() + 720F;
                            lButton.animate().rotation(deg).setInterpolator(new AccelerateDecelerateInterpolator());
                        } else Toast.makeText(getApplicationContext(),
                                R.string.conn_wifi_unavailable, Toast.LENGTH_LONG)
                                .show();
                        float deg = lButton.getRotation() + 360F;
                        lButton.animate().rotation(deg).setInterpolator(new AccelerateDecelerateInterpolator());
                    }
                });
        // ListView Item Click Listener
        lServers.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                // ListView Clicked item value
                String itemValue = (String) lServers.getItemAtPosition(position);

                AuthorizeServer(itemValue);
            }


        });
    }

    /**
     * metoda vyvolaná pokud je nalezen server na lokální síti
     * slouží k znovu načtení dat
     */
    public void dataReload() {
        lButton.setClickable(true);
        ServerList serverList = ServerList.getInstance();
        serverList.clear();
        array = serverList.getServersString();
        for (Server s : serverList.getServers()) {
            for (Server c : serverList.getCustomServers())
                if (c.equals(s)) {
                    s.username = c.username;
                    s.password = c.password;
                }
        }

        lAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, array);
        lServers.setAdapter(lAdapter);
        lAdapter.notifyDataSetChanged();
        Toast.makeText(getApplicationContext(),
                getString(R.string.conn_search_finished), Toast.LENGTH_LONG)
                .show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId() == R.id.farServers) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            menu.setHeaderTitle(array1.get(info.position));

            String[] menuItems = {
                    getString(R.string.mm_connect),
                    getString(R.string.mm_change_login),
                    getString(R.string.mm_change_settings),
                    getString(R.string.mm_info),
                    getString(R.string.mm_delete),
                    getString(R.string.mm_delete_all)
            };

            for (int i = 0; i < menuItems.length; i++) {
                menu.add(Menu.NONE, i, i, menuItems[i]);
            }
        }
    }

    /**
     * kontextové menu zobrazené pří dlouhém stisku na položku uložených serverů
     * pořadí vybraného serveru je zjišťováno díky indexu prvku, který událost vyvolal
     * @param item
     * @return
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int menuItemIndex = item.getItemId();
        String listItemName = array1.get(info.position);
        String[] tmp = listItemName.split("\t");
        //TODO dialogy k mazani
        switch (menuItemIndex) {
            case 0:
                Server tmpServer = ServerList.getInstance().getServer(tmp[0]);
                if (tmpServer.active) {
                    AuthorizeServer(tmp[0]);
                } else Toast.makeText(getApplicationContext(),
                        R.string.conn_server_offline, Toast.LENGTH_LONG)
                        .show();
                break;
            case 1:
                showDialog(tmp[0]);
                break;
            case 2:
                Intent intent = new Intent(getBaseContext(), NewServer.class);
                intent.putExtra("server", listItemName);
                startActivityForResult(intent, 2);
                break;
            case 3:
                // TODO: remove item from menu
                break;
            case 4:
                array1.remove(info.position);
                ServerList.getInstance().removeServer(info.position);
                fAdapter.notifyDataSetChanged();
                break;
            case 5:
                ServerList.getInstance().clearCustomServer();
                deleteAllServers();
                break;
        }
        return true;
    }

    public void addServer(View view) {
        Intent intent = new Intent(this, NewServer.class);
        startActivityForResult(intent, 1);
    }

    public void AuthorizeServer(String itemValue) {
        Intent intent = new Intent(getBaseContext(), ServerConnector.class);
        intent.putExtra("server", itemValue);
        startActivityForResult(intent, 2);
    }

    /**
     * metoda je vyvolána pokud je ukončena aktivita, která byla vyvolána pro výsledek
     * metoda slouží k obsloužení výsledku
     * @param requestCode číslo pro identifikaci volní (určí o kterou aktivitu se jedná)
     * @param resultCode hodnota vyjadřující zda aktivita zkončila úspěchem či nikoli
     * @param data případné návratové hodnoty
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            array1 = ServerList.getInstance().getStoredServersString();
            fAdapter.notifyDataSetChanged();
            //asi predelat s tim novym adapterem do ifu, pak to vali
            fAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_1, android.R.id.text1, array1);
            fAdapter.notifyDataSetChanged();
            fServers.setAdapter(fAdapter);
            fAdapter.notifyDataSetChanged();
            String txt = ServerList.getInstance().getServerStoreString();
            SharedPreferences.Editor editor = sharedpreferences.edit();
            editor.putString("storedServers", txt);
            //editor.commit();
            editor.apply();
            if (resultCode == RESULT_OK) {
                array1 = ServerList.getInstance().getStoredServersString();
                fAdapter.notifyDataSetChanged();
            }else if (resultCode == RESULT_CANCELED) {
                //Do nothing?
            }
        } else if (requestCode == 2) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(getApplicationContext(),
                        R.string.conn_connected, Toast.LENGTH_LONG)
                        .show();
            } else {
                Toast.makeText(getApplicationContext(),
                        R.string.conn_no_server_authorized, Toast.LENGTH_LONG)
                        .show();
            }
        }
    }//onActivityResult

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void showDialog(final String serverName) {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_user);
        dialog.setTitle("Set user data");
        final Server server = ServerList.getInstance().getServer(serverName);
        //set dialog component
        final EditText mName = (EditText) dialog.findViewById(R.id.dialogName);
        final EditText mPasswd = (EditText) dialog.findViewById(R.id.dialogPasswd);
        Button dialogButton = (Button) dialog.findViewById(R.id.dialogButtonOK);
        // if button is clicked, close the custom dialog
        if (server.password != null) mPasswd.setText(server.password);
        if (server.username != null) mName.setText(server.username);

        dialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                server.password = HelpServices.hashPasswd(mPasswd.getText().toString());
                server.username = mName.getText().toString();
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    @Subscribe
    public void onEvent(ServerReloadEvent event) {
        dataReload();
    }

    @Override
    public void onPause() {
        super.onPause();
        Save();
        if(EventBus.getDefault().isRegistered(this))EventBus.getDefault().unregister(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        Save();
        if(EventBus.getDefault().isRegistered(this))EventBus.getDefault().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        get();
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    public void Save() {
        String n = ServerList.getInstance().getSaveString();

        SharedPreferences.Editor editor = sharedpreferences.edit();
        editor.remove("StoredServers");
        editor.clear();
        editor.putString("StoredServers", n);
        editor.commit();
    }

    public void get() {
        sharedpreferences = getDefaultSharedPreferences(getApplicationContext());//getSharedPreferences(myServerPreferences, Context.MODE_PRIVATE);
        if (sharedpreferences.contains("StoredServers")) {
            ServerList.getInstance().loadServers(sharedpreferences.getString("StoredServers", ""));
        }
    }

    public void deleteAllServers() {
        sharedpreferences = getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedpreferences.edit();
        editor.clear();
        editor.remove("StoredServers");
        editor.commit();
        Toast.makeText(getApplicationContext(),
                R.string.gl_all_deleted,
                Toast.LENGTH_LONG).show();
    }

    @Subscribe
    public void tcpErrorEvent(TCPErrorEvent event) {
        ServerList.getInstance().deactivateServer();
        Toast.makeText(getApplicationContext(),
                event.getError(),
                Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, ServerSelect.class);
        startActivity(intent);
    }

}