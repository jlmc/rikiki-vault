package io.github.jlmc.rikikivault.core.configuration;

public record NotificationSettings(boolean autoDismiss, int autoDismissSeconds) {

    public static NotificationSettings empty() {
        return new NotificationSettings(true, 5);
    }
}
