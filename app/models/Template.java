package models;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.daisy.pipeline.client.filestorage.JobStorage;
import org.daisy.pipeline.client.models.Argument;
import org.daisy.pipeline.client.models.Job;
import org.daisy.pipeline.client.models.Script;
import org.daisy.pipeline.client.utils.Files;

import play.Logger;
import scala.reflect.internal.Trees.This;

/**
 * This class is not strictly a "model". Instead, it provides static methods for accessing the templates,
 * and thus is used whenever you otherwise would use a template. (Templates are not stored in the database.)
 */
public class Template implements Comparable<Template> {
	
	public String name;
	public String ownerTemplatesDirname;
	public Long ownerId;
	public Date lastUpdated;
	public Job clientlibJob = null;
	
	public Map<String,Object> asJsonifyableObject(User user, boolean includeArguments) {
		Map<String,Object> result = new HashMap<String,Object>();
		
		result.put("name", name);
		result.put("ownerId", ownerId);
		User owner = ownerId != null ? User.findById(ownerId) : null;
		result.put("ownerName", owner != null ? owner.name : null);
		result.put("dirname", ownerTemplatesDirname);
		result.put("lastUpdated", lastUpdated != null ? lastUpdated.getTime() : 0);
		if (clientlibJob.getScript() != null) {
			Map<String,Object> script = new HashMap<String,Object>();
			script.put("id", clientlibJob.getScript().getId());
			script.put("nicename", clientlibJob.getScript().getNicename());
			script.put("version", clientlibJob.getScript().getVersion());
			script.put("href", clientlibJob.getScript().getHref());
			script.put("description", clientlibJob.getScript().getDescription());
			script.put("homepage", clientlibJob.getScript().getHomepage());
			script.put("inputFilesets", clientlibJob.getScript().getInputFilesets());
			script.put("outputFilesets", clientlibJob.getScript().getOutputFilesets());
			result.put("script", script);
		}
		
		if (includeArguments) {
			// arguments
			Map<String,Object> argsObj = new HashMap<String,Object>();
			for (Argument argument : clientlibJob.getInputs()) {
				Map<String,Object> argObj = new HashMap<String,Object>();
				argObj.put("values", argument.getAsList());
				argObj.put("defined", argument.isDefined());
				argObj.put("desc", argument.isDefined());
				argObj.put("kind", argument.getKind());
				argObj.put("mediaTypes", argument.getMediaTypes());
				argObj.put("nicename", argument.getNicename());
				argObj.put("ordered", argument.getOrdered());
				argObj.put("output", argument.getOutput());
				argObj.put("required", argument.getRequired());
				argObj.put("sequence", argument.getSequence());
				argObj.put("type", argument.getType());
				argsObj.put(argument.getName(), argObj);
			}
			result.put("inputs", argsObj);
			
			// context
			File contextDir = clientlibJob.getJobStorage().getContextDir();
			try {
				Map<String, File> context = Files.listFilesRecursively(contextDir, false);
				result.put("context", context.keySet());
			} catch (IOException e) { /* too bad... */ }
		}
		
		return result;
	}
	
	private Template(String name, String ownerTemplatesDirname, Long ownerId, Job clientlibJob) {
		this.name = name;
		this.ownerTemplatesDirname = ownerTemplatesDirname;
		this.ownerId = ownerId;
		this.clientlibJob = clientlibJob;
	}

	@Override
	public int compareTo(Template other) {
		return (name+ownerId).compareTo(other.name+other.ownerId);
	}

	private static Map<Object, Map<String, Template>> templateCache = new HashMap<Object, Map<String, Template>>(); // Object = Long or String (Long for userId, String for templates not associated with a user)
	private static Date templateCacheLastUpdated;
	
	/** Get the template with the name `templateName`, owned by the user with the user Id `templateOwnerId`, and accessible by the user `user`. */
	public static Template get(User user, Long templateOwnerId, String templateName)							   { return get(user, (Object)templateOwnerId,        templateName, false); }
	
	/** Get the template with the name `templateName`, owned by the user with the user Id `templateOwnerId`, and accessible by the user `user`. Will refresh from disk if `forceUpdate` is true. */
	public static Template get(User user, Long templateOwnerId, String templateName, boolean forceUpdate)		   { return get(user, (Object)templateOwnerId,        templateName, forceUpdate); }
	
	/** Get the template with the name `templateName`, shared by all users through the directory named by `sharedTemplatesDirname`, and accessible by the user `user`. */
	public static Template get(User user, String sharedTemplatesDirname, String templateName) 					   { return get(user, (Object)sharedTemplatesDirname, templateName, false); }
	
	/** Get the template with the name `templateName`, shared by all users through the directory named by `sharedTemplatesDirname`, and accessible by the user `user`. Will refresh from disk if `forceUpdate` is true. */
	public static Template get(User user, String sharedTemplatesDirname, String templateName, boolean forceUpdate) { return get(user, (Object)sharedTemplatesDirname, templateName, forceUpdate); }
	
