package utils;

import java.io.File;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import models.Notification;
import models.NotificationConnection;

import play.Logger;

/**
 * Utility class for performing process related functions such as command line processing.
 * Based on http://stackoverflow.com/a/809976
 */
public class Pipeline2Engine {
	
	public static enum State {
		STOPPED, STARTING, RUNNING
	};
	
	private static State state = null;
	
	public static final String SLASH = System.getProperty("file.separator");
	public static final String DP2_START = "/".equals(SLASH) ? "./pipeline2" : "cmd /c start /B pipeline2.bat";
	public static File cwd = null;
	
	static Worker engine = null;
	
	private Pipeline2Engine(){} // don't instantiate
	
	public static void start() {
		if (engine != null)
			halt();
		engine = executeCommandWithWorker(DP2_START, new File(cwd,"bin"));
		state = State.STARTING;
	}
	
	public static void halt() {
		if (engine != null)
			engine.process.destroy();
		engine = null;
		state = State.STOPPED;
	}
	
	public static State getState() {
		return state;
	}
	
	public static void setState(State state) {
		Pipeline2Engine.state = state;
		NotificationConnection.pushAll(new Notification("heartbeat", Pipeline2Engine.state.toString()));
		Logger.debug("Pipeline 2 engine state: "+Pipeline2Engine.state);
	}
	
	/**
     * Thread class to be used as a worker
     */
    private static class Worker extends Thread {
        private final Process process;
        private Integer exitValue;

        Worker(final Process process) {
            this.process = process;
        }

        public Integer getExitValue() {
            return exitValue;
        }

        @Override
        public void run() {
            try {
                exitValue = process.waitFor();
                
            } catch (InterruptedException ignore) {
                return;
            }
        }
    }
    
    /**
     * Executes a command.
     * 
     * @param command
     * @param cwd
     * @param timeOut
     * @return
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    private static Worker executeCommandWithWorker(final String command, final File cwd) {
    	
    	Logger.info("Running command '"+command+"' from directory '"+cwd.getAbsolutePath()+"'");
    	
        // create the process which will run the command
        Runtime runtime = Runtime.getRuntime();
        Process process;
		try {
			process = runtime.exec(command, null, cwd.getAbsoluteFile());
		} catch (IOException e) {
            String errorMessage = "The process for the command [" + command + "] could not be created due to an IO error.";
            Logger.error(errorMessage, e);
            return null;
		}
        
        // consume and display the error and output streams
        StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), "INFO");
        StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR");
        outputGobbler.start();
        errorGobbler.start();

        // create and start a Worker thread which this thread will join for the timeout period 
        Worker worker = new Worker(process);
        worker.start();
        return worker;
        
//        try {
//            worker.join(timeOut);
//            Integer exitValue = worker.getExitValue();
//            if (exitValue != null)
//            {
//                // the worker thread completed within the timeout period
//                return exitValue;
//            }
//
//            // if we get this far then we never got an exit value from the worker thread as a result of a timeout 
//            String errorMessage = "The command [" + command + "] timed out.";
//            Logger.error(errorMessage);
//            return 1;
//        }
//        catch (InterruptedException e) {
//            worker.interrupt();
//            Thread.currentThread().interrupt();
//            String errorMessage = "The command [" + command + "] did not complete due to an unexpected interruption.";
//            Logger.error(errorMessage, e);
//            return 1;
//        }
    }

    /**
     * Utility thread class which consumes and displays stream input.
     * 
     * Original code taken from http://www.javaworld.com/javaworld/jw-12-2000/jw-1229-traps.html?page=4
     */
    static class StreamGobbler extends Thread {
        private InputStream inputStream;
        private String streamType;

        /**
         * Constructor.
         * 
         * @param inputStream the InputStream to be consumed
         * @param streamType the stream type (should be OUTPUT or ERROR)
         */
        StreamGobbler(final InputStream inputStream, final String streamType) {
            this.inputStream = inputStream;
            this.streamType = streamType;
        }

        /**
         * Consumes the output from the input stream and displays the lines consumed if configured to do so.
         */
        @Override
        public void run() {
            try {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line = null;
                while ((line = bufferedReader.readLine()) != null) {
                	if ("INFO".equals(streamType)) Logger.info(line);
                	else if ("ERROR".equals(streamType)) Logger.error(line);
                	else if ("DEBUG".equals(streamType)) Logger.debug(line);
                	else if ("TRACE".equals(streamType)) Logger.trace(line);
                	else if ("WARN".equals(streamType)) Logger.warn(line);
                }
                
            } catch (IOException ex) {
                Logger.error("Failed to successfully consume and display the input stream of type " + streamType + ".", ex);
                ex.printStackTrace();
            }
        }
    }
}