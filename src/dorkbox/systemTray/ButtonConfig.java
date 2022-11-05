package dorkbox.systemTray;

import java.util.HashMap;
import java.util.Map;

public class ButtonConfig{

    public enum ActionType {
        NONE, SHOW_MENU, CUSTOM;
    }

    public enum Button {
        LEFT, RIGHT
    }

    public static class PerButtonConfig {
        private ActionType actionType = ActionType.SHOW_MENU;
        private Runnable customAction;

        public ActionType getActionType() {
            return actionType;
        }

        public Runnable getCustomAction() {
            return customAction;
        }
    }

    public interface Configurator {
        void none();
        void showMenu();

        void custom(Runnable action);
    }

    private final Map<Button, PerButtonConfig> config = new HashMap<>();

    ButtonConfig() {
        config.put(Button.LEFT, new PerButtonConfig());
        config.put(Button.RIGHT, new PerButtonConfig());
    }

    private Configurator configurator(Button button) {
        return new Configurator() {
            @Override
            public void none() {
                config.get(button).actionType = ActionType.NONE;
            }

            @Override
            public void showMenu() {
                config.get(button).actionType = ActionType.SHOW_MENU;
            }

            @Override
            public void custom(Runnable action) {
                config.get(button).actionType = ActionType.CUSTOM;
                config.get(button).customAction = action;
            }

        };
    }

    public Configurator left() {
        return configurator(Button.LEFT);
    }

    public Configurator right() {
        return configurator(Button.RIGHT);
    }

    public PerButtonConfig getLeft() {
        return config.get(Button.LEFT);
    }

    public PerButtonConfig getRight() {
        return config.get(Button.RIGHT);
    }
}