	private static Template get(User user, Object sharedTemplatesDirnameOrTemplateOwner, String templateName, boolean forceUpdate) {
		
		// determine if we're looking for a template belonging to a user, or a global shared template
		Long templateOwnerId = null;
		String templateDirname = null;
		if (sharedTemplatesDirnameOrTemplateOwner instanceof Long) {
			templateOwnerId = (Long)sharedTemplatesDirnameOrTemplateOwner;
		} else {
			templateDirname = ""+sharedTemplatesDirnameOrTemplateOwner;
			if (templateDirname == "") {
				templateDirname = "shared";
			}
		}
		
		// get a list of all templates that the user is allowed to access
		List<Template> templates = list(user);
		
		for (Template template : templates) {
			
			// check if this is the template we're looking for
			if (!template.name.equals(templateName)) continue;
			if (templateOwnerId != null && template.ownerId != templateOwnerId) continue;
			if (templateDirname != null && !template.ownerTemplatesDirname.equals(templateDirname)) continue;
			
			// refresh from disk if needed
			int updateFrequency = templateOwnerId == null ? 5 : 60;
			if (forceUpdate || template.clientlibJob == null || template.lastUpdated == null || template.lastUpdated.before(new Date(new Date().getTime() - 1000*60*updateFrequency))) {
				File templatesDir = new File(new File(Setting.get("templates")), template.ownerTemplatesDirname);
				template.clientlibJob = org.daisy.pipeline.client.filestorage.JobStorage.loadJob(templateName, templatesDir);
			}
			
			// return the template
			return template;
		}
		// template not found
		return null;
	}
	
	/** Get a list of all templates that the user `user` is allowed to access. */
	public static List<Template> list(User user) { return list(user, false); }
	
	/** Get a list of all templates that the user `user` is allowed to access. Will refresh from disk if `forceUpdate` is true. */
	public static List<Template> list(User user, boolean forceUpdate) {
		
		// refresh from disk if needed
		int updateFrequency = 5;
		if (forceUpdate || templateCacheLastUpdated == null || templateCacheLastUpdated.before(new Date(new Date().getTime() - 1000*60*updateFrequency))) {
			templateCache.clear();
			
			File templatesDir = new File(Setting.get("templates"));
			for (File templateUserDir : templatesDir.listFiles()) {
				Long ownerId = null;
				if (templateUserDir.getName().matches("^\\d+-.*$")) {
					String[] nameSplit = templateUserDir.getName().split("-", 2);
					try {
						ownerId = Long.parseLong(nameSplit[0]);
					} catch (NumberFormatException e) { /* should never happen due to the regex in the if */ }
					
				}
				Object cacheKey = ownerId == null ? templateUserDir : ownerId;
				if (!templateCache.containsKey(cacheKey)) {
					templateCache.put(cacheKey, new HashMap<String, Template>());
				}
				for (File templateDir : templateUserDir.listFiles()) {
					Job clientlibJob = org.daisy.pipeline.client.filestorage.JobStorage.loadJob(templateDir.getName(), templateUserDir);
					templateCache.get(cacheKey).put(templateDir.getName(), new Template(templateDir.getName(), templateUserDir.getName(), ownerId, clientlibJob));
				}
			}
		}
		
		// determine which template directories this user has access to
		List<Object> cacheKeys = new ArrayList<Object>();
		if (user.admin) {
			cacheKeys.addAll(templateCache.keySet());
			
		} else {
			for (Object cacheKey : templateCache.keySet()) {
				if (!(cacheKey instanceof Long) || (Long)cacheKey == user.id) {
					cacheKeys.add(cacheKey);
				}
			}
		}
		
		// compile a list of all the templates in the directories that this user has access to
		List<Template> results = new ArrayList<Template>();
		for (Object cacheKey : cacheKeys) {
			Map<String, Template> templates = templateCache.get(cacheKey);
			if (templates != null) {
				results.addAll(templates.values());
			}
		}
		Collections.sort(results);
		
		return results;
	}

