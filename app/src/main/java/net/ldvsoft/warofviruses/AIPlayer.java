package net.ldvsoft.warofviruses;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static java.lang.Math.abs;
import static java.lang.Math.min;
import static java.lang.Thread.sleep;
import static net.ldvsoft.warofviruses.GameLogic.BOARD_SIZE;
import static net.ldvsoft.warofviruses.GameLogic.CoordinatePair;
import static net.ldvsoft.warofviruses.GameLogic.GameState;
import static net.ldvsoft.warofviruses.GameLogic.PlayerFigure;

/**
 * Created by Сева on 20.10.2015.
 */
public class AIPlayer extends Player {
    public static final User AI_USER = new User(
            DBProvider.USER_AI_PLAYER_ID,
            WoVPreferences.AI_GOOGLE_TOKEN,
            WoVPreferences.AI_NICKNAME_STR, WoVPreferences.AI_NICKNAME_ID,
            WoVPreferences.DEFAULT_CROSS_COLOR, WoVPreferences.DEFAULT_ZERO_COLOR,
            null);
    private AsyncTask<Void, CoordinatePair, Void> runningStrategy;

    static class MoveCostPair {
        CoordinatePair move;
        double cost;
        MoveCostPair(CoordinatePair move, double cost) {
            this.move = move;
            this.cost = cost;
        }
    }

    public AIPlayer(GameLogic.PlayerFigure ownFigure) {
        this.ownFigure = ownFigure;
        this.user = AI_USER;
        this.type = 1;
        runningStrategy = new BruteforceStrategy();
    }

    public static AIPlayer deserialize(User user, GameLogic.PlayerFigure ownFigure, Context context) {
        // There is only one AI user
        return new AIPlayer(ownFigure);
    }

    @Override
    public void makeTurn() {
        Log.d("AIPlayer", "Turn passed to AI player");
        runningStrategy = new BruteforceStrategy();
        runningStrategy.execute();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (runningStrategy != null) {
            runningStrategy.cancel(true);
        }
    }

    @Override
    public void onGameStateChanged(GameEvent event, Player whoChanged) {
    }

    @Override
    public void setGame(Game game) {
        super.setGame(game);
        if (!game.isFinished() && game.getCurrentPlayer() == this) {
            makeTurn();
        }
    }

    private class BruteforceStrategy extends AsyncTask<Void, CoordinatePair, Void> {
        final static double DANGER_FACTOR = 1.25, CONTROL_FACTOR = 1.0, TURN_COUNT_FACTOR = 0.05;
        private static final int GREEDY_TURNS_FOR_ENEMY = 1; //to avoid immediate danger
        private static final String TAG = "BruteforceStrategy";

        //always returns score for ai player.
        private double getEndGameScore(GameLogic game) {
            switch (game.getCurrentGameState()) {
                case CROSS_WON:
                    return ownFigure == PlayerFigure.CROSS ? +100000 : -100000;
                case ZERO_WON:
                    return ownFigure == PlayerFigure.ZERO  ? +100000 : -100000;
                default:
                    return 0;
            }
        }

        //always returns score for ai player.
        private double getControlledCellsScore(GameLogic game) {

            if (game.getCurrentGameState() != GameState.RUNNING) {
                return getEndGameScore(game);
            }

            double result = 0;

            boolean currentPlayerChanged = false;
            if (game.getCurrentPlayerFigure() != ownFigure) {
                game.setCurrentPlayerToOpponent();
                currentPlayerChanged = true;
            }

            for (int sign : new int[]{1, -1}) {

                for (int i = 0; i < BOARD_SIZE; i++) {
                    for (int j = 0; j < BOARD_SIZE; j++) {
                        if (game.getCellAt(i, j).isActive()) {
                            result += sign * CONTROL_FACTOR;
                        }
                        if (game.getCellAt(i, j).canMakeTurn() &&
                                game.getCellAt(i, j).getCellType().getOwner() == GameLogic.getOpponentPlayerFigure(game.getCurrentPlayerFigure())) {
                            result += sign * DANGER_FACTOR;
                        }
                        if (game.getCellAt(i, j).canMakeTurn()) {
                            result += sign * TURN_COUNT_FACTOR;
                        }
                    }
                }

                game.setCurrentPlayerToOpponent();
            }

            //we've probably just damaged current game state
            if (currentPlayerChanged) {
                game.setCurrentPlayerToOpponent();
            }
            return result;
        }

