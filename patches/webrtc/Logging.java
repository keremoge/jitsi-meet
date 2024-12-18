package org.webrtc;

import androidx.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Logging {
   private static final Logger fallbackLogger = createFallbackLogger();
   private static volatile boolean loggingEnabled;
   @Nullable
   private static Loggable loggable;
   private static Logging.Severity loggableSeverity;

   private static Logger createFallbackLogger() {
      Logger fallbackLogger = Logger.getLogger("org.webrtc.Logging");
      fallbackLogger.setLevel(Level.ALL);
      return fallbackLogger;
   }

   static void injectLoggable(Loggable injectedLoggable, Logging.Severity severity) {
      if (injectedLoggable != null) {
         loggable = injectedLoggable;
         loggableSeverity = severity;
      }

   }

   static void deleteInjectedLoggable() {
      loggable = null;
   }

   public static void enableLogThreads() {
      nativeEnableLogThreads();
   }

   public static void enableLogTimeStamps() {
      nativeEnableLogTimeStamps();
   }

   /** @deprecated */
   @Deprecated
   public static void enableTracing(String path, EnumSet<Logging.TraceLevel> levels) {
   }

   public static synchronized void enableLogToDebugOutput(Logging.Severity severity) {
      if (loggable != null) {
         throw new IllegalStateException("Logging to native debug output not supported while Loggable is injected. Delete the Loggable before calling this method.");
      } else {
         nativeEnableLogToDebugOutput(severity.ordinal());
         loggingEnabled = true;
      }
   }

   public static void log(Logging.Severity severity, String tag, String message) {
      if (tag != null && message != null) {
         if (loggable != null) {
            if (severity.ordinal() >= loggableSeverity.ordinal()) {
               loggable.onLogMessage(message, severity, tag);
            }
         } else if (loggingEnabled) {
            nativeLog(severity.ordinal(), tag, message);
         } else {
            Level level;
            switch(severity) {
            case LS_ERROR:
               level = Level.SEVERE;
               break;
            case LS_WARNING:
               level = Level.WARNING;
               break;
            case LS_INFO:
               level = Level.INFO;
               break;
            default:
               level = Level.FINE;
            }

            fallbackLogger.log(level, tag + ": " + message);
         }
      } else {
         throw new IllegalArgumentException("Logging tag or message may not be null.");
      }
   }

   public static void d(String tag, String message) {
      log(Logging.Severity.LS_INFO, tag, message);
   }

   public static void e(String tag, String message) {
      log(Logging.Severity.LS_ERROR, tag, message);
   }

   public static void w(String tag, String message) {
      log(Logging.Severity.LS_WARNING, tag, message);
   }

   public static void e(String tag, String message, Throwable e) {
      log(Logging.Severity.LS_ERROR, tag, message);
      log(Logging.Severity.LS_ERROR, tag, e.toString());
      log(Logging.Severity.LS_ERROR, tag, getStackTraceString(e));
   }

   public static void w(String tag, String message, Throwable e) {
      log(Logging.Severity.LS_WARNING, tag, message);
      log(Logging.Severity.LS_WARNING, tag, e.toString());
      log(Logging.Severity.LS_WARNING, tag, getStackTraceString(e));
   }

   public static void v(String tag, String message) {
      log(Logging.Severity.LS_VERBOSE, tag, message);
   }

   private static String getStackTraceString(Throwable e) {
      if (e == null) {
         return "";
      } else {
         StringWriter sw = new StringWriter();
         PrintWriter pw = new PrintWriter(sw);
         e.printStackTrace(pw);
         return sw.toString();
      }
   }

   private static native void nativeEnableLogToDebugOutput(int var0);

   private static native void nativeEnableLogThreads();

   private static native void nativeEnableLogTimeStamps();

   private static native void nativeLog(int var0, String var1, String var2);

   public static enum Severity {
      LS_VERBOSE,
      LS_INFO,
      LS_WARNING,
      LS_ERROR,
      LS_NONE;

      // $FF: synthetic method
      private static Logging.Severity[] $values() {
         return new Logging.Severity[]{LS_VERBOSE, LS_INFO, LS_WARNING, LS_ERROR, LS_NONE};
      }
   }

   /** @deprecated */
   @Deprecated
   public static enum TraceLevel {
      TRACE_NONE(0),
      TRACE_STATEINFO(1),
      TRACE_WARNING(2),
      TRACE_ERROR(4),
      TRACE_CRITICAL(8),
      TRACE_APICALL(16),
      TRACE_DEFAULT(255),
      TRACE_MODULECALL(32),
      TRACE_MEMORY(256),
      TRACE_TIMER(512),
      TRACE_STREAM(1024),
      TRACE_DEBUG(2048),
      TRACE_INFO(4096),
      TRACE_TERSEINFO(8192),
      TRACE_ALL(65535);

      public final int level;

      private TraceLevel(int level) {
         this.level = level;
      }

      // $FF: synthetic method
      private static Logging.TraceLevel[] $values() {
         return new Logging.TraceLevel[]{TRACE_NONE, TRACE_STATEINFO, TRACE_WARNING, TRACE_ERROR, TRACE_CRITICAL, TRACE_APICALL, TRACE_DEFAULT, TRACE_MODULECALL, TRACE_MEMORY, TRACE_TIMER, TRACE_STREAM, TRACE_DEBUG, TRACE_INFO, TRACE_TERSEINFO, TRACE_ALL};
      }
   }
}
