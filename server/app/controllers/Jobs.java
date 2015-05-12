package controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import models.Job;
import models.Notification;
import models.NotificationConnection;
import models.Setting;
import models.User;
import models.UserSetting;

import org.daisy.pipeline.client.Pipeline2Exception;
import org.daisy.pipeline.client.Pipeline2Logger;
import org.daisy.pipeline.client.filestorage.JobStorage;
import org.daisy.pipeline.client.models.Argument;
import org.daisy.pipeline.client.models.Script;
import org.daisy.pipeline.client.models.Job.Status;
import org.daisy.pipeline.client.utils.Files;

import play.Logger;
import play.libs.Akka;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import scala.concurrent.duration.Duration;
//import scala.actors.threadpool.Arrays;
import utils.ContentType;
import utils.FileInfo;
import utils.XML;

import com.fasterxml.jackson.databind.JsonNode;

public class Jobs extends Controller {

	public static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	public static Result newJob() {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());

		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		Job newJob = new Job(user);
		newJob.save();
		Logger.debug("created new job: '"+newJob.id+"'");
		
		return redirect(routes.Jobs.getJob(newJob.id));
	}
	
	public static Result restart(Long jobId) {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		Logger.debug("restart("+jobId+")");
		
		Job webuiJob = Job.findById(jobId);
		if (webuiJob == null) {
			Logger.debug("Job #"+jobId+" was not found.");
			return notFound("Sorry; something seems to have gone wrong. The job was not found.");
		}
		if (!(	user.admin
			||	webuiJob.user.equals(user.id)
			||	webuiJob.user < 0 && user.id < 0 && "true".equals(Setting.get("users.guest.shareJobs"))
				)) {
			return forbidden("You are not allowed to restart this job.");
		}
		
		webuiJob.cancelPushNotifications();
		
		if (webuiJob.engineId != null) {
			Logger.info("deleting old job: "+webuiJob.engineId);
			Application.ws.deleteJob(webuiJob.engineId);
			webuiJob.engineId = null;
		}
		
		webuiJob.status = "NEW";
		webuiJob.notifiedComplete = false;
		org.daisy.pipeline.client.models.Job clientlibJob = webuiJob.asJob();
		clientlibJob.setStatus(org.daisy.pipeline.client.models.Job.Status.IDLE);
		webuiJob.status = "IDLE";
		webuiJob.started = null;
		webuiJob.finished = null;
		webuiJob.save();
		
		Logger.info("------------------------------ Posting job... ------------------------------");
		Logger.debug(XML.toString(clientlibJob.toJobRequestXml(true)));
		clientlibJob = Application.ws.postJob(clientlibJob);
		if (clientlibJob == null) {
			Logger.error("An error occured when trying to post job");
			return internalServerError("An error occured when trying to post job");
		}
		webuiJob.setJob(clientlibJob);
		webuiJob.save();
		
		NotificationConnection.pushJobNotification(webuiJob.user, new Notification("job-status-"+webuiJob.id, org.daisy.pipeline.client.models.Job.Status.IDLE));
		webuiJob.pushNotifications();
		
		User.flashBrowserId(user);
		Logger.debug("return redirect(controllers.routes.Jobs.getJob("+webuiJob.id+"));");
		return redirect(controllers.routes.Jobs.getJob(webuiJob.id));
	}
	
	public static Result getScript(Long jobId, String scriptId) {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		if ("false".equals(UserSetting.get(-2L, "scriptEnabled-"+scriptId))) {
			return forbidden();
		}
		
		Script script = Scripts.get(scriptId);
		
		if (script == null) {
			Logger.error("An error occured while trying to read the script with id '"+scriptId+"' from the engine.");
			return internalServerError("An error occured while trying to read the script with id '"+scriptId+"' from the engine.");
		}

		/* List of mime types that are supported by more than one file argument.
		 * The Web UI cannot automatically assign files of these media types to a
		 * file argument since there are multiple possible file arguments/widgets. */
		List<String> mediaTypeBlacklist = new ArrayList<String>();
		{
			Map<String,Integer> mediaTypeOccurences = new HashMap<String,Integer>();
			for (Argument arg : script.getInputs()) {
				for (String mediaType : arg.getMediaTypes()) {
					if (mediaTypeOccurences.containsKey(mediaType)) {
						mediaTypeOccurences.put(mediaType, mediaTypeOccurences.get(mediaType)+1);
					} else {
						mediaTypeOccurences.put(mediaType, 1);
					}
				}
			}
			for (String mediaType : mediaTypeOccurences.keySet()) {
				if (mediaTypeOccurences.get(mediaType) > 1)
					mediaTypeBlacklist.add(mediaType);
			}
		}

		boolean uploadFiles = false;
		boolean hideAdvancedOptions = "true".equals(Setting.get("jobs.hideAdvancedOptions"));
		boolean hasAdvancedOptions = false;
		for (Argument arg : script.getInputs()) {
			if (arg.getRequired() != Boolean.TRUE) {
				hasAdvancedOptions = true;
			}
			if ("anyFileURI".equals(arg.getType()) || "anyURI".equals(arg.getType()) || "anyDirURI".equals(arg.getType())) {
				uploadFiles = true;
			}
		}

		User.flashBrowserId(user);
		return ok(views.html.Jobs.getScript.render(script, script.getId().replaceAll(":", "\\x3A"), uploadFiles, hasAdvancedOptions, hideAdvancedOptions, mediaTypeBlacklist, jobId));
	}
	
	public static Result getJobs() {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null || (user.id < 0 && !"true".equals(Setting.get("users.guest.shareJobs"))))
			return redirect(routes.Login.login());
		
		if (user.admin)
			flash("showOwner", "true");
		flash("userid", user.id+"");
		
		List<Job> jobList;
		if (user.admin) {
			jobList = Job.find.all();
			
		} else if (user.id >= 0) {
			jobList = Job.find.where().eq("user", user.id).findList();
			
		} else if (user.id < 0 && "true".equals(Setting.get("users.guest.shareJobs"))) {
			jobList = Job.find.where().lt("user", 0).findList();
			
		} else {
			jobList = new ArrayList<Job>();
		}
		
		Collections.sort(jobList);
		Collections.reverse(jobList);
		
		User.flashBrowserId(user);
		return ok(views.html.Jobs.getJobs.render());
	}
	
	public static Result getJobsJson() {
		if (FirstUse.isFirstUse())
			return unauthorized("unauthorized");
		
		User user = User.authenticate(request(), session());
		if (user == null || (user.id < 0 && !"true".equals(Setting.get("users.guest.shareJobs"))))
			return unauthorized("unauthorized");
		
		List<Job> jobList;
		if (user.admin) {
			jobList = Job.find.all();
			
		} else if (user.id >= 0) {
			jobList = Job.find.where().eq("user", user.id).findList();
			
		} else if (user.id < 0 && "true".equals(Setting.get("users.guest.shareJobs"))) {
			jobList = Job.find.where().lt("user", 0).findList();
			
		} else {
			jobList = new ArrayList<Job>();
		}
		
		Collections.sort(jobList);
		Collections.reverse(jobList);
		
		JsonNode jobsJson = play.libs.Json.toJson(jobList);
		return ok(jobsJson);
	}
	
	public static Result getJob(Long id) {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		Logger.debug("getJob("+id+")");
		
		Job webuiJob = Job.findById(id);
		if (webuiJob == null) {
			Logger.debug("Job #"+id+" was not found.");
			return notFound("Sorry; something seems to have gone wrong. The job was not found.");
		}
		if (!(	user.admin
			||	webuiJob.user.equals(user.id)
			||	webuiJob.user < 0 && user.id < 0 && "true".equals(Setting.get("users.guest.shareJobs"))
				)) {
			return forbidden("You are not allowed to view this job.");
		}
		
		User.flashBrowserId(user);
		if ("NEW".equals(webuiJob.status)) {
			return ok(views.html.Jobs.newJob.render(webuiJob.id));
			
		} else {
			return ok(views.html.Jobs.getJob.render(webuiJob.id));
		}
	}
	
	public static Result getJobJson(Long id) {
		if (FirstUse.isFirstUse())
			return unauthorized("unauthorized");
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return unauthorized("unauthorized");
		
		Job webuiJob = Job.findById(id);
		if (webuiJob == null) {
			Logger.debug("Job #"+id+" was not found.");
			return notFound("Sorry; something seems to have gone wrong. The job was not found.");
		}
		
		if (!(	user.admin
			||	webuiJob.user.equals(user.id)
			||	webuiJob.user < 0 && user.id < 0 && "true".equals(Setting.get("users.guest.shareJobs"))
			)) {
			return forbidden("You are not allowed to view this job.");
		}
		
		org.daisy.pipeline.client.models.Job engineJob = webuiJob.getJobFromEngine(0);
		if (engineJob == null) {
			Logger.error("An error occured while fetching the job from the engine");
			return internalServerError("An error occured while fetching the job from the engine");
		}
		
		Map<String,Object> output = new HashMap<String,Object>();
		output.put("webuiJob", webuiJob);
		output.put("engineJob", engineJob);
		output.put("results", Job.jsonifiableResults(engineJob));
		
		JsonNode jobJson = play.libs.Json.toJson(output);
		return ok(jobJson);
	}
	
	public static Result getAllResults(Long id) {
		return getResult(id, null);
	}
	
	public static Result getResult(Long id, String href) {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		Job webuiJob = Job.findById(id);
		if (webuiJob == null) {
			Logger.debug("Job #"+id+" was not found.");
			return notFound("Sorry; something seems to have gone wrong. The job was not found.");
		}
		if (!(	user.admin
				||	webuiJob.user.equals(user.id)
				||	webuiJob.user < 0 && user.id < 0 && "true".equals(Setting.get("users.guest.shareJobs"))
					))
				return forbidden("You are not allowed to view this job.");
		
//		try {
			Logger.debug("retrieving result from Pipeline 2 engine...");
			
			Logger.debug("href: "+(href==null?"[null]":href));
			
			org.daisy.pipeline.client.models.Job job = webuiJob.getJobFromEngine(0);
			
			if (href != null && href.length() > 0) {
				href = job.getHref() + "/result/" + href;
			}
			
			org.daisy.pipeline.client.models.Result result = job.getResultFromHref(href);
			if (result == null) {
				return badRequest("Could not find result: "+href);
			}
			
			String filename;
			if (result.filename != null) {
				filename = result.filename;
				
			} else if (result.href != null) {
				filename = result.href.replaceFirst("^.*/([^/]*)$", "$1");
				
			} else {
				if ("application/zip".equals(result.mimeType) || result.from != null) {
					filename = id+".zip";
					
				} else {
					filename = id+"";
				}
			}
			response().setHeader("Content-Disposition", "attachment; filename=\""+filename+"\"");
			
			File resultFile = result.asFile();
			if (resultFile == null || !resultFile.exists()) {
				Logger.debug("Result file does not exist on disk; request directly from engine...");
				
				resultFile = Application.ws.getJobResultAsFile(job.getId(), href);
			}
			
			if (resultFile == null) {
				return badRequest("Result not found: "+href);
			}
			
			try {
				String contentType = ContentType.probe(resultFile.getName(), new FileInputStream(resultFile));
				response().setContentType(contentType);
				
				Logger.debug("contentType: "+contentType);
				
			} catch (FileNotFoundException e) {
				/* ignore */
			}
			
			long size = resultFile.length();
			if (size > 0) {
				response().setHeader("Content-Length", size+"");
				Logger.debug("size: "+size);
			} else {
				Logger.debug("content size unknown (size="+size+")");
			}
			
			String parse = request().getQueryString("parse");
			if ("report".equals(parse)) {
				response().setContentType("text/html");
				String report = Files.read(resultFile);
				Pattern regex = Pattern.compile("^.*<body[^>]*>(.*)</body>.*$", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
				Matcher regexMatcher = regex.matcher(report);
				String body = null;
				if (regexMatcher.find()) {
					body = regexMatcher.group(1);
				} else {
					Logger.debug("no body element found in report; returning the entire report");
					body = report;
				}
				final byte[] utf8Bytes = body.getBytes(StandardCharsets.UTF_8);
				response().setHeader(CONTENT_LENGTH, ""+utf8Bytes.length);
				return ok(body);
				
			}
			
			return ok(resultFile);
			
//		} catch (Pipeline2Exception e) {
//			Logger.error(e.getMessage(), e);
//			return Application.error(500, "Sorry, something unexpected occured", "A problem occured while communicating with the Pipeline engine", e.getMessage());
//		}
	}

	public static Result getLog(final Long id) {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		response().setHeader("Content-Disposition", "attachment; filename=\"job-"+id+".log\"");
		
		Job webuiJob = Job.findById(id);
		if (webuiJob == null) {
			Logger.debug("Job #"+id+" was not found.");
			return notFound("Sorry; something seems to have gone wrong. The job was not found.");
		}
		if (!(	user.admin
				||	webuiJob.user.equals(user.id)
				||	webuiJob.user < 0 && user.id < 0 && "true".equals(Setting.get("users.guest.shareJobs"))
					))
				return forbidden("You are not allowed to view this job.");
		
		String jobLog = Application.ws.getJobLog(webuiJob.engineId);
		//jobLog = org.daisy.pipeline.client.Jobs.getLog(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id);
		
		if (jobLog == null) {
			Pipeline2Logger.logger().error("Was not able to read job log for "+id);
			return Application.internalServerError("Unable to retrieve job log.");
		}
		
		if (jobLog.length() == 0) {
			return ok("The log is empty.");
		}
		
		return ok(jobLog);
	}

    public static Result postJob(Long jobId) {
		if (FirstUse.isFirstUse()) {
			return redirect(routes.FirstUse.getFirstUse());
		}

		User user = User.authenticate(request(), session());
		if (user == null) {
			return redirect(routes.Login.login());
		}
		
		Job job = Job.findById(jobId);
		if (job == null) {
			return notFound("The job with ID='"+jobId+"' was not found.");
		}
		
		if (job.user != user.id) {
			return forbidden("You can only run your own jobs.");
		}
		
		Map<String, String[]> params = request().body().asFormUrlEncoded();
		if (params == null) {
			Logger.error("Could not read form data: "+request().body().asText());
			return internalServerError("Could not read form data");
		}

		String scriptId = params.get("id")[0];
		if ("false".equals(UserSetting.get(user.id, "scriptEnabled-"+scriptId))) {
			return forbidden();
		}
		
		Script script = Scripts.get(scriptId);
		if (script == null) {
			Logger.error("An error occured while trying to read the script with id '"+scriptId+"'.");
			return Application.internalServerError("An error occured while trying to read the script with id '"+scriptId+"'.");
		}
		try {
			script = new Script(script.toXml()); // create new instance of script
			
		} catch (Pipeline2Exception e) {
			Logger.error("An error occured while trying to read the script with id '"+scriptId+"'.");
			return Application.internalServerError("An error occured while trying to read the script with id '"+scriptId+"'.");
		}
		
		org.daisy.pipeline.client.models.Job clientlibJob = job.asJob();
		clientlibJob.setScript(script);
		
		for (String paramName : params.keySet()) {
			String[] values = params.get(paramName);
			Argument arg = script.getArgument(paramName);
			if (arg != null) {
				arg.clear();
				if (values.length == 1 && "".equals(values[0])) {
					// don't add value; treat empty strings as unset values
				} else {
					for (String value : values) {
						arg.add(value);
					}
				}
			} else {
				Logger.warn(paramName+" is not a valid argument for the script "+script.getNicename());
			}
		}

		// Parse and validate the submitted form (also create any necessary output directories in case of local mode)
		// TODO: see if clientlib can be used for validation instead
		Scripts.ScriptForm scriptForm = new Scripts.ScriptForm(user.id, script, params);
		scriptForm.validate();
		
		Logger.debug("------------------------------ Posting job... ------------------------------");
		Logger.debug(XML.toString(clientlibJob.toJobRequestXml(true)));
		clientlibJob = Application.ws.postJob(clientlibJob);
		if (clientlibJob == null) {
			Logger.error("An error occured when trying to post job");
			return internalServerError("An error occured when trying to post job");
		}
		job.setJob(clientlibJob);
		job.status = "IDLE";
		job.save();
		
		NotificationConnection.push(job.user, new Notification("job-created-"+job.id, job.created.toString()));
		
		JsonNode jobJson = play.libs.Json.toJson(job);
		Notification jobNotification = new Notification("new-job", jobJson);
		Logger.debug("pushed new-job notification with status=IDLE for job #"+jobId);
		NotificationConnection.pushJobNotification(job.user, jobNotification);
		job.pushNotifications();
		
		if (user.id < 0 && scriptForm.guestEmail != null && scriptForm.guestEmail.length() > 0) {
			String jobUrl = Application.absoluteURL(routes.Jobs.getJob(job.id).absoluteURL(request())+"?guestid="+(models.User.parseUserId(session())!=null?-models.User.parseUserId(session()):""));
			String html = views.html.Account.emailJobCreated.render(jobUrl, job.nicename).body();
			String text = "To view your Pipeline 2 job, go to this web address: " + jobUrl;
			if (Account.sendEmail("Job created: "+job.nicename, html, text, scriptForm.guestEmail, scriptForm.guestEmail))
				flash("success", "An e-mail was sent to "+scriptForm.guestEmail+" with a link to this job.");
			else
				flash("error", "Was unable to send an e-mail with a link to this job.");
		}
		
		Logger.debug("return redirect(controllers.routes.Jobs.getJob("+job.id+"));");
		return redirect(controllers.routes.Jobs.getJob(job.id));
	}
    
    public static Result delete(Long jobId) {
		if (FirstUse.isFirstUse())
			return unauthorized("unauthorized");
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return unauthorized("unauthorized");
		
		Job webuiJob = Job.findById(jobId);
		if (webuiJob == null) {
			Logger.debug("Job #"+jobId+" was not found.");
			return notFound("Sorry; something seems to have gone wrong. The job was not found.");
		}
		
		if (!(	user.admin
			||	webuiJob.user.equals(user.id)
			||	webuiJob.user < 0 && user.id < 0 && "true".equals(Setting.get("users.guest.shareJobs"))
			)) {
			return forbidden("You are not allowed to view this job.");
		}
    	
    	Logger.debug("deleting "+jobId);
		webuiJob.delete();
		return ok();
    }
    
    public static Result upload(Long jobId) {
		if (FirstUse.isFirstUse())
			return forbidden();
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return forbidden();
		
		Job webuiJob = Job.findById(jobId);
		if (webuiJob == null) {
			Logger.debug("Job #"+jobId+" was not found.");
			return notFound("Sorry; something seems to have gone wrong. The job was not found.");
		}
		
        MultipartFormData body = request().body().asMultipartFormData();
        List<FilePart> files = body.getFiles();
        
        List<Map<String,Object>> filesResult = new ArrayList<Map<String,Object>>();
        
        for (FilePart file : files) {
        	Logger.debug(request().method()+" | "+file.getContentType()+" | "+file.getFilename()+" | "+file.getFile().getAbsolutePath());
        	
        	Map<String,Object> fileObject = new HashMap<String,Object>();
        	fileObject.put("name", file.getFilename());
        	fileObject.put("size", file.getFile().length());
        	filesResult.add(fileObject);
        	
        	Akka.system().scheduler().scheduleOnce(
    				Duration.create(0, TimeUnit.SECONDS),
    				new Runnable() {
    					public void run() {
							JobStorage jobStorage = (JobStorage)webuiJob.asJob().getJobStorage();
							File f = file.getFile();
							
							Map<String,Object> result = new HashMap<String,Object>();
							result.put("fileName", file.getFilename());
							result.put("contentType", file.getContentType());
							result.put("total", file.getFile().length());
							
							if (file.getFilename().toLowerCase().endsWith(".zip")) {
								Logger.debug("adding zip file: "+file.getFilename());
								try {
									File tempDir = java.nio.file.Files.createTempFile("pipeline2-webui-upload", null).toFile();
									tempDir.delete();
									tempDir.mkdirs();
									Files.unzip(f, tempDir);
									
									List<Map<String,Object>> jsonFileset = new ArrayList<Map<String,Object>>();
									Map<String, File> files = Files.listFilesRecursively(tempDir, false);
									for (String href : files.keySet()) {
										Map<String,Object> fileResult = new HashMap<String,Object>();
										String contentType = ContentType.probe(href, new FileInputStream(files.get(href)));
										fileResult.put("fileName", href);
										fileResult.put("contentType", contentType);
										fileResult.put("total", files.get(href).length());
										fileResult.put("isXML", contentType != null && (contentType.equals("application/xml") || contentType.equals("text/xml") || contentType.endsWith("+xml")));
										jsonFileset.add(fileResult);
									}
									result.put("fileset", jsonFileset);
									
									Logger.debug("zip file contains "+tempDir.listFiles().length+" files");
									for (File dirFile : tempDir.listFiles()) {
										Logger.debug("top-level entry in zip: "+dirFile.getName());
										jobStorage.addContextFile(dirFile, dirFile.getName());
									}
									
								} catch (IOException e) {
									Logger.error("Failed to unzip uploaded zip file", e);
								}
								
							} else {
								Logger.debug("adding zip file: "+f.getName());
								
								List<Map<String,Object>> jsonFileset = new ArrayList<Map<String,Object>>();
								Map<String,Object> fileResult = new HashMap<String,Object>();
								String contentType;
								try {
									contentType = ContentType.probe(file.getFilename(), new FileInputStream(f));
								} catch (FileNotFoundException e) {
									contentType = "application/octet-stream";
								}
								fileResult.put("fileName", file.getFilename());
								fileResult.put("contentType", contentType);
								fileResult.put("total", f.length());
								fileResult.put("isXML", contentType != null && (contentType.equals("application/xml") || contentType.equals("text/xml") || contentType.endsWith("+xml")));
								jsonFileset.add(fileResult);
								result.put("fileset", jsonFileset);
								
								jobStorage.addContextFile(f, file.getFilename());
							}
				        	jobStorage.save(true); // true = move files instead of copying
				        	
							NotificationConnection.push(user.id, new Notification("uploads", result));
    					}
    				},
    				Akka.system().dispatcher()
    				);
        	
        }
        
        Map<String,List<Map<String,Object>>> result = new HashMap<String,List<Map<String,Object>>>();
        result.put("files", filesResult);
        
		response().setContentType("text/html");
		return ok(play.libs.Json.toJson(result));
		
    }
	
}
