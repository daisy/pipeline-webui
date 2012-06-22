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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.RuntimeErrorException;

import models.Job;
import models.Setting;
import models.Upload;
import models.User;

import org.w3c.dom.Node;

import pipeline2.Pipeline2WS;
import pipeline2.Pipeline2WSResponse;
import pipeline2.models.script.Argument;
import pipeline2.models.script.arguments.*;
import pipeline2.models.Script;
import play.Logger;
import play.db.ebean.Transactional;
import play.libs.XPath;
import play.mvc.*;
import scala.actors.threadpool.Arrays;

public class Jobs extends Controller {

	public static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public static Result getJobs() {
		// TODO: hide when running in free-for-all mode in a DAISY/WIPO-envoronment, but not NLB/SBS environment
		// TODO: show only jobs for the current user when logged in as normal user; admins can see all jobs, as well as a "created by"-column

		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());

		User user = User.authenticate(session("userid"), session("email"), session("password"));
		if (user == null || user.id < 0)
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
		
		if (request().queryString().containsKey("userid") && request().queryString().get("userid").length > 0) {
			Long userId = Long.parseLong(request().queryString().get("userid")[0]);
			if (userId < 0) {
				session("userid", ""+userId);
		    	session("name", models.Setting.get("guest.name"));
		    	session("email", "");
		    	session("password", "");
		    	session("admin", "false");
			}
		}
		
		User user = User.authenticate(session("userid"), session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());

