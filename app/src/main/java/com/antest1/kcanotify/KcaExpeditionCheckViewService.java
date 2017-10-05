package com.antest1.kcanotify;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.antest1.kcanotify.KcaApiData.checkUserShipDataLoaded;
import static com.antest1.kcanotify.KcaApiData.getKcShipDataById;
import static com.antest1.kcanotify.KcaApiData.getShipTypeAbbr;
import static com.antest1.kcanotify.KcaApiData.getUserItemStatusById;
import static com.antest1.kcanotify.KcaApiData.getUserShipDataById;
import static com.antest1.kcanotify.KcaConstants.DB_KEY_DECKPORT;
import static com.antest1.kcanotify.KcaConstants.PREF_KCA_LANGUAGE;
import static com.antest1.kcanotify.KcaUtils.getId;
import static com.antest1.kcanotify.KcaUtils.getStringFromException;
import static com.antest1.kcanotify.KcaUtils.getTimeStr;
import static com.antest1.kcanotify.KcaUtils.getWindowLayoutType;
import static com.antest1.kcanotify.KcaUtils.joinStr;

public class KcaExpeditionCheckViewService extends Service {
    public static final String SHOW_EXCHECKVIEW_ACTION = "show_excheckview";

    public static final double[][] toku_bonus = {
            {2.0, 2.0, 2.0, 2.0, 2.0},
            {4.0, 4.0, 4.0, 4.0, 4.0},
            {5.0, 5.0, 5.2, 5.4, 5.4},
            {5.4, 5.6, 5.8, 5.9, 6.0}
    };

    public static boolean active;
    static boolean error_flag = false;
    Context contextWithLocale;
    int displayWidth = 0;
    public KcaDBHelper dbHelper;
    private View mView, itemView;
    LayoutInflater mInflater;
    private WindowManager mManager;
    WindowManager.LayoutParams mParams;

    String locale;
    int selected = 1;
    JsonArray deckdata;
    List<JsonObject> ship_data;
    Map<String, JsonObject> checkdata;

    public String getStringWithLocale(int id) {
        return KcaUtils.getStringWithLocale(getApplicationContext(), getBaseContext(), id);
    }

    private void showInfoView(MotionEvent paramMotionEvent, int selected) {
        setItemViewLayout(KcaApiData.getExpeditionInfo(selected, locale));
        itemView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int width = itemView.getMeasuredWidth();
        int height = itemView.getMeasuredHeight();

        WindowManager.LayoutParams localLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getWindowLayoutType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        localLayoutParams.x = ((int) (50.0F + paramMotionEvent.getRawX()));
        if ((selected - 1) % 8 >= 4) localLayoutParams.x -= (100 + width);
        localLayoutParams.y = ((int) paramMotionEvent.getRawY());
        localLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        if (itemView.getParent() != null) {
            mManager.removeViewImmediate(itemView);
        }
        mManager.addView(itemView, localLayoutParams);
    }