	public static Template create(Job clientlibJob, User user) {
		
		list(user, true); // force refresh of templateCache from disk to ensure the cache is in sync with the filesystem
		
		String userTemplatesDirname = null;
		if (templateCache.containsKey(user.id)) {
			Map<String, Template> userTemplates = templateCache.get(user.id);
			for (String key : userTemplates.keySet()) {
				userTemplatesDirname = userTemplates.get(key).ownerTemplatesDirname;
				break;
			}
			
		} else {
			templateCache.put(user.id, new HashMap<String,Template>());
		}
		
		if (userTemplatesDirname == null) {
			File templatesDir = new File(Setting.get("templates"));
			Logger.info("templates dir: "+templatesDir);
			for (File templateUserDir : templatesDir.listFiles()) {
				if (templateUserDir.getName().startsWith(user.id+"-")) {
					userTemplatesDirname = templateUserDir.getName();
				}
			}
		}
		if (userTemplatesDirname == null) {
			userTemplatesDirname = user.name.split(" ")[0];
			userTemplatesDirname = user.id+"-"+userTemplatesDirname.replaceAll("[/\\\\?%*:\\|\"<>\\.\\s]", "");
			if ((user.id+"-").equals(userTemplatesDirname)) {
				userTemplatesDirname = user.id+"-"+user.email.split("@")[0];
			}
		}
		
		File userTemplatesDir = new File(new File(Setting.get("templates")), userTemplatesDirname);
		Logger.info("using as template user dir: "+userTemplatesDir);
		if (!userTemplatesDir.exists() && !userTemplatesDir.mkdirs()) {
			userTemplatesDir = new File(new File(Setting.get("templates")), user.id+"-user");
			Logger.info("didn't work; using as template user dir: "+userTemplatesDir);
			if (!userTemplatesDir.exists() & !userTemplatesDir.mkdirs()) {
				Logger.error("Could not create templates directory: "+userTemplatesDir);
				return null;
			}
		}
		
		Job templateJob = new Job();
		Logger.info("clientlibJob nicename:"+clientlibJob.getNicename());
		templateJob.setJobXml(clientlibJob.toXml());
		Logger.info("creating template nicename based on number of templates");
		int templateCount = 1;
		for (Object cacheKey : templateCache.keySet()) {
			templateCount += templateCache.get(cacheKey).size();
		}
		Logger.info("there are "+templateCount+" templates in total");
		String templateNicename = "Template "+templateCount;
		while (templateCache.get(user.id).containsKey(templateNicename)) {
			Logger.info(templateNicename+" already exists, incrementing...");
			templateNicename = "Template "+(++templateCount);
		}
		templateJob.setNicename(templateNicename);
		Logger.info("using as template dirname (nicename): "+templateJob.getNicename());
		new File(userTemplatesDir, templateJob.getNicename());
		
		Logger.info("clientlibJob contextDir: "+clientlibJob.getJobStorage().getContextDir());
		
		if (clientlibJob.getJobStorage() == null || !clientlibJob.getJobStorage().getContextDir().isDirectory()) {
			new JobStorage(templateJob, userTemplatesDir, templateJob.getNicename());
			Logger.info("templateJob contextDir: "+templateJob.getJobStorage().getContextDir());
			
		} else {
			clientlibJob.getJobStorage().save();
			new JobStorage(templateJob, userTemplatesDir, clientlibJob.getJobStorage(), templateJob.getNicename());
			Logger.info("templateJob contextDir: "+templateJob.getJobStorage().getContextDir());
		}
		templateJob.getJobStorage().save();
		Template template = new Template(templateJob.getNicename(), userTemplatesDirname, user.id, templateJob);
		Logger.info("template.ownerTemplatesDirname: "+template.ownerTemplatesDirname);
		
		templateCache.get(user.id).put(templateJob.getNicename(), template);
		Logger.info("templateCache.get("+user.id+").put("+templateJob.getNicename()+", "+template.ownerTemplatesDirname+")");
		
		return template;
	}

	public void delete() {
		Logger.error("TODO: template was deleted at "+new Date()+", check stacktrace...", new Exception());
		if (templateCache.containsKey(this.ownerId)) {
			templateCache.get(this.ownerId).remove(this.name);
		}
		
		File userTemplateDir = new File(new File(new File(Setting.get("templates")), ownerTemplatesDirname), name);
		
		if (userTemplateDir.exists() && userTemplateDir.isDirectory()) {
			Logger.error("Deleting template directory: "+userTemplateDir);
			clientlibJob = null;
			Map<String, File> files;
			try {
				files = Files.listFilesRecursively(userTemplateDir, false);
			} catch (IOException e) {
				Logger.error("Could not list all files in the template directory for "+userTemplateDir, e);
				return;
			}
			for (String filename : files.keySet()) {
				files.get(filename).delete();
			}
			boolean aDirWasDeleted = true;
			while (aDirWasDeleted) {
				aDirWasDeleted = false;
				Map<String, File> dirs;
				try {
					dirs = Files.listFilesRecursively(userTemplateDir, true);
				} catch (IOException e) {
					Logger.error("Could not list all subdirectories in the template directory for "+userTemplateDir, e);
					return;
				}
				
				// attempt to avoid too many iterations of this while loop
				ArrayList<String> sortedKeys = new ArrayList<String>(dirs.keySet());
				Collections.sort(sortedKeys);
				Collections.reverse(sortedKeys);
				
				for (String dirname : sortedKeys) {
					File dir = dirs.get(dirname);
					if (dir.listFiles().length == 0) {
						aDirWasDeleted = dir.delete() || aDirWasDeleted;
					}
				}
			}
		}
	}

	public File asZip() {
		File dir = new File(new File(new File(Setting.get("templates")), ownerTemplatesDirname), name);
		String zipFilename = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "")+".zip";
		File zip = new File(new File(new File(System.getProperty("java.io.tmpdir")), "daisy-pipeline-webui-tempdir"), zipFilename);
		try {
			Files.zip(dir, zip);
			return zip;
			
		} catch (IOException e) {
			Logger.error("Unable to create zip", e);
			return null;
		}
	}
	
}
