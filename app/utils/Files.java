package utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import play.Logger;

/**
 * Utility class for appending files to ZIP archives.
 * 
 * Based on:
 * 		http://snippets.dzone.com/posts/show/3468
 * 		http://stackoverflow.com/a/8829253/281065
 * 		http://stackoverflow.com/a/1399432/281065
 * 		http://stackoverflow.com/a/2265206/281065
 * 
 */
public class Files {

	/**
	 * Convenience method for addFilesToZip(File zipFile, Map<String,File>
	 * files).
	 * 
	 * Appends all the files in the `directory` into the `zipFile` with paths
	 * relative to `directory`s parent directory.
	 * 
	 * @param zipFile
	 *            The ZIP-file.
	 * @param directory
	 *            The directory to add to the ZIP.
	 * @throws IOException
	 */
	public static void addDirectoryToZip(File zipFile, File directory) throws IOException {
		Map<String, File> files = listFilesRecursively(directory, directory.getParentFile().toURI(), true);
		addFilesToZip(zipFile, files);
	}

	/**
	 * Convenience method for addFilesToZip(File zipFile, Map<String,File>
	 * files).
	 * 
	 * Appends all the files in the `directory` into the `zipFile` with paths
	 * relative to the `directory`.
	 * 
	 * @param zipFile
	 *            The ZIP-file.
	 * @param directory
	 *            The directory to add to the ZIP.
	 * @throws IOException
	 */
	public static void addDirectoryContentsToZip(File zipFile, File directory) throws IOException {
		Map<String, File> files = listFilesRecursively(directory, directory.toURI(), true);
		addFilesToZip(zipFile, files);
	}

	/**
	 * Lists all files recursively, starting at `directory`, resolving their
	 * relative paths against `base`. The return value can be used as an argument
	 * for addFilesToZip(File zipFile, Map<String,File> files);
	 * 
	 * @param directory
	 * @param base
	 * @return
	 * @throws IOException
	 */
	public static Map<String, File> listFilesRecursively(File directory, URI base, boolean includeDirectories) throws IOException {
		Map<String, File> files = new HashMap<String, File>();

		if (directory.isDirectory()) {
			if (includeDirectories) {
				String name = base.relativize(directory.toURI()).getPath();
				if (!name.endsWith("/"))
					name += "/";
				files.put(name, directory);
			}
			
			for (File file : directory.listFiles()) {
//				if (file.isDirectory()) {
					Map<String, File> subfiles = listFilesRecursively(file, base, includeDirectories);
					for (String subfile : subfiles.keySet())
						files.put(subfile, subfiles.get(subfile));

//				} else if (file.isFile()) {
//					files.put(base.relativize(file.toURI()).getPath(), file);
//
//				}
			}

		} else if (directory.isFile()) {
			files.put(base.relativize(directory.toURI()).getPath(), directory);
		}

		return files;
	}
	