    private void updateSelectedView(int idx) {
        for (int i = 1; i < 4; i++) {
            int view_id = getId("fleet_".concat(String.valueOf(i + 1)), R.id.class);
            if (idx == i) {
                mView.findViewById(view_id).setBackgroundColor(
                        ContextCompat.getColor(getApplicationContext(), R.color.colorAccent));
            } else {
                mView.findViewById(view_id).setBackgroundColor(
                        ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary));
            }
        }
    }

    public IBinder onBind(Intent paramIntent) {
        return null;
    }

    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(getApplicationContext())) {
            // Can not draw overlays: pass
            stopSelf();
        }

        try {
            active = true;
            locale = LocaleUtils.getLocaleCode(KcaUtils.getStringPreferences(getApplicationContext(), PREF_KCA_LANGUAGE));
            ship_data = new ArrayList<>();
            checkdata = new HashMap<>();
            dbHelper = new KcaDBHelper(getApplicationContext(), null, 3);
            contextWithLocale = KcaUtils.getContextWithLocale(getApplicationContext(), getBaseContext());
            mInflater = LayoutInflater.from(contextWithLocale);
            mView = mInflater.inflate(R.layout.view_excheck_list, null);
            mView.setVisibility(View.GONE);
            mView.findViewById(R.id.excheckview_head).setOnTouchListener(mViewTouchListener);
            for (int i = 1; i < 4; i++) {
                mView.findViewById(getId("fleet_".concat(String.valueOf(i + 1)), R.id.class)).setOnTouchListener(mViewTouchListener);
            }

            for (int i = 0; i < 40; i++) {
                mView.findViewById(KcaUtils.getId("expedition_btn_".concat(String.valueOf(i + 1)), R.id.class)).setOnTouchListener(mViewTouchListener);
            }

            if (KcaApiData.isEventTime) {
                mView.findViewById(R.id.expedition_btn_133).setOnTouchListener(mViewTouchListener);
                mView.findViewById(R.id.expedition_btn_134).setOnTouchListener(mViewTouchListener);
                mView.findViewById(R.id.excheck_row_e).setVisibility(View.VISIBLE);
            } else {
                mView.findViewById(R.id.excheck_row_e).setVisibility(View.GONE);
            }

            itemView = mInflater.inflate(R.layout.view_excheck_detail, null);
            mParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    getWindowLayoutType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            mParams.gravity = Gravity.CENTER;

            mManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            mManager.addView(mView, mParams);

            Display display = ((WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            displayWidth = size.x;
        } catch (Exception e) {
            active = false;
            error_flag = true;
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        active = false;
        if (mView != null) {
            if (mView.getParent() != null) mManager.removeViewImmediate(mView);
        }
        if (itemView != null) {
            if (itemView.getParent() != null) mManager.removeViewImmediate(itemView);
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().startsWith(SHOW_EXCHECKVIEW_ACTION)) {
                deckdata = dbHelper.getJsonArrayValue(DB_KEY_DECKPORT);
                int selected_new = Integer.parseInt(intent.getAction().split("/")[1]);
                if (selected_new < 1) selected_new = 1;
                else if (selected_new > 3) selected_new = 2;
                if (selected_new < deckdata.size()) {
                    selected = selected_new;
                }
                int setViewResult = setView();
                if (setViewResult == 0) {
                    if (mView.getParent() != null) {
                        mManager.removeViewImmediate(mView);
                    }
                    mManager.addView(mView, mParams);
                }
                Log.e("KCA", "show_excheckview_action " + String.valueOf(setViewResult));
                mView.setVisibility(View.VISIBLE);
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private JsonObject checkFleetCondition(String data) {
        boolean total_pass = false;
        Map<String, Integer> stypedata = new HashMap<>();
        for (JsonObject obj : ship_data) {
            String stype = String.valueOf(obj.get("stype").getAsInt());
            if (stypedata.containsKey(stype)) {
                stypedata.put(stype, stypedata.get(stype) + 1);
            } else {
                stypedata.put(stype, 1);
            }
        }

        JsonArray value = new JsonArray();
        String[] conds = data.split("/");
        for (String cond : conds) {
            boolean partial_pass = true;
            JsonObject cond_check = new JsonObject();
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            String[] shipcond = cond.split("\\|");
            for (String sc : shipcond) {
                String[] ship_count = sc.split("\\-");
                String[] ship = ship_count[0].split(",");
                int count = Integer.valueOf(ship_count[1]);
                List<String> ship_list = new ArrayList<>();
                for (String s : ship) {
                    if (stypedata.containsKey(s)) {
                        count -= stypedata.get(s);
                    }
                }
                cond_check.addProperty(ship_count[0], count <= 0);
                partial_pass = partial_pass && (count <= 0);
            }
            total_pass = total_pass || partial_pass;
            value.add(cond_check);
        }
        JsonObject result = new JsonObject();
        result.addProperty("pass", total_pass);
        result.add("value", value);
        return result;
    }

    private JsonObject checkCondition(int exp_no) {
        boolean total_pass = true;
        JsonObject result = new JsonObject();

        JsonObject data = KcaApiData.getExpeditionInfo(exp_no, locale);

        boolean has_flag_lv = data.has("flag-lv");
        boolean has_flag_cond = data.has("flag-cond");
        boolean has_total_lv = data.has("total-lv");
        boolean has_total_cond = data.has("total-cond");
        boolean has_drum_ship = data.has("drum-ship");
        boolean has_drum_num = data.has("drum-num");
        boolean has_drum_num_optional = data.has("drum-num-optional");

        int total_num = data.get("total-num").getAsInt();
        result.addProperty("total-num", ship_data.size() >= total_num);
        total_pass = total_pass && (ship_data.size() >= total_num);

        result.addProperty("flag-lv", true);
        if (has_flag_lv) {
            if (ship_data.size() > 0) {
                int flag_lv_value = ship_data.get(0).get("lv").getAsInt();
                int flag_lv = data.get("flag-lv").getAsInt();
                result.addProperty("flag-lv", flag_lv_value >= flag_lv);
                total_pass = total_pass && (flag_lv_value >= flag_lv);
            } else {
                result.addProperty("flag-cond", false);
                total_pass = false;
            }
        }

        result.addProperty("flag-cond", true);
        if (has_flag_cond) {
            if (ship_data.size() > 0) {
                int flag_conv_value = ship_data.get(0).get("stype").getAsInt();
                int flag_cond = data.get("flag-cond").getAsInt();
                result.addProperty("flag-cond", flag_conv_value == flag_cond);
                total_pass = total_pass && (flag_conv_value == flag_cond);
            } else {
                result.addProperty("flag-cond", false);
                total_pass = false;
            }
        }

        result.addProperty("total-lv", true);
        if (has_total_lv) {
            int total_lv_value = 0;
            for (JsonObject obj : ship_data) {
                total_lv_value += obj.get("lv").getAsInt();
            }
            int total_lv = data.get("total-lv").getAsInt();
            result.addProperty("total-lv", total_lv_value >= total_lv);
            total_pass = total_pass && (total_lv_value >= total_lv);
        }

        if (has_total_cond) {
            String total_cond = data.get("total-cond").getAsString();
            JsonObject total_cond_result = checkFleetCondition(total_cond);
            result.add("total-cond", total_cond_result.getAsJsonArray("value"));
            total_pass = total_pass && total_cond_result.get("pass").getAsBoolean();
        }

        // Drum: 75
        int drum_ship_value = 0;
        int drum_num_value = 0;

        for (JsonObject obj : ship_data) {
            int count = 0;
            for (JsonElement itemobj : obj.getAsJsonArray("item")) {
                int slotitem_id = itemobj.getAsJsonObject().get("slotitem_id").getAsInt();
                int level = itemobj.getAsJsonObject().get("level").getAsInt();
                if (slotitem_id == 75) {
                    drum_num_value += 1;
                    count += 1;
                }
            }
            if (count > 0) drum_ship_value += 1;
        }

        result.addProperty("drum-ship", true);
        result.addProperty("drum-num", true);
        result.addProperty("drum-num-optional", true);

        if (has_drum_ship) {
            int drum_ship = data.get("drum-ship").getAsInt();
            result.addProperty("drum-ship", drum_ship_value >= drum_ship);
            total_pass = total_pass && (drum_ship_value >= drum_ship);
        }
        if (has_drum_num) {
            int drum_num = data.get("drum-num").getAsInt();
            result.addProperty("drum-num", drum_num_value >= drum_num);
            total_pass = total_pass && (drum_num_value >= drum_num);
        } else if (has_drum_num_optional) {
            int drum_num = data.get("drum-num-optional").getAsInt();
            result.addProperty("drum-num-optional", drum_num_value >= drum_num);
            total_pass = total_pass && (drum_num_value >= drum_num);
        }

        result.addProperty("pass", total_pass);
        return result;
    }

    // Daihatsu: 68, 89Tank: 166, Amp: 167, Toku-Daihatsu: 193
    private JsonObject getBonus() {
        double bonus = 0.0;
        boolean kinu_exist = false;
        int bonus_count = 0;
        int daihatsu_count = 0;
        int tank_count = 0;
        int amp_count = 0;
        int toku_count = 0;
        double bonus_level = 0.0;
        JsonObject result = new JsonObject();

        for (JsonObject obj : ship_data) {
            int count = 0;
            if (obj.get("ship_id").getAsInt() == 487) { // Kinu Kai Ni
                bonus += 5.0;
                kinu_exist = true;
            }
            for (JsonElement itemobj : obj.getAsJsonArray("item")) {
                int slotitem_id = itemobj.getAsJsonObject().get("slotitem_id").getAsInt();
                int level = itemobj.getAsJsonObject().get("level").getAsInt();
                switch (slotitem_id) {
                    case 68:
                        bonus += 5.0;
                        bonus_level += level;
                        bonus_count += 1.0;
                        daihatsu_count += 1;
                        break;
                    case 166:
                        bonus += 2.0;
                        bonus_level += level;
                        bonus_count += 1.0;
                        tank_count += 1;
                        break;
                    case 167:
                        bonus += 1.0;
                        bonus_level += level;
                        bonus_count += 1.0;
                        amp_count += 1;
                        break;
                    case 193:
                        bonus += 5.0;
                        bonus_level += level;
                        bonus_count += 1.0;
                        toku_count += 1;
                        break;
                    default:
                        break;
                }
            }
        }
        if (bonus_count > 0) bonus_level /= bonus_count;
        int left_count = bonus_count - toku_count;
        if (left_count > 4) left_count = 4;
        if (bonus > 20) bonus = 20.0;
        bonus += (100.0 + 0.2 * bonus_level);
        if (toku_count > 0) {
            int toku_idx;
            if (toku_count > 4) toku_idx = 3;
            else toku_idx = toku_count - 1;
            bonus += toku_bonus[toku_idx][left_count];
        }
        result.addProperty("kinu", kinu_exist);
        result.addProperty("daihatsu", daihatsu_count);
        result.addProperty("tank", tank_count);
        result.addProperty("amp", amp_count);
        result.addProperty("toku", toku_count);
        result.addProperty("bonus", bonus);
        return result;
    }

    private void setItemViewVisibilityById(int id, boolean visible) {
        int visible_value = visible ? View.VISIBLE : View.GONE;
        itemView.findViewById(id).setVisibility(visible_value);
    }

    private void setItemTextViewById(int id, String value) {
        ((TextView) itemView.findViewById(id)).setText(value);
    }

    private void setItemTextViewColorById(int id, boolean value, boolean is_option) {
        if (value) {
            ((TextView) itemView.findViewById(id)).setTextColor(ContextCompat
                    .getColor(getApplicationContext(), R.color.colorExpeditionBtnGoodBack));
        } else if (is_option) {
            ((TextView) itemView.findViewById(id)).setTextColor(ContextCompat
                    .getColor(getApplicationContext(), R.color.grey));
        } else {
            ((TextView) itemView.findViewById(id)).setTextColor(ContextCompat
                    .getColor(getApplicationContext(), R.color.colorExpeditionBtnFailBack));
        }
    }


    private String convertTotalCond(String str) {
        String[] ship_count = str.split("\\-");
        String[] ship = ship_count[0].split(",");
        List<String> ship_list = new ArrayList<>();
        for (String s : ship) {
            ship_list.add(getShipTypeAbbr(Integer.parseInt(s)));
        }
        String ship_concat = joinStr(ship_list, "/");
        return ship_concat.concat(":").concat(ship_count[1]);
    }

    private List<View> generateConditionView(String data, JsonArray check) {
        List<View> views = new ArrayList<>();
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 14, 0);

        String[] conds = data.split("/");
        int count = 0;
        for (String cond : conds) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            String[] shipcond = cond.split("\\|");
            for (String sc : shipcond) {
                TextView scView = new TextView(this);
                scView.setText(convertTotalCond(sc));
                if(check.get(count).getAsJsonObject().get(sc.split("\\-")[0]).getAsBoolean()) {
                    scView.setTextColor(ContextCompat
                            .getColor(getApplicationContext(), R.color.colorExpeditionBtnGoodBack));
                } else {
                    scView.setTextColor(ContextCompat
                            .getColor(getApplicationContext(), R.color.colorExpeditionBtnFailBack));
                }
                rowLayout.addView(scView, params);
            }
            views.add(rowLayout);
            count += 1;
        }
        return views;
    }

    public void setItemViewLayout(JsonObject data) {
        String no = data.get("no").getAsString();
        String name = data.get("name").getAsString();
        String title = String.format("[%s] %s", no, name);
        int time = data.get("time").getAsInt() * 60;
        int total_num = data.get("total-num").getAsInt();

        JsonObject check = checkdata.get(no);

        boolean has_flag_lv = data.has("flag-lv");
        boolean has_flag_cond = data.has("flag-cond");
        boolean has_flag_info = has_flag_lv || has_flag_cond;
        boolean has_total_lv = data.has("total-lv");
        boolean has_total_cond = data.has("total-cond");
        boolean has_drum_ship = data.has("drum-ship");
        boolean has_drum_num = data.has("drum-num");
        boolean has_drum_num_optional = data.has("drum-num-optional");
        boolean has_drum_info = has_drum_ship || has_drum_num || has_drum_num_optional;

        ((LinearLayout) itemView.findViewById(R.id.view_excheck_fleet_condition)).removeAllViews();

        ((TextView) itemView.findViewById(R.id.view_excheck_title))
                .setText(title);
        ((TextView) itemView.findViewById(R.id.view_excheck_time))
                .setText(getTimeStr(time));

        ((TextView) itemView.findViewById(R.id.view_excheck_fleet_total_num))
                .setText(String.format("Total %d", total_num));
        setItemTextViewColorById(R.id.view_excheck_fleet_total_num,
                check.get("total-num").getAsBoolean(), false);

        setItemViewVisibilityById(R.id.view_excheck_flagship, has_flag_info);
        if (has_flag_info) {
            setItemViewVisibilityById(R.id.view_excheck_flagship_lv, has_flag_lv);
            if (has_flag_lv) {
                int flag_lv = data.get("flag-lv").getAsInt();
                setItemTextViewById(R.id.view_excheck_flagship_lv,
                        String.format("Lv %d", flag_lv));
                setItemTextViewColorById(R.id.view_excheck_flagship_lv,
                        check.get("flag-lv").getAsBoolean(), false);
            }
            setItemViewVisibilityById(R.id.view_excheck_flagship_cond, has_flag_cond);
            if (has_flag_cond) {
                int flag_cond = data.get("flag-cond").getAsInt();
                setItemTextViewById(R.id.view_excheck_flagship_cond,
                        getShipTypeAbbr(flag_cond));
                setItemTextViewColorById(R.id.view_excheck_flagship_cond,
                        check.get("flag-cond").getAsBoolean(), false);
            }
        }

        setItemViewVisibilityById(R.id.view_excheck_fleet_total_lv, has_total_lv);
        if (has_total_lv) {
            int total_lv = data.get("total-lv").getAsInt();
            setItemTextViewById(R.id.view_excheck_fleet_total_lv,
                    String.format("Total Lv %d", total_lv));
            setItemTextViewColorById(R.id.view_excheck_fleet_total_lv,
                    check.get("total-lv").getAsBoolean(), false);
        }

        setItemViewVisibilityById(R.id.view_excheck_fleet_condition, has_total_cond);
        if (has_total_cond) {
            String total_cond = data.get("total-cond").getAsString();
            for (View v : generateConditionView(total_cond, check.getAsJsonArray("total-cond"))) {
                ((LinearLayout) itemView.findViewById(R.id.view_excheck_fleet_condition)).addView(v);
            }
        }

        setItemViewVisibilityById(R.id.view_excheck_drum, has_drum_info);
        if (has_drum_info) {
            setItemViewVisibilityById(R.id.view_excheck_drum_ship, has_drum_ship);
            if (has_drum_ship) {
                int drum_ship = data.get("drum-ship").getAsInt();
                setItemTextViewById(R.id.view_excheck_drum_ship,
                        String.format("%d Ships", drum_ship));
                setItemTextViewColorById(R.id.view_excheck_drum_ship,
                        check.get("drum-ship").getAsBoolean(), false);
            }
            setItemViewVisibilityById(R.id.view_excheck_drum_count, has_drum_num || has_drum_num_optional);
            if (has_drum_num) {
                int drum_num = data.get("drum-num").getAsInt();
                setItemTextViewById(R.id.view_excheck_drum_count,
                        String.format("%d Drums", drum_num));
                setItemTextViewColorById(R.id.view_excheck_drum_count,
                        check.get("drum-num").getAsBoolean(), false);
            } else if (has_drum_num_optional) {
                int drum_num = data.get("drum-num-optional").getAsInt();
                setItemTextViewById(R.id.view_excheck_drum_count,
                        String.format("%d Drums", drum_num));
                setItemTextViewColorById(R.id.view_excheck_drum_count,
                        check.get("drum-num").getAsBoolean(), true);
            }
        }
        itemView.setVisibility(View.VISIBLE);
    }

    public int setView() {
        try {
            JsonArray api_ship = deckdata.get(selected)
                    .getAsJsonObject().getAsJsonArray("api_ship");
            ship_data.clear();
            if (checkUserShipDataLoaded()) {
                for (int i = 0; i < api_ship.size(); i++) {
                    int id = api_ship.get(i).getAsInt();
                    if (id > 0) {
                        JsonObject data = new JsonObject();
                        JsonObject usershipinfo = getUserShipDataById(id, "ship_id,lv,slot,cond");
                        JsonObject kcshipinfo = getKcShipDataById(usershipinfo.get("ship_id").getAsInt(), "stype");
                        data.addProperty("ship_id", usershipinfo.get("ship_id").getAsInt());
                        data.addProperty("lv", usershipinfo.get("lv").getAsInt());
                        data.addProperty("cond", usershipinfo.get("cond").getAsInt());
                        data.addProperty("stype", kcshipinfo.get("stype").getAsInt());
                        data.add("item", new JsonArray());

                        JsonArray shipslot = usershipinfo.getAsJsonArray("slot");
                        for (int j = 0; j < shipslot.size(); j++) {
                            int itemid = shipslot.get(j).getAsInt();
                            if (itemid > 0) {
                                JsonObject iteminfo = getUserItemStatusById(itemid, "slotitem_id,level", "");
                                data.getAsJsonArray("item").add(iteminfo);
                            }
                        }
                        ship_data.add(data);
                    }
                }
                for (int i = 1; i <= 40; i++) {
                    String key = String.valueOf(i);
                    checkdata.put(key, checkCondition(i));
                    if (checkdata.get(key).get("pass").getAsBoolean()) {
                        mView.findViewById(getId("expedition_btn_".concat(String.valueOf(i)), R.id.class))
                                .setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.colorExpeditionBtnGoodBack));
                        ((TextView) mView.findViewById(getId("expedition_btn_".concat(String.valueOf(i)), R.id.class)))
                                .setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.colorExpeditionBtnGoodText));
                    } else {
                        mView.findViewById(getId("expedition_btn_".concat(String.valueOf(i)), R.id.class))
                                .setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.colorExpeditionBtnFailBack));
                        ((TextView) mView.findViewById(getId("expedition_btn_".concat(String.valueOf(i)), R.id.class)))
                                .setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.colorExpeditionBtnFailText));
                    }
                }
                if (KcaApiData.isEventTime) {
                    checkdata.put("133", checkCondition(133));
                    checkdata.put("134", checkCondition(134));
                }
                JsonObject bonus_info = getBonus();
                updateSelectedView(selected);
                ((TextView) mView.findViewById(R.id.excheck_info)).setText(bonus_info.toString());
                mView.findViewById(R.id.excheck_info).setBackgroundColor(
                        ContextCompat.getColor(getApplicationContext(), R.color.colorFleetInfoExpedition));
            } else {
                ((TextView) mView.findViewById(R.id.excheck_info)).setText(getStringWithLocale(R.string.kca_init_content));
                mView.findViewById(R.id.excheck_info).setBackgroundColor(
                        ContextCompat.getColor(getApplicationContext(), R.color.colorFleetInfoNoShip));
            }
            return 0;
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), getStringFromException(e), Toast.LENGTH_LONG).show();
            return 1;
        }
    }

    private View.OnTouchListener mViewTouchListener = new View.OnTouchListener() {
        private static final int MAX_CLICK_DURATION = 200;
        private long startClickTime = -1;
        private long clickDuration;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int id = v.getId();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Log.e("KCA-FV", "ACTION_DOWN");
                    startClickTime = Calendar.getInstance().getTimeInMillis();
                    for (int i = 0; i < 40; i++) {
                        if (id == mView.findViewById(getId("expedition_btn_".concat(String.valueOf(i + 1)), R.id.class)).getId()) {
                            setItemViewLayout(KcaApiData.getExpeditionInfo(i + 1, locale));
                            showInfoView(event, i + 1);
                            break;
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    Log.e("KCA-FV", "ACTION_UP");
                    clickDuration = Calendar.getInstance().getTimeInMillis() - startClickTime;
                    itemView.setVisibility(View.GONE);

                    if (clickDuration < MAX_CLICK_DURATION) {
                        if (id == mView.findViewById(R.id.excheckview_head).getId()) {
                            stopSelf();
                        } else {
                            for (int i = 1; i < 4; i++) {
                                if (id == mView.findViewById(getId("fleet_".concat(String.valueOf(i + 1)), R.id.class)).getId()) {
                                    if (i < deckdata.size()) {
                                        selected = i;
                                    }
                                    setView();
                                    break;
                                }
                            }
                        }
                    }
                    break;
            }
            return true;
        }
    };
}