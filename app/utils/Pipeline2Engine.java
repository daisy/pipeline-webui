package utils;

import org.daisy.pipeline.client.Pipeline2WSException;
import org.daisy.pipeline.client.Pipeline2WSResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import models.Notification;
import models.NotificationConnection;
import models.Setting;

import play.Logger;

/**
 * Utility class for performing process related functions such as command line processing.
 * Based on http://stackoverflow.com/a/809976
 */
public class Pipeline2Engine {
	
	public static enum State {
		STOPPED, STARTING, RUNNING, ERROR
	};
	
	private static State state = null;
	
	public static final String SLASH = System.getProperty("file.separator");
	public static final String DP2_START = "/".equals(SLASH) ? "./pipeline2" : "cmd /c start /B pipeline2.bat";
	public static File cwd = null;
	
	public static List<String> errorMessages = new ArrayList<String>();
	public static List<String> errorStacktraces = new ArrayList<String>();
	
	static Worker engine = null;
	
	/** True if we're shutting down. No engine will start while this is true. */
	public static boolean shuttingDown = false;
	
	private Pipeline2Engine(){} // don't instantiate
	
	public static void start() {
		if (engine != null)
			halt();
		if (!shuttingDown) {
			setState(State.STARTING);
			engine = executeCommandWithWorker(DP2_START, new File(cwd,"bin"));
		}
	}
	
	public static void shutdown() {
		shuttingDown = true;
		halt();
		Logger.debug("halted...");
	}
	
	public static void halt() {
		if (engine != null || !state.equals(State.STOPPED)) {
			
			// shut down through the /admin/halt API
			try {
                String tempDir = System.getProperty("java.io.tmpdir");
                if (!tempDir.endsWith(SLASH))
                	tempDir += SLASH;
                File keyFile = new File(tempDir+"dp2key.txt");
                
                String key = null;
                Scanner scanner = new Scanner(new FileInputStream(keyFile));
                try {
                  if (scanner.hasNextLine()){
                    key = scanner.nextLine();
                  }
                } finally{
                  scanner.close();
                }
                
                Logger.debug("Shutdown key: "+key);
				
				if (key == null) {
					Logger.error("Could not read the Pipeline 2 engine key file");
					
				} else {
					Pipeline2WSResponse response = org.daisy.pipeline.client.Admin.halt(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), key);
					if (response.status != 204) {
						Logger.error("Could not shut down the Pipeline 2 engine:");
						Logger.error(response.asText());
						
					} else {
						Logger.error("Successfully shut down the Pipeline 2 engine!");
					}
				}
			} catch (Pipeline2WSException e) {
				Logger.error(e.getLocalizedMessage(), e);
			} catch (FileNotFoundException e) {
				Logger.error("Could not read Pipelne 2 engine key file; "+e.getLocalizedMessage(), e);
			}
			
			// shut down by killing the process
			if (engine != null)
				engine.process.destroy();
		}
		
		engine = null;
		setState(State.STOPPED);
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

        @SuppressWarnings("unused")
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
            
            errorMessages.add(errorMessage);
            errorMessages.add(e.getLocalizedMessage());
            while (errorMessages.size() > 100) errorMessages.remove(0);
            
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            errorStacktraces.add(sw.toString()); // stack trace as a string
            while (errorStacktraces.size() > 100) errorStacktraces.remove(0);
            
    		if (Pipeline2Engine.getState() != Pipeline2Engine.State.RUNNING) {
    			NotificationConnection.pushAll(new Notification("engine.error.message", errorMessage));
    		}
    		
            Logger.error(errorMessage, e);
            
            setState(State.ERROR);
            
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
                	else if ("ERROR".equals(streamType)) Logger.error("|||"+line);
                	else if ("DEBUG".equals(streamType)) Logger.debug(line);
                	else if ("TRACE".equals(streamType)) Logger.trace(line);
                	else if ("WARN".equals(streamType)) Logger.warn(line);
                	
                	if ("ERROR".equals(streamType)) {
                		errorMessages.add(line);
                		while (errorMessages.size() > 100) errorMessages.remove(0);
                		if (Pipeline2Engine.getState() != Pipeline2Engine.State.RUNNING) {
                			NotificationConnection.pushAll(new Notification("engine.error.message", line));
                		}
                	}
                	else if ("TRACE".equals(streamType)) {
                		errorStacktraces.add(line);
                		while (errorStacktraces.size() > 100) errorStacktraces.remove(0);
                	}
                }
                
            } catch (IOException e) {
                Logger.warn("Could not completely consume and display the "+streamType+" input stream");
            }
        }
    }
}