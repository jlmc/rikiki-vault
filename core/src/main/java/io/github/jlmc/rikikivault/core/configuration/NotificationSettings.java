package io.github.jlmc.rikikivault.core.configuration;

public record NotificationSettings(boolean autoDismiss, int autoDismissSeconds, NotificationPosition position) {

    public static NotificationSettings empty() {
        return new NotificationSettings(true, 5, NotificationPosition.BOTTOM_RIGHT);
    }
}
