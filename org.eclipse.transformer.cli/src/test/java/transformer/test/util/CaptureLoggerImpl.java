/********************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: (EPL-2.0 OR Apache-2.0)
 ********************************************************************************/

package transformer.test.util;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import java.util.logging.Logger;

public class CaptureLoggerImpl extends Logger {
	public static final boolean CAPTURE_INACTIVE = true;

	public CaptureLoggerImpl(String baseLoggerName, boolean captureInactive) {
		this(Logger.getLogger(baseLoggerName), captureInactive);
	}

	public CaptureLoggerImpl(String baseLoggerName) {
		this(Logger.getLogger(baseLoggerName));
	}

	public CaptureLoggerImpl(Logger baseLogger) {
		this(baseLogger, !CAPTURE_INACTIVE);
	}

	public CaptureLoggerImpl(Logger baseLogger, boolean captureInactive) {
                super(baseLogger.getName(), null);
		this.baseLogger = baseLogger;

		this.captureInactive = captureInactive;
		this.capturedEvents = new ArrayList<>();
	}

	//

	private final Logger baseLogger;

	public Logger getBaseLogger() {
		return baseLogger;
	}

	@Override
	public String getName() {
		return getBaseLogger().getName();
	}

	//

	private final boolean captureInactive;

	public boolean getCaptureInactive() {
		return captureInactive;
	}

	public boolean capture(Level level) {
		return (getCaptureInactive() || isLoggable(level));
	}

	//

	public LogEvent capture(Level level, String message, Object... rawParms) {
		return capture(level, null, message, rawParms);
	}

	public LogEvent capture(Level level, Throwable th, String message, Object... rawParms) {

		if (!capture(level)) {
			return null;
		}

		LogEvent logEvent = new LogEvent(level, th, message, rawParms);
		addEvent(logEvent);
		return logEvent;
	}

	private static final String[] EMPTY_STRINGS = new String[0];

	public static class LogEvent {
		public final Level		level;

		public final String		threadName;
		public final long		timeStamp;

		public final String		message;
		public final String[]	parms;
		public final String		thrownMessage;

		private String			printString;

		private boolean append(Object object, boolean isFirst, StringBuilder builder) {
			if (object == null) {
				return false;
			}
			if (!isFirst) {
				builder.append(" ");
			}
			builder.append("[ ");
			builder.append(object);
			builder.append(" ]");
			return true;
		}

		@Override
		public String toString() {
			if (printString == null) {
				boolean isFirst = true;

				StringBuilder builder = new StringBuilder();

				boolean didAdd = append(level, isFirst, builder);
				if (didAdd) {
					isFirst = false;
				}
				didAdd = append(threadName, isFirst, builder);
				if (didAdd) {
					isFirst = false;
				}
				append(Long.valueOf(timeStamp), isFirst, builder);
				isFirst = false;

				append(message, isFirst, builder);
				if (parms != null) {
					for (String parm : parms) {
						append(parm, isFirst, builder);
					}
				}

				append(thrownMessage, isFirst, builder);

				printString = builder.toString();
			}

			return printString;
		}

		public LogEvent(Level level, Throwable th, String message, Object... rawParms) {
			this.level = level;

			this.threadName = Thread.currentThread()
				.getName();
			this.timeStamp = System.nanoTime();

			this.message = message;

			if ((rawParms == null) || (rawParms.length == 0)) {
				parms = EMPTY_STRINGS;
			} else {
				String[] useParms = new String[rawParms.length];
				for (int parmNo = 0; parmNo < rawParms.length; parmNo++) {
					Object nextParm = rawParms[parmNo];
					useParms[parmNo] = ((nextParm == null) ? null : nextParm.toString());
				}
				this.parms = useParms;
			}

			this.thrownMessage = ((th == null) ? null : th.getMessage());
		}
	}

	private final List<LogEvent> capturedEvents;

	public List<LogEvent> getCapturedEvents() {
		return capturedEvents;
	}

	protected void addEvent(LogEvent logEvent) {
		capturedEvents.add(logEvent);
	}

	public List<LogEvent> consumeCapturedEvents() {
		List<LogEvent> events = new ArrayList<>(capturedEvents);
		capturedEvents.clear();
		return events;
	}

	public int getCaptureEventCount() {
		return getCapturedEvents().size();
	}

	public LogEvent getCapturedEvent(int eventNo) {
		return getCapturedEvents().get(eventNo);
	}

	@Override
	public boolean isLoggable(Level level) {
		return getBaseLogger().isLoggable(level);
	}

        @Override
        public void log(Level level, String format, Object arg) {
            capture(level, format, arg);
            getBaseLogger().log(level, format, arg);
        }

        @Override
        public void config(String msg) {
            capture(Level.CONFIG, msg);
            getBaseLogger().config(msg);
        }

        @Override
        public void fine(String msg) {
            capture(Level.FINE, msg);
            getBaseLogger().fine(msg);
        }

        @Override
        public void finer(String msg) {
            capture(Level.FINER, msg);
            getBaseLogger().finer(msg);
        }

        @Override
        public void finest(String msg) {
            capture(Level.FINEST, msg);
            getBaseLogger().finest(msg);
        }

        @Override
        public void info(String msg) {
            capture(Level.INFO, msg);
            getBaseLogger().info(msg);
        }

        @Override
        public void severe(String msg) {
            capture(Level.SEVERE, msg);
            getBaseLogger().severe(msg);
        }

        @Override
        public void warning(String msg) {
            capture(Level.WARNING, msg);
            getBaseLogger().warning(msg);
        }

}
