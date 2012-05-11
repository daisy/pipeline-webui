package controllers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.management.RuntimeErrorException;

import models.Job;
import models.Setting;
import models.Upload;
import models.User;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.w3c.dom.Node;

import pipeline2.Pipeline2WS;
import pipeline2.Pipeline2WSResponse;
import play.Logger;
import play.db.ebean.Transactional;
import play.libs.XPath;
import play.mvc.*;
import scala.actors.threadpool.Arrays;
import utils.Pair;

public class Jobs extends Controller {
	
	// Each key is a job ID, each list is a list of web sockets for the corresponding job
	public static Map<String,List<WebSocket<String>>> statusUpdates = Collections.synchronizedMap(new HashMap<String,List<WebSocket<String>>>());
	public static Map<String,List<WebSocket<String>>> messageUpdates = Collections.synchronizedMap(new HashMap<String,List<WebSocket<String>>>());
	
	public static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	public static Result getJobs() {
		// TODO: hide when running in free-for-all mode in a DAISY/WIPO-envoronment, but not NLB/SBS environment
		// TODO: show only jobs for the current user when logged in as normal user; admins can see all jobs, as well as a "created by"-column
		
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Pipeline2WSResponse jobs = pipeline2.Jobs.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"));
		
		if (jobs.status != 200) {
			return Application.error(jobs.status, jobs.statusName, jobs.statusDescription, "");
		}
		
		List<Job> jobList = new ArrayList<Job>();

		List<Node> jobNodes = XPath.selectNodes("//d:job", jobs.asXml(), Pipeline2WS.ns);
		for (Node jobNode : jobNodes) {
			
			Job job = Job.findById(XPath.selectText("@id", jobNode, Pipeline2WS.ns));
			if (job == null) {
				Logger.error("No job with id "+XPath.selectText("@id", jobNode, Pipeline2WS.ns)+" was found.");
			} else {
				job.href = XPath.selectText("@href", jobNode, Pipeline2WS.ns);
				job.status = XPath.selectText("@status", jobNode, Pipeline2WS.ns);
				if (user.admin || user.id == job.user)
					jobList.add(job);
			}
		}
		
		Collections.sort(jobList);
		Collections.reverse(jobList);
		if (user.admin)
			flash("showOwner", "true");

		return ok(views.html.Jobs.getJobs.render(jobList));
	}

	public static Result getJob(String id) {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Pipeline2WSResponse job = pipeline2.Jobs.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id);
		
		if (job.status != 200 && job.status != 201) {
			return Application.error(job.status, job.statusName, job.statusDescription, "");
		}
		
		String href = XPath.selectText("/d:job/@href", job.asXml(), Pipeline2WS.ns);
		String jobId = XPath.selectText("/d:job/@id", job.asXml(), Pipeline2WS.ns);
		String status = XPath.selectText("/d:job/@status", job.asXml(), Pipeline2WS.ns);
		String script = XPath.selectText("/d:job/d:script/@script", job.asXml(), Pipeline2WS.ns);
		String scriptId = XPath.selectText("/d:job/d:script/@id", job.asXml(), Pipeline2WS.ns);
		String scriptHref = XPath.selectText("/d:job/d:script/@href", job.asXml(), Pipeline2WS.ns);
		String nicename = XPath.selectText("/d:job/d:script/d:nicename", job.asXml(), Pipeline2WS.ns);
		String description = XPath.selectText("/d:job/d:script/d:description", job.asXml(), Pipeline2WS.ns);
		String log = XPath.selectText("/d:job/d:log/@href", job.asXml(), Pipeline2WS.ns);
		String result = XPath.selectText("/d:job/d:result/@href", job.asXml(), Pipeline2WS.ns);

		List<List<String>> messages = new ArrayList<List<String>>();

		List<Node> messageNodes = XPath.selectNodes("/d:job/d:messages/d:message", job.asXml(), Pipeline2WS.ns);