		Pipeline2WSResponse wsJob = pipeline2.Jobs.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id, null);

		if (wsJob.status != 200 && wsJob.status != 201) {
			return Application.error(wsJob.status, wsJob.statusName, wsJob.statusDescription, "");
		}
		
		pipeline2.models.Job job = new pipeline2.models.Job(wsJob.asXml());

		Job webuiJob = Job.findById(job.id);
		if (webuiJob.user != user.id)
			return forbidden();
		
		if (!Job.lastMessageSequence.containsKey(job.id) && job.messages.size() > 0) {
			Collections.sort(job.messages);
			Job.lastMessageSequence.put(job.id, job.messages.get(job.messages.size()-1).sequence);
		}
		if (!Job.lastStatus.containsKey(job.id)) {
			Job.lastStatus.put(job.id, job.status);
		}

		return ok(views.html.Jobs.getJob.render(job, webuiJob.nicename));
	}

	public static Result getResult(String id) {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		if (request().queryString().containsKey("userid") && request().queryString().get("userid").length > 0) {
			Long userId = Long.parseLong(request().queryString().get("userid")[0]);
			if (userId < 0) {
				session("userid", ""+userId);
		    	session("name", models.Setting.get("guest.name"));
		    	session("email", "");
		    	session("password", "");
		    	session("admin", "false");
			}
		}
		
		User user = User.authenticate(session("userid"), session("email"), session("password"));
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
		
		if (request().queryString().containsKey("userid") && request().queryString().get("userid").length > 0) {
			Long userId = Long.parseLong(request().queryString().get("userid")[0]);
			if (userId < 0) {
				session("userid", ""+userId);
		    	session("name", models.Setting.get("guest.name"));
		    	session("email", "");
		    	session("password", "");
		    	session("admin", "false");
			}
		}
		
		User user = User.authenticate(session("userid"), session("email"), session("password"));
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

	//                                                          kind    position  part      name
	private static final Pattern PARAM_NAME = Pattern.compile("^([A-Za-z]+)(\\d*)([A-Za-z]*?)-(.*)$");
	private static final Pattern FILE_REFERENCE = Pattern.compile("^upload(\\d+)-file(\\d+)$");

	@Transactional
	public static Result postJob() {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());

		User user = User.authenticate(session("userid"), session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());

		Logger.debug("------------------------------ Posting job... ------------------------------");

		Map<String, String[]> params = request().body().asFormUrlEncoded();
		if (params == null) {
			return internalServerError("Internal server error: could not read form data.");
		}

		String id = params.get("id")[0];
		String href = Setting.get("dp2ws.endpoint")+"/scripts/"+id; // TODO: will be unneccessary in next update of Web API ?

		// Get all referenced uploads from DB
		Map<Long,Upload> uploads = new HashMap<Long,Upload>();
		for (String uploadId : params.get("uploads")[0].split(",")) {
			Upload upload = Upload.findById(Long.parseLong(uploadId));
			if (upload.user == user.id)
				uploads.put(upload.id, upload);
		}

		// Get a description of the script from Pipeline 2 Web Service
		Pipeline2WSResponse wsScript = pipeline2.Scripts.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id);
		if (wsScript.status != 200) { return Application.error(wsScript.status, wsScript.statusName, wsScript.statusDescription, ""); }
		Script script = new Script(wsScript.asXml());

		// Parse all arguments
		List<Argument> arguments = new ArrayList<Argument>();
		for (String param : params.keySet()) {
			Matcher matcher = PARAM_NAME.matcher(param);
			if (!matcher.find()) {
				Logger.debug("Unable to parse argument parameter: "+param);
			} else {
				String kind = matcher.group(1);
				String name = matcher.group(4);
				Logger.debug(kind+": "+name);

				Argument argument = null;
				for (Argument arg : script.arguments) {
					if (arg.name.equals(name)) {
						argument = arg;
						break;
					}
				}
				if (argument == null) {
					Logger.debug("'"+name+"' is not an argument for the script '"+id+"'; ignoring it");
					continue;
				}

				if ("anyFileURI".equals(argument.xsdType)) {
					if (argument.sequence) { // Multiple files
						ArgFiles argFiles = new ArgFiles(argument);
						for (int i = 0; i < params.get(param).length; i++) {
							matcher = FILE_REFERENCE.matcher(params.get(param)[i]);
							if (!matcher.find()) {
								Logger.debug("Unable to parse file reference: "+params.get(param)[i]);
							} else {
								Long uploadId = Long.parseLong(matcher.group(1));
								Integer fileNr = Integer.parseInt(matcher.group(2));
								argFiles.hrefs.add(uploads.get(uploadId).listFiles().get(fileNr).href);
							}
						}
						arguments.add(argFiles);

					} else { // Single file
						matcher = FILE_REFERENCE.matcher(params.get(param)[0]);
						if (!matcher.find()) {
							Logger.debug("Unable to parse file reference: "+params.get(param)[0]);
						} else {
							Long uploadId = Long.parseLong(matcher.group(1));
							Integer fileNr = Integer.parseInt(matcher.group(2));
							
							if (uploads.containsKey(uploadId)) {
								arguments.add( new ArgFile(argument, uploads.get(uploadId).listFiles().get(fileNr).href) );
								
							} else {
								Logger.warn("No such upload: "+uploadId);
							}
							
						}
					}

				} else if ("boolean".equals(argument.xsdType)) {
					// Boolean
					arguments.add( new ArgBoolean(argument, new Boolean(params.get(param)[0])) );

				} else if ("parameters".equals(argument.xsdType)) {
					// TODO: parameters are not implemented yet

				} else { // Unknown types are treated like strings

					if (argument.sequence) { // Multiple strings
						ArgStrings argStrings = new ArgStrings(argument);
						for (int i = 0; i < params.get(param).length; i++) {
							argStrings.add(params.get(param)[i]);
						}
						arguments.add(argStrings);

					} else { // Single string
						arguments.add( new ArgString(argument, params.get(param)[0]) );
					}

				}
			}
		}

		File contextZipFile = null;

		if (uploads.size() > 0) {

			// ---------- See if there's an existing ZIP we can use as the context ----------
			//	models.Upload contextZipUpload = null;
			//	Long biggestZipSoFar = 0L;
			//	
			//	for (Long uploadId : uploads.keySet()) {
			//		Upload upload = uploads.get(uploadId);
			//		if (upload.isZip()) {
			//			File file = upload.getFile();
			//			if (file.length() > biggestZipSoFar) {
			//				biggestZipSoFar = file.length();
			//				contextZipUpload = upload;
			//			}
			//		}
			//	}
			//	
			//	if (contextZipUpload == null)
			//		Logger.debug("There's no ZIP files available to use as context");
			//	else
			//		Logger.debug("The ZIP file '"+contextZipUpload.getFile().getAbsolutePath()+"' is the biggest ZIP file available and will be used as context");

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
			for (Long uploadId : uploads.keySet()) {
				Upload upload = uploads.get(uploadId);
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

			//	if (contextZipUpload == null) {
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
			//	} else {
			//		contextZipFile = contextZipUpload.getFile();
			//		try {
			//			Logger.debug("adding contents of '"+contextDir+"' to the ZIP '"+contextZipFile+"'");
			//			utils.Files.addDirectoryContentsToZip(contextZipFile, contextDir);
			//		} catch (IOException e) {
			//			Logger.error("Unable to add files to existing context ZIP file.", e);
			//			throw new RuntimeErrorException(new Error(e), "Unable to add files to existing context ZIP file.");
			//		}
			//	}

		}

		if (contextZipFile == null)
			Logger.debug("No files in context, submitting job without context ZIP file");
		else
			Logger.debug("Context ZIP file is present, submitting job with context ZIP file");
		
		
		Pipeline2WSResponse job = pipeline2.Jobs.post(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), href, arguments, contextZipFile);

		if (job.status != 200 && job.status != 201) {
			return Application.error(job.status, job.statusName, job.statusDescription, "");
		}
		String jobId = XPath.selectText("/*/@id", job.asXml(), Pipeline2WS.ns);
		Job webUiJob = new Job(jobId, user);
		webUiJob.nicename = id;
		webUiJob.save();
		
		webUiJob.pushNotifications();

		return redirect(controllers.routes.Jobs.getJob(jobId));
	}

}
