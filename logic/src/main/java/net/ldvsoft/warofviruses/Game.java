package net.ldvsoft.warofviruses;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Сева on 19.10.2015.
 */
public class Game {
    private long id;
    private Player crossPlayer, zeroPlayer;
    private OnGameFinishedListener onGameFinishedListener = null;

    private GameLogic gameLogic;
    private int zeroType;
    private int crossType;

    private List<GameEvent> unconfirmedEvents = new ArrayList<>();

    public int getCrossType() {
        return crossType;
    }

    public int getZeroType() {
        return zeroType;
    }

    public GameLogic.GameState getGameState() {
        return gameLogic.getCurrentGameState();
    }

    public interface OnGameFinishedListener {
        void onGameFinished();
    }

    public static Game deserializeGame(long id, Player crossPlayer, int crossType, Player zeroPlayer, int zeroType, GameLogic gameLogic) {
        Game game = new Game();
        game.id = id;
        game.crossPlayer = crossPlayer;
        game.zeroPlayer = zeroPlayer;
        game.gameLogic = gameLogic;
        game.crossType = crossType;
        game.zeroType = zeroType;
        game.crossPlayer.setGame(game);
        game.zeroPlayer.setGame(game);
        return game;
    }

    public void updateGameInfo() {
        if (isFinished()) {
            return;
        }
        crossPlayer.updateGameInfo(this);
        zeroPlayer.updateGameInfo(this);
    }

    public boolean isFinished() {
        return gameLogic.isFinished();
    }

    public Player getZeroPlayer() {
        return zeroPlayer;
    }

    public Player getCrossPlayer() {
        return crossPlayer;
    }

    /*
    Is called when owner of this game (activity or server) is destroying to properly finish player work and close their resources
     */
    public void onStop() {
        if (crossPlayer != null)
            crossPlayer.onStop();
        if (zeroPlayer != null)
            zeroPlayer.onStop();
    }
    public long getGameId() {
        return id;
    }

    public int getAwaitingEventNumber() {
        return gameLogic.getEventHistory().size();
    }

    public GameLogic getGameLogic() {
        return gameLogic;
    }

    public GameLogic getUnconfirmedGameLogic() {
        GameLogic result = gameLogic;
        for (GameEvent event : unconfirmedEvents) {
            result = event.applyEvent(result);
        }

        if (result == null) {
            return null;
        }

        GameLogic.PlayerFigure newFigure = result.getCurrentPlayerFigure();
        if (newFigure != gameLogic.getCurrentPlayerFigure()) {
            result.blockCurrentPlayer();
        }

        return result;
    }

    public void startNewGame(Player cross, Player zero) {
        id = new SecureRandom().nextLong();
        crossPlayer = cross;
        zeroPlayer = zero;
        crossType = cross.type;
        zeroType = zero.type;
        gameLogic = new GameLogic();
        crossPlayer.setGame(this);
        zeroPlayer.setGame(this);
    }

    public Player getCurrentPlayer() {
        switch (gameLogic.getCurrentPlayerFigure()) {
            case CROSS:
                return crossPlayer;
            case ZERO:
                return zeroPlayer;
            default:
                return null;
        }
    }

    private void notifyPlayer(Player player) {
        if (player != null) {
            player.makeTurn();
        }
    }

    public void setOnGameFinishedListener(OnGameFinishedListener onGameFinishedListener) {
        this.onGameFinishedListener = onGameFinishedListener;
    }

    private Player getPlayerByFigure(GameLogic.PlayerFigure figure) {
        switch (figure) {
            case CROSS:
                return crossPlayer;
            case ZERO:
                return zeroPlayer;
            case NONE:
                return null;
        }
        return null;
    }

    public boolean giveUp(Player sender) {
        if (isWaitingForConfirm()) {
            return false;
        }

        GameLogic result = getUnconfirmedGameLogic().giveUp(sender.ownFigure);
        if (result != null) {
            unconfirmedEvents.add(result.getLastEvent());
            return confirm(sender);
        }
        return false;
    }