		for (Node messageNode : messageNodes) {
			List<String> row = new ArrayList<String>();
			row.add(XPath.selectText("@level", messageNode, Pipeline2WS.ns));
			row.add(XPath.selectText("@sequence", messageNode, Pipeline2WS.ns));
			row.add(XPath.selectText(".", messageNode, Pipeline2WS.ns));
			messages.add(row);
		}
		
		return ok(views.html.Jobs.getJob.render(href, jobId, nicename, description, status, scriptHref, scriptId, script, log, result, messages));
	}
	
	public static Result getResult(String id) {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Pipeline2WSResponse result = pipeline2.Jobs.getResult(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id);
		
		response().setHeader("Content-Disposition", "attachment; filename=\"result-"+id+".zip\"");
		response().setContentType("application/zip");
		
		return ok(result.asStream());
	}
	
	public static Result getLog(final String id) {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Pipeline2WSResponse jobLog = pipeline2.Jobs.getLog(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id);
		InputStream responseStream = jobLog.asStream();
		String responseText = "";

		if (responseStream != null) {
            Writer writer = new StringWriter();
            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(new InputStreamReader(responseStream, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
            	try {
					responseStream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
            responseText = writer.toString();
        } else {        
        	responseText = "";
        }
		
		if (jobLog.status != 200 && jobLog.status != 201 && jobLog.status != 204) {
			return Application.error(jobLog.status, jobLog.statusName, jobLog.statusDescription, responseText);
		}
		
		if (jobLog.status == 204) {
			return ok(views.html.Jobs.emptyLog.render(id));
			
		} else {
			String[] lines = responseText.split("\n");
			return ok(views.html.Jobs.getLog.render(id, Arrays.asList(lines)));
		}
	}

	@Transactional
	public static Result postJob() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Logger.debug("------------------------------ Posting job... ------------------------------");
		Map<String, String[]> params = request().body().asFormUrlEncoded();
		if (params == null) {
			return internalServerError("Internal server error: could not read form data.");
		}

		// ---------- Read request parameters ----------
		List<models.Upload> uploads = new ArrayList<models.Upload>();
		Map<String,List<String>> inputs = new HashMap<String,List<String>>();
		Map<String,String> options = new HashMap<String,String>();
		String id = params.get("id") != null && params.get("id").length > 0 ? params.get("id")[0] : null;
		String href = params.get("href") != null && params.get("href").length > 0 ? params.get("href")[0] : null;
		File contextZipFile = null;

		for (String param : params.keySet()) {
			if (param.startsWith("option-")) {
				String value = params.get(param)[0];
				param = param.substring(7);
				options.put(param, value);
				Logger.debug("Option: "+param+" = "+value);
			}
		}

		// ---------- Read JSON object provided in the "files" parameter ----------
		Logger.debug("params.get(\"files\")[0]: "+params.get("files")[0]);
		if (params.get("files") != null && params.get("files").length > 0 && !"".equals(params.get("files")[0])) {
			JsonNode files = null;
			try {
				files = new ObjectMapper().readTree(new JsonFactory().createJsonParser(params.get("files")[0]));
			} catch (JsonParseException e) {
				Logger.error("Unable to parse JSON value of 'files' parameter.", e);
				throw new RuntimeErrorException(new Error(e), "Unable to parse JSON value of 'files' parameter.");
			} catch (JsonProcessingException e) {
				Logger.error("Unable to process JSON value of 'files' parameter.", e);
				throw new RuntimeErrorException(new Error(e), "Unable to process JSON value of 'files' parameter.");
			} catch (IOException e) {
				Logger.error("Unable to get the JSON value of 'files' parameter.", e);
				throw new RuntimeErrorException(new Error(e), "Unable to get the JSON value of 'files' parameter.");
			}
			JsonNode jsonUploads = files.has("uploads") ? files.get("uploads") : null; // Array
			JsonNode jsonInputs = files.has("inputs") ? files.get("inputs") : null; // Object

			// ---------- List of all uploads --------
			if (jsonUploads != null && jsonUploads.isArray()) {
				for (JsonNode jsonUploadId : jsonUploads) {
					Logger.debug("upload id: "+jsonUploadId.asLong());
					Long uploadId = jsonUploadId.asLong();
					Upload upload = Upload.find.byId(uploadId);
					if (upload == null)
						return badRequest("Could not find the upload with id "+uploadId+".");
					uploads.add(upload);
				}
			}

			// ---------- List of which files goes on which input ports ----------
			if (jsonInputs != null && jsonInputs.isObject()) {
				Iterator<Entry<String, JsonNode>> jsonInputEntries = jsonInputs.getFields();
				while (jsonInputEntries.hasNext()) {
					Entry<String, JsonNode> jsonInput = jsonInputEntries.next();
					String port = jsonInput.getKey();
					inputs.put(port, new ArrayList<String>());
					JsonNode jsonPortSequence = jsonInput.getValue();
					if (jsonPortSequence.isArray()) {
						for (JsonNode jsonPortElement : jsonPortSequence) {
							Logger.debug("'"+jsonPortElement.getTextValue()+"' goes on port '"+port+"'");
							inputs.get(port).add(jsonPortElement.getTextValue());
						}
					}
				}
			}

			// ---------- See if there's an existing ZIP we can use as the context ----------
//			models.Upload contextZipUpload = null;
//			Long biggestZipSoFar = 0L;
//
//			for (Upload upload : uploads) {
//				if (upload.isZip()) {
//					File file = upload.getFile();
//					if (file.length() > biggestZipSoFar) {
//						biggestZipSoFar = file.length();
//						contextZipUpload = upload;
//					}
//				}
//			}
//			
//			if (contextZipUpload == null)
//				Logger.debug("There's no ZIP files available to use as context");
//			else
//				Logger.debug("The ZIP file '"+contextZipUpload.getFile().getAbsolutePath()+"' is the biggest ZIP file available and will be used as context");

			// ---------- Create a temporary directory ("the context") ----------
			File contextDir = null;
			try {
				contextDir = File.createTempFile("jobContext", "");

				if (!(contextDir.delete())) {
					Logger.error("Could not delete contextDir file: " + contextDir.getAbsolutePath());

				} if (!(contextDir.mkdir())) {
					Logger.error("Could not create contextDir directory: " + contextDir.getAbsolutePath());
				}

			} catch (IOException e) {
				Logger.error("Could not create temporary file (context directory): "+e.getMessage(), e);
				return internalServerError("Could not create temporary file (context directory)");
			}
			
			Logger.debug("Created context directory: "+contextDir.getAbsolutePath());

			// ---------- Copy or unzip all uploads to a common directory ----------
			Logger.info("number of uploads: "+uploads.size());
			for (models.Upload upload : uploads) {
				if (upload.isZip()) {
//					if (contextZipUpload != null && contextZipUpload.id == upload.id) {
//						Logger.info("not unzipping context zip ("+upload.getFile().getAbsolutePath()+")");
//						continue;
//					}
					Logger.info("unzipping "+upload.getFile()+" to contextDir");
					try {
						utils.Files.unzip(upload.getFile(), contextDir);
					} catch (IOException e) {
						Logger.error("Unable to unzip files into context directory.", e);
						throw new RuntimeErrorException(new Error(e), "Unable to unzip files into context directory.");
					}
				} else {
					File from = upload.getFile();
					File to = new File(contextDir, from.getName());
					Logger.info("copying "+from+" to "+to);
					try {
						utils.Files.copy(from, to); // We could do file.renameTo here to move it instead of making a copy, but copying makes it easier in case we need to re-run a job
					} catch (IOException e) {
						Logger.error("Unable to copy files to context directory.", e);
						throw new RuntimeErrorException(new Error(e), "Unable to copy files to context directory.");
					}
				}
			}

//			if (contextZipUpload == null) {
				if (contextDir.list().length == 0) {
					contextZipFile = null;
				} else {
					try {
						contextZipFile = File.createTempFile("jobContext", ".zip");
						Logger.debug("Created job context zip file: "+contextZipFile);
					} catch (IOException e) {
						Logger.error("Unable to create temporary job context ZIP file.", e);
						throw new RuntimeErrorException(new Error(e), "Unable to create temporary job context ZIP file.");
					}
					try {
						utils.Files.zip(contextDir, contextZipFile);
					} catch (IOException e) {
						Logger.error("Unable to zip context directory.", e);
						throw new RuntimeErrorException(new Error(e), "Unable to zip context directory.");
					}
				}
//			} else {
//				contextZipFile = contextZipUpload.getFile();
//				try {
//					Logger.debug("adding contents of '"+contextDir+"' to the ZIP '"+contextZipFile+"'");
//					utils.Files.addDirectoryContentsToZip(contextZipFile, contextDir);
//				} catch (IOException e) {
//					Logger.error("Unable to add files to existing context ZIP file.", e);
//					throw new RuntimeErrorException(new Error(e), "Unable to add files to existing context ZIP file.");
//				}
//			}
		}

		if (contextZipFile == null) {
			Logger.debug("No files in context, submitting job without context ZIP file");
			Pipeline2WSResponse job = pipeline2.Jobs.post(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), href, options, inputs);
			
			if (job.status != 200 && job.status != 201) {
				return Application.error(job.status, job.statusName, job.statusDescription, "");
			}

			String jobId = XPath.selectText("/*/@id", job.asXml());
			Job webUiJob = new Job(jobId, user);
			webUiJob.save();
			
			return redirect(controllers.routes.Jobs.getJob(jobId));

		} else {
			Logger.debug("Context ZIP file is present, submitting job with context ZIP file");
			Pipeline2WSResponse job = pipeline2.Jobs.post(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), href, options, inputs, contextZipFile);
			
			if (job.status != 200 && job.status != 201) {
				return Application.error(job.status, job.statusName, job.statusDescription, "");
			}
			
			String jobId = XPath.selectText("/*/@id", job.asXml(), Pipeline2WS.ns);
			Job webUiJob = new Job(jobId, user);
			webUiJob.save();
			
			return redirect(controllers.routes.Jobs.getJob(jobId));
		}
	}
	
	/*
	public static WebSocket<String> getStatusUpdates(final String id) {
		return new WebSocket<String>() {
			public void onReady(WebSocket.In<String> in, WebSocket.Out<String> out) {
				final WebSocket<String> self = this;
				in.onMessage(new Callback<String>() {
					public void invoke(String event) {
						Logger.debug("Received event on WebSocket: "+event);  
					} 
				});
				in.onClose(new Callback0() {
					public void invoke() {
						synchronized (statusUpdates) {
							List<WebSocket<String>> sockets = statusUpdates.get(id);
							if (sockets != null)
								sockets.remove(self);
						}
						Logger.info("Disconnected from WebSocket");
					}
				});
				
				out.write("Hello!");
			}
		};
	}
	
	public static WebSocket<String> getMessageUpdates(final String id) {
		return new WebSocket<String>() {
			public void onReady(WebSocket.In<String> in, WebSocket.Out<String> out) {
				final WebSocket<String> self = this;
				in.onMessage(new Callback<String>() {
					public void invoke(String event) {
						Logger.debug("Received event on WebSocket: "+event);  
					} 
				});
				in.onClose(new Callback0() {
					public void invoke() {
						synchronized (statusUpdates) {
							List<WebSocket<String>> sockets = statusUpdates.get(id);
							if (sockets != null)
								sockets.remove(self);
						}
						Logger.info("Disconnected from WebSocket");
					}
				});
				
				out.write("Hello!");
			}
		};
	}
	*/
	
}
