package net.ldvsoft.warofviruses;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class SettingsActivity extends AppCompatActivity {
    private static Gson gson = new Gson();

    private int crossHueColor, zeroHueColor;
    private SeekBar crossColor = null, zeroColor = null;
    private FigureSet figureSet = new FigureSet();
    private class ColorChangedListener implements SeekBar.OnSeekBarChangeListener {
        private final GameLogic.PlayerFigure whatChanged;

        private ColorChangedListener(GameLogic.PlayerFigure figure) {
            whatChanged = figure;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (whatChanged == GameLogic.PlayerFigure.CROSS) {
                crossHueColor = seekBar.getProgress();
            } else {
                zeroHueColor = seekBar.getProgress();
            }
            reloadColors();
        }
    }

    private void reloadColors() {
        /*FIXME*/
        for (GameLogic.PlayerFigure figure : GameLogic.PlayerFigure.values()) {
            figureSet.setFigureSource(figure, DefaultFigureSource.NAME);
        }
        figureSet.setHueCross(crossHueColor);
        figureSet.setHueZero(zeroHueColor);
        ((BoardCellButton) findViewById(R.id.color_cross_alive)).setFigure(figureSet, BoardCellState.get(GameLogic.CellType.CROSS));
        ((BoardCellButton) findViewById(R.id.color_cross_dead)).setFigure(figureSet, BoardCellState.get(GameLogic.CellType.DEAD_CROSS));
        ((BoardCellButton) findViewById(R.id.color_zero_alive)).setFigure(figureSet, BoardCellState.get(GameLogic.CellType.ZERO));
        ((BoardCellButton) findViewById(R.id.color_zero_dead)).setFigure(figureSet, BoardCellState.get(GameLogic.CellType.DEAD_ZERO));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long userId = getSharedPreferences(WoVPreferences.PREFERENCES, MODE_PRIVATE).getLong(WoVPreferences.CURRENT_USER_ID, -1);
        User user = DBOpenHelper.getInstance(this).getUserById(userId);
        setContentView(R.layout.activity_settings);

        ((EditText) findViewById(R.id.edit_nickname)).setText(user.getNickNameStr());

        crossColor = (SeekBar) findViewById(R.id.color_cross_value);
        crossColor.setOnSeekBarChangeListener(new ColorChangedListener(GameLogic.PlayerFigure.CROSS));
        crossHueColor = user.getColorCross();
        crossColor.setProgress(crossHueColor);

        zeroColor = (SeekBar) findViewById(R.id.color_zero_value);
        zeroColor.setOnSeekBarChangeListener(new ColorChangedListener(GameLogic.PlayerFigure.ZERO));
        zeroHueColor = user.getColorZero();
        zeroColor.setProgress(zeroHueColor);
        reloadColors();
    }

    private void sendToServer(User user) {
        if (user.getId() == HumanPlayer.USER_ANONYMOUS.getId()) {
            return;
        }
        JsonObject data = new JsonObject();
        // Login without token will just rewrite our row
        data.add(WoVProtocol.LOCAL_USER, gson.toJsonTree(user));
        WoVGcmListenerService.sendGcmMessage(this, WoVProtocol.ACTION_LOGIN, data);
    }

    public void changeNickname(View view) {
        String nickname = ((EditText) findViewById(R.id.edit_nickname)).getText().toString();
        long userId = getSharedPreferences(WoVPreferences.PREFERENCES, MODE_PRIVATE).getLong(WoVPreferences.CURRENT_USER_ID, -1);
        User user = DBOpenHelper.getInstance(this).getUserById(userId);
        user.setNickNameStr(nickname);
        DBOpenHelper.getInstance(this).addUser(user);
        sendToServer(user);
    }

    public void changeCrossColor(View view) {
        long userId = getSharedPreferences(WoVPreferences.PREFERENCES, MODE_PRIVATE).getLong(WoVPreferences.CURRENT_USER_ID, -1);
        User user = DBOpenHelper.getInstance(this).getUserById(userId);
        user.setCrossColor(crossColor.getProgress());
        DBOpenHelper.getInstance(this).addUser(user);
        sendToServer(user);
    }

    public void changeZeroColor(View view) {
        long userId = getSharedPreferences(WoVPreferences.PREFERENCES, MODE_PRIVATE).getLong(WoVPreferences.CURRENT_USER_ID, -1);
        User user = DBOpenHelper.getInstance(this).getUserById(userId);
        user.setZeroColor(zeroColor.getProgress());
        DBOpenHelper.getInstance(this).addUser(user);
        sendToServer(user);
    }
}