    public boolean isWaitingForConfirm() {
        return getUnconfirmedGameLogic().getCurrentPlayerFigure() != gameLogic.getCurrentPlayerFigure();
    }

    public boolean skipTurn(Player sender) {
        if (!sender.equals(getCurrentPlayer()) || isWaitingForConfirm()) {
            return false;
        }

        GameLogic result = getUnconfirmedGameLogic().skipTurn();
        if (result != null) {
            unconfirmedEvents.add(result.getLastEvent());
            return confirm(sender);
        }
        return false;
    }

    public void update() {
        crossPlayer.setGame(this);
        zeroPlayer.setGame(this);
    }
    public boolean doTurn(Player sender, int x, int y) {
        if (!sender.equals(getCurrentPlayer()) || isWaitingForConfirm()) {
            return false;
        }

        GameLogic result = getUnconfirmedGameLogic().doTurn(x, y);
        if (result != null) {
            unconfirmedEvents.add(result.getLastEvent());
        }

        return result != null;
    }

    public boolean cancelTurn(Player sender) {
        if (unconfirmedEvents.isEmpty()) {
            return false;
        }
        unconfirmedEvents.remove(unconfirmedEvents.size() - 1);
        return true;
    }

    private void gameStateChanged(GameEvent event, Player sender) {
        if (sender.equals(zeroPlayer)) {
            crossPlayer.onGameStateChanged(event, sender);
        } else {
            zeroPlayer.onGameStateChanged(event, sender);
        }

    }

    public boolean confirm(Player sender) {
        if (!sender.equals(getCurrentPlayer())) {
            return false;
        }

        GameLogic.PlayerFigure oldPlayerFigure = gameLogic.getCurrentPlayerFigure();

        if (!getUnconfirmedGameLogic().isBlocked()) {
            return false;
        }

        List<GameEvent> lastEvents = new ArrayList<>(unconfirmedEvents);
        unconfirmedEvents.clear();
        for (GameEvent event: lastEvents) {
            gameStateChanged(event, sender);
            gameLogic = event.applyEvent(gameLogic);
        }

        if (oldPlayerFigure != gameLogic.getCurrentPlayerFigure()) {
            notifyPlayer(getPlayerByFigure(GameLogic.getOpponentPlayerFigure(oldPlayerFigure)));
        }

        return true;
    }

    public GameLogic.PlayerFigure getMyFigure(long userId) {
        if (crossPlayer.getUser().getId() == zeroPlayer.getUser().getId()) {
            //Just a local game
            return GameLogic.PlayerFigure.NONE;
        }
        if (crossPlayer.getUser().getId() == userId) {
            return GameLogic.PlayerFigure.CROSS;
        } else {
            return GameLogic.PlayerFigure.ZERO;
        }

    }

    public boolean applyPlayerEvents(List<GameEvent> events, Player sender) {
        if (sender != getCurrentPlayer()) {
            return false;
        }

        GameLogic result = gameLogic;
        GameLogic.PlayerFigure oldPlayerFigure = gameLogic.getCurrentPlayerFigure();
        unconfirmedEvents.clear();
        for (GameEvent event : events) {
            try {
                Thread.sleep(750);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            result = event.applyEvent(result);
            unconfirmedEvents.add(event);
            if (result == null) {
                unconfirmedEvents.clear();
                return false;
            }
            gameStateChanged(event, sender);
        }

        if (oldPlayerFigure != result.getCurrentPlayerFigure()) {
            gameLogic = result;
            unconfirmedEvents.clear();
            notifyPlayer(getPlayerByFigure(GameLogic.getOpponentPlayerFigure(oldPlayerFigure)));
            return true;
        }

        unconfirmedEvents.clear();
        return false;
    }
}
