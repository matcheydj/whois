package net.ripe.db.whois.common;

import javax.annotation.concurrent.Immutable;

@Immutable
public final class Message {
    private final Messages.Type type;
    private final String text;
    private final Object[] args;
    private final String value;

    public Message(final Messages.Type type, final String text, final Object... args) {
        this.type = type;
        this.text = text;
        this.args = args;
        this.value = args.length == 0 ? text : String.format(text, args);
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final Message message = (Message) o;
        return type == message.type && value.equals(message.value);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }

    public Messages.Type getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public String getText() {
        return text;
    }

    public Object[] getArgs() {
        return args;
    }
}