        private void runStrategy(GameLogic gameLogic) {
            List<CoordinatePair> optMoves = bruteforceMoves(gameLogic);
            if (isCancelled()) {
                return;
            }

            if (optMoves.size() == 0) { //There were no possible turns for me
                gameLogic = gameLogic.skipTurn();
            } else {
                for (CoordinatePair move : optMoves) {
                    gameLogic = gameLogic.doTurn(move.x, move.y);
                }
            }
            game.applyPlayerEvents(gameLogic.getLastEventsBy(ownFigure), AIPlayer.this);
        }

        private List<CoordinatePair> orderMovesByCost(List<CoordinatePair> moves, GameLogic curGameLogic) {
            List<MoveCostPair> movesWithCosts = new ArrayList<>();
            for (CoordinatePair move : moves) {
                GameLogic logic = curGameLogic.doTurn(move.x, move.y);
                movesWithCosts.add(new MoveCostPair(move, getControlledCellsScore(logic)));
            }

            Collections.sort(movesWithCosts, new Comparator<MoveCostPair>() {
                @Override
                public int compare(MoveCostPair lhs, MoveCostPair rhs) {
                    return Double.compare(lhs.cost, rhs.cost);
                }
            });

            List<CoordinatePair> result = new ArrayList<>();
            for (MoveCostPair pair : movesWithCosts) {
                result.add(pair.move);
            }
            return result;
        }

        private List<CoordinatePair> bruteforceMoves(GameLogic gameLogic) {
            if (isCancelled()) {
                return new ArrayList<>();
            }

            List<CoordinatePair> result = new ArrayList<>();

            if (gameLogic.getCurrentPlayerFigure() != ownFigure) {
                return result;
            }

            List<CoordinatePair> moves = gameLogic.getMoves();

            if (moves.size() == 0) {
                return result;
            }

            moves = orderMovesByCost(moves, gameLogic);
            Collections.reverse(moves); //take into account only moves that give us highest scores
            int newMovesSize = min(moves.size(), 5);
            moves = Arrays.asList(Arrays.copyOf(moves.toArray(new CoordinatePair[newMovesSize]), newMovesSize));

            double optScore = -10000;

            for (CoordinatePair move: moves) {
                GameLogic tmpGameLogic = gameLogic.doTurn(move.x, move.y);
                List<CoordinatePair> optMoves = bruteforceMoves(tmpGameLogic);

                for (CoordinatePair optMove : optMoves) {
                    tmpGameLogic = tmpGameLogic.doTurn(optMove.x, optMove.y);
                }
                for (int i = 0; i < GREEDY_TURNS_FOR_ENEMY; i++) {
                    ArrayList<CoordinatePair> enemyMoves = tmpGameLogic.getMoves();
                    orderMovesByCost(enemyMoves, tmpGameLogic);
                    if (enemyMoves.size() == 0) {
                        break;
                    }
                    tmpGameLogic = tmpGameLogic.doTurn(enemyMoves.get(0).x, enemyMoves.get(0).y); //greedy minimize AI score
                }

                double newScore = getControlledCellsScore(tmpGameLogic);
                if (newScore > optScore) {
                    optScore = newScore;
                    result = optMoves;
                    result.add(0, move);
                }
            }
            return result;
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.d("AIPlayer", "AIPlayer::run");
            runStrategy(game.getGameLogic());
            Log.d("AIPlayer", "Turn finished");
            return null;
        }
    }
}
