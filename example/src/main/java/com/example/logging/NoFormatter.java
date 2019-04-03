package com.example.logging;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * @author robert.xue
 * @since 222
 */
public class NoFormatter extends Formatter {

    /* (non-Javadoc)
     * @see java.util.logging.Formatter#format(java.util.logging.LogRecord)
     */
    @Override
    public String format(LogRecord record) {
        return record.getMessage() + '\n';
    }
}
