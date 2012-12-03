package com.w20e.socrates.formatting;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.velocity.app.Velocity;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.LogChute;


/**
 * This wrapper is needed to enable velocity logging to java.util.logging
 * system.
 * 
 * @author dokter
 * 
 */
public class VelocityLoggingWrapper implements LogChute {

    /**
     * Initialize this class' logging.
     */
    private static final Logger LOGGER = Logger
            .getLogger(VelocityLoggingWrapper.class.getName());
    
    
    public VelocityLoggingWrapper() {
        try {
            /*
             * register this class as a logger with the Velocity singleton
             * (NOTE: this would not work for the non-singleton method.)
             */
            Velocity.setProperty(Velocity.RUNTIME_LOG_LOGSYSTEM, this);
            Velocity.init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(RuntimeServices arg0) throws Exception {
        // whatever...

    }

    @Override
    public boolean isLevelEnabled(int arg0) {

        return true;
    }

    @Override
    public void log(int level, String message) {

        LOGGER.log(mapIntToLevel(level), message);
    }

    @Override
    public void log(int level, String message, Throwable throwable) {

        LOGGER.log(mapIntToLevel(level), message, throwable);
    }
    
    private Level mapIntToLevel(final int level) {

        if (level == -1) {
            return Level.ALL;
        } else if (level == 0) {
            return Level.FINEST;
        } else if (level == 1) {
            return Level.INFO;
        } else if (level == 2) {
            return Level.WARNING;
        } else {
            return Level.SEVERE;
        }
    }

}