	/**
	 * Does not actually load the files, since that could potentially eat up your RAM.
	 * All File objects are set to null instead.
	 * 
	 * @param zipfile
	 * @return
	 */
	public static List<String> listZipFiles(File zipfile) {
		List<String> files = new ArrayList<String>();
		
		try {
			ZipFile zip = new ZipFile(zipfile);
			Enumeration<? extends ZipEntry> zipEntries = zip.entries();
			
			while (zipEntries.hasMoreElements()) {
				ZipEntry entry = (ZipEntry) zipEntries.nextElement();
				if (!entry.isDirectory())
					files.add(entry.getName());
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return files;
	}
	
	/**
	 * Same as listZipFiles(File zipfile), except that this will
	 * return a list of [ zip entry name , content type ] pairs.
	 * The content type is based purely on the filename.
	 * 
	 * @param zipfile
	 * @return
	 */
	public static List<FileInfo> listZipFilesWithContentType(File zipfile) {
		List<FileInfo> files = new ArrayList<FileInfo>();
		
		try {
			ZipInputStream zin = new ZipInputStream(new FileInputStream(zipfile));
			ZipEntry entry = zin.getNextEntry();
			while (entry != null) {
				
				if (!entry.isDirectory()) {
					String entryName = entry.getName();
					String contentType = ContentType.probe(entryName, zin);
					Long entrySize = entry.getSize();
					Logger.debug("file: "+entryName+" | contentType: "+contentType+" | "+entrySize);
					files.add(new FileInfo(entryName, contentType, entrySize));
				}
				
				entry = zin.getNextEntry();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return files;
	}

	/**
	 * Convenience method for addFilesToZip(File zipFile, Map<String,File>
	 * files).
	 * 
	 * Appends all the `files` into the `zipFile` with paths relative to
	 * `baseDirectory`.
	 * 
	 * @param zipFile
	 *            The ZIP-file.
	 * @param files
	 *            The list of files to append to the ZIP-file.
	 * @param baseDirectory
	 *            The directory to resolve the relative file paths against.
	 * @throws IOException
	 */
	public static void addFilesToZip(File zipFile, File[] files, File baseDirectory) throws IOException {
		URI base = baseDirectory.toURI();

		Map<String, File> relativeFiles = new HashMap<String, File>();
		for (File file : files) {
			if (file.isFile()) {
				String name = base.relativize(file.toURI()).getPath();
				relativeFiles.put(name, file);
			}
		}

		addFilesToZip(zipFile, relativeFiles);
	}

	/**
	 * This is where the action happens. The other functions uses this one, but
	 * this can be used directly as well.
	 * 
	 * @param zipFile
	 *            The ZIP file.
	 * @param files
	 *            A map of all the files to add to the ZIP file, where the key
	 *            is the ZIP entry name to use (the relative file paths).
	 * @throws IOException
	 */
	public static void addFilesToZip(File zipFile, Map<String, File> files) throws IOException {

		// get a temp file
		File tempFile = File.createTempFile(zipFile.getName(), null);

		// delete it, otherwise you cannot rename your existing zip to it.
		tempFile.delete();

		if (!zipFile.renameTo(tempFile)) {
			throw new RuntimeException("could not rename the file " + zipFile.getAbsolutePath() + " to " + tempFile.getAbsolutePath());
		}

		// 4MiB buffer
		byte[] buf = new byte[4096 * 1024];

		ZipInputStream zin = new ZipInputStream(new FileInputStream(tempFile));
		ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));

		ZipEntry entry = zin.getNextEntry();
		while (entry != null) {
			String entryName = entry.getName();
			boolean notInFiles = true;
			for (String fileName : files.keySet()) {
				if (fileName.equals(entryName)) {
					notInFiles = false;
					break;
				}
			}
			if (notInFiles) {
				Logger.debug("keeping in ZIP: "+entryName);
				// Add ZIP entry to output stream.
				out.putNextEntry(new ZipEntry(entryName));
				// Transfer bytes from the ZIP file to the output file
				int len;
				while ((len = zin.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
			}
			entry = zin.getNextEntry();
		}
		// Close the streams
		zin.close();

		// Compress the files
		for (String fileName : files.keySet()) {
			Logger.debug("adding to ZIP: "+fileName);
			if (files.get(fileName).isDirectory()) {
				// TODO
			} else {
				InputStream in = new FileInputStream(files.get(fileName));
				// Add ZIP entry to output stream.
				out.putNextEntry(new ZipEntry(fileName));
				// Transfer bytes from the file to the ZIP file
				int len;
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
				// Complete the entry
				out.closeEntry();
				in.close();
			}
		}

		// Complete the ZIP file
		out.close();
		tempFile.delete();
	}
	
	/**
	 * Unzip the ZIP-file `zip` to the directory `dir`.
	 * 
	 * @param zip ZIP file
	 * @param dir output directory
	 * @throws IOException 
	 */
	public static void unzip(File zip, File dir) throws IOException {
		if (!zip.exists()) {
			IOException e = new IOException("ZIP file does not exist: "+(zip!=null?zip.getAbsolutePath():"[null]"));
			Logger.error("ZIP file does not exist: "+(zip!=null?zip.getAbsolutePath():"[null]"), e);
			throw e;
		}
		if (!zip.isFile()) {
			IOException e = new IOException("ZIP file is not a file: "+(zip!=null?zip.getAbsolutePath():"[null]"));
			Logger.error("ZIP file is not a file: "+(zip!=null?zip.getAbsolutePath():"[null]"), e);
			throw e;
		}
		if (dir.exists() && !dir.isDirectory()) {
			IOException e = new IOException("ZIP output is not a directory: "+(dir!=null?dir.getAbsolutePath():"[null]"));
			Logger.error("ZIP output is not a directory: "+(dir!=null?dir.getAbsolutePath():"[null]"), e);
			throw e;
		}
		if (!dir.exists()) {
			dir.mkdirs();
		}
		
		ZipFile zipFile;
		try { zipFile = new ZipFile(zip); }
		catch (ZipException e) {
			Logger.error("Error while opening ZIP file: "+(zip!=null?zip.getAbsolutePath():"[null]"), e);
			throw e;
		} catch (IOException e) {
			Logger.error("Error while opening ZIP file: "+(zip!=null?zip.getAbsolutePath():"[null]"), e);
			throw e;
		}
		Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
		
    	// 4MiB buffer
		byte[] buf = new byte[4096 * 1024];
		
		while (zipEntries.hasMoreElements()) {
			ZipEntry entry = (ZipEntry) zipEntries.nextElement();
			
			if (entry.isDirectory()) {
				// Extracting directory
				new File(dir, entry.getName()).mkdirs();
				
            } else {
            	// Extracting file
            	
            	InputStream is;
				try { is = zipFile.getInputStream(entry); }
				catch (IOException e) {
					Logger.error("Error while opening ZIP entry '"+(entry!=null?entry.getName():"[null]")+"' from ZIP file: "+(zip!=null?zip.getAbsolutePath():"[null]"), e);
					throw e;
				}
            	FileOutputStream fos;
            	File entryFile = new File(dir, entry.getName());
            	entryFile.getParentFile().mkdirs();
				try { fos = new FileOutputStream(entryFile); }
				catch (FileNotFoundException e) {
					Logger.error("Error while opening output file '"+(entryFile!=null?entryFile.getAbsolutePath():"[null]")+"' for ZIP entry '"+(entry!=null?entry.getName():"[null]")+"' from ZIP file: "+(zip!=null?zip.getAbsolutePath():"[null]"), e);
					throw e;
				}
            	
            	int len;
				try {
					while ((len = is.read(buf)) > 0) {
						try { fos.write(buf, 0, len); }
						catch (IOException e) {
							Logger.error("Error while writing output file '"+(entryFile!=null?entryFile.getAbsolutePath():"[null]")+"' for ZIP entry '"+(entry!=null?entry.getName():"[null]")+"' from ZIP file: "+(zip!=null?zip.getAbsolutePath():"[null]"), e);
							throw e;
						}
					}
				} catch (IOException e) {
					Logger.error("Error while reading ZIP entry '"+(entry!=null?entry.getName():"[null]")+"' from ZIP file: "+(zip!=null?zip.getAbsolutePath():"[null]"), e);
					throw e;
				}
				
				try {
					is.close();
				} catch (IOException e) {
					Logger.error("Error while closing input stream from ZIP entry '"+(entry!=null?entry.getName():"[null]")+"' from ZIP file: "+(zip!=null?zip.getAbsolutePath():"[null]"), e);
					throw e;
				}
				try {
					fos.close();
				} catch (IOException e) {
					Logger.error("Error while closing output file '"+(entryFile!=null?entryFile.getAbsolutePath():"[null]"), e);
					throw e;
				}
            }
			
		}
	}
    
	/**
	 * Zip up the directory `dir` into a new ZIP-file `zip`. If `dir` is a file, then only that single file is zipped.
	 * 
	 * @param dir
	 * @param zip
	 * @throws IOException 
	 */
	public static void zip(File dir, File zip) throws IOException {
		zip.getParentFile().mkdirs();
		ZipOutputStream zipOs = new ZipOutputStream(new FileOutputStream(zip));
		
		byte buff[]= new byte[4 * 1024 * 1024];
        
		Map<String, File> files = listFilesRecursively(dir, dir.toURI(), true);
        for (String entryName : files.keySet()){
            ZipEntry entry = new ZipEntry(entryName);
            if (entry.isDirectory())
            	continue;
            zipOs.putNextEntry(entry);
            InputStream is=new FileInputStream(files.get(entryName));
            int read=0;
            while((read=is.read(buff))>0){
                zipOs.write(buff,0,read);
            }
            is.close();
        }
        
        zipOs.close();
        
	}
	
	public static void copy(File from, File to) throws IOException {
	    if(!to.exists()) {
	    	to.getParentFile().mkdirs();
	    	to.createNewFile();
	    }

	    FileChannel source = null;
	    FileChannel destination = null;

	    try {
	        source = new FileInputStream(from).getChannel();
	        destination = new FileOutputStream(to).getChannel();
	        destination.transferFrom(source, 0, source.size());
	    }
	    finally {
	        if(source != null) {
	            source.close();
	        }
	        if(destination != null) {
	            destination.close();
	        }
	    }
	}
	
}
