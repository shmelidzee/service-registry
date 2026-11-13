package ru.registry;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

final class ConsoleLogFormatter extends Formatter {

    static void install() {
        for (Handler handler : Logger.getLogger("").getHandlers()) {
            handler.setFormatter(new ConsoleLogFormatter());
        }
    }

    @Override
    public String format(LogRecord record) {
        StringBuilder line = new StringBuilder()
                .append('[')
                .append(record.getLevel().getName())
                .append("] ")
                .append(formatMessage(record));
        if (record.getThrown() != null) {
            line.append(": ").append(record.getThrown());
        }
        return line.append(System.lineSeparator()).toString();
    }
}
