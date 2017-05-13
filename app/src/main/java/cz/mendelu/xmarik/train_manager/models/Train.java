package cz.mendelu.xmarik.train_manager.models;

import java.util.ArrayList;

import cz.mendelu.xmarik.train_manager.network.TCPClientApplication;
import cz.mendelu.xmarik.train_manager.helpers.ParseHelper;

/**
 * Class Train represents a train.
 */
public class Train {
    // data:
    public String name;
    public String owner;
    public String label;
    public String note;
    public int addr;
    public String kind;
    public TrainFunction function[];

    // state:
    public int stepsSpeed = 0;
    public int kmphSpeed = 0;
    public boolean direction = false;
    public boolean total = false;
    public boolean stolen = false;

    /** Constructs train from server string.
     * @param data in format: název|majitel|označení|poznámka|DCC adresa|třída|číslo soupravy|
            orientace stanovište A|funkce|rychlost ve stupních|rychlost km/h|směr|
            id oblasti řízení|cv_take|cv_release|func_vyznamy
     */
    public Train(String data) {
        updateFromServerString(data);
    }

    public void updateFromServerString(String data) {
        ArrayList<String> parsed = ParseHelper.parse(data, "|", "");
        ArrayList<String> functionStrs = ParseHelper.parse(parsed.get(15), ";", "");

        name = parsed.get(0);
        owner = parsed.get(1);
        label = parsed.get(2);
        note = parsed.get(3);
        addr = Integer.parseInt(parsed.get(4));
        kind = parsed.get(5);

        stepsSpeed = Integer.parseInt(parsed.get(9));
        kmphSpeed = Integer.parseInt(parsed.get(10));
        direction = parsed.get(11).equals("1");

        function = new TrainFunction[parsed.get(8).length()];
        for (int i = 0; i < function.length; i++) {
            String desc = "";
            if (i < functionStrs.size()) desc = functionStrs.get(i);
            this.function[i] = new TrainFunction(i, desc, parsed.get(8).charAt(i) == '1');
        }
    }

    public void setDirection(boolean direction) {
        if (this.direction == direction) return;
        this.direction = direction;
        String strDir = direction ? "1" : "0";
        TCPClientApplication.getInstance().send("-;LOK;" + String.valueOf(this.addr) + ";D;" + strDir);
    }

    public void setSpeedSteps(int steps) {
        if (this.stepsSpeed == steps) return;
        this.stepsSpeed = steps;
        TCPClientApplication.getInstance().send("-;LOK;" + String.valueOf(this.addr) + ";SP-S;" + steps);
    }

    public void setFunc(int id, boolean state) {
        if (function[id].checked == state) return;
        function[id].checked = state;
        String strState = state ? "1" : "0";
        TCPClientApplication.getInstance().send("-;LOK;" + String.valueOf(this.addr) + ";F;" +
                String.valueOf(id) + ";" + strState);
    }

    public void setTotal(boolean total) {
        if (this.total == total) return;
        this.total = total;
        String strTotal = total ? "1" : "0";
        TCPClientApplication.getInstance().send("-;LOK;" + String.valueOf(this.addr) + ";TOTAL;" + strTotal);
    }

    public void release() {
        TCPClientApplication.getInstance().send("-;LOK;" + String.valueOf(this.addr) + ";RELEASE");
    }

    public void emergencyStop() {
        kmphSpeed = 0;
        stepsSpeed = 0;
        TCPClientApplication.getInstance().send("-;LOK;" + String.valueOf(this.addr) + ";STOP");
    }
}
