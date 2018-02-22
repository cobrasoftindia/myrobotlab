package org.myrobotlab.framework.repo;

import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.myrobotlab.codec.CodecUtils;
import org.myrobotlab.framework.ServiceReservation;
import org.myrobotlab.framework.ServiceType;
import org.myrobotlab.framework.Status;
import org.myrobotlab.framework.interfaces.LoggingSink;
import org.myrobotlab.io.FileIO;
import org.myrobotlab.logging.LoggerFactory;
import org.slf4j.Logger;

public abstract class Repo {

	public final static Logger log = LoggerFactory.getLogger(Repo.class);

	TreeMap<String, ServiceDependency> libraries = new TreeMap<String, ServiceDependency>();

	final static String REPO_STATE_FILE_NAME = "repo.json";

	public static final String INSTALL_START = "installationStart";
	public static final String INSTALL_FINISHED = "installationStop";

	protected static transient Map<String, Repo> localInstances = new HashMap<String, Repo>();

	// Repo is an interface to a singleton of each "type" of repo
	private static String defaultRepoManagerType = "Ivy";

	List<Status> errors = new ArrayList<Status>();

	transient Set<LoggingSink> installLoggingSinks = new HashSet<LoggingSink>();

	public void info(String format, Object... args) {
		for (LoggingSink service : installLoggingSinks) {
			service.info(format, args);
		}
	}

	public void install() {
		// if a runtime exits we'll broadcast we are starting to install
		ServiceData sd = ServiceData.getLocalInstance();
		info("starting installation of %s services", sd.getServiceTypeNames().length);
		for (ServiceType service : sd.getServiceTypes()) {
			install(service.getSimpleName());
		}
		info("finished installing %s", sd.getServiceTypeNames().length);
	}

	public void install(String serviceType) {
		install("../libraries/jar", serviceType);
	}

	abstract public void install(String location, String serviceType);

	/**
	 * searches through dependencies directly defined by the service and all Peers -
	 * recursively searches for their dependencies if any are not found - returns
	 * false
	 * 
	 * @param fullTypeName
	 *            f
	 * @return true/false
	 */
	public boolean isServiceTypeInstalled(String fullTypeName) {
		ServiceData sd = ServiceData.getLocalInstance();

		if (!sd.containsServiceType(fullTypeName)) {
			log.error("unknown service {}", fullTypeName);
			return false;
		}

		Set<ServiceDependency> libraries = getUnfulfilledDependencies(fullTypeName);
		if (libraries.size() > 0) {
			// log.info("{} is NOT installed", fullTypeName);
			return false;
		}

		// log.info("{} is installed", fullTypeName);
		return true;
	}

	public Set<ServiceDependency> getUnfulfilledDependencies(String type) {
		if (!type.contains(".")) {
			type = String.format("org.myrobotlab.service.%s", type);
		}

		Set<ServiceDependency> ret = new LinkedHashSet<ServiceDependency>();

		// get the dependencies required by the type
		ServiceData sd = ServiceData.getLocalInstance();
		if (!sd.containsServiceType(type)) {
			log.error(String.format("%s not found", type));
			return ret;
		}

		ServiceType st = sd.getServiceType(type);

		// look through our repo and resolve
		// if we dont have it - we need it
		List<ServiceDependency> metaDependencies = st.getDependencies();

		if (metaDependencies != null && metaDependencies.size() > 0) {
			for (ServiceDependency library : metaDependencies) {
				String key = library.getKey();
				if (!libraries.containsKey(key) || !libraries.get(key).isInstalled()) {
					ret.add(library);
				}
			}
		}

		Map<String, ServiceReservation> peers = st.getPeers();
		if (peers != null) {
			for (String key : peers.keySet()) {
				ServiceReservation sr = peers.get(key);
				ret.addAll(getUnfulfilledDependencies(sr.fullTypeName));
			}
		}

		return ret;
	}

	public void clear() {
		log.info("Repo.clear - clearing libraries");
		FileIO.rm("libraries");
		log.info("Repo.clear - clearing repo");
		FileIO.rm("repo");
		log.info("Repo.clear - {}", REPO_STATE_FILE_NAME);
		FileIO.rm(REPO_STATE_FILE_NAME);
		log.info("Repo.clear - clearing memory");
		libraries.clear();
		log.info("clearing errors");
		clearErrors();
	}

	public void clearErrors() {
		errors.clear();
	}

	public final static String makeFullTypeName(String type) {
		if (type == null) {
			return null;
		}
		if (!type.contains(".")) {
			return String.format("org.myrobotlab.framework.repo.%s", type);
		}
		return type;
	}
	
	static public Repo getInstance() {
		return getInstance(defaultRepoManagerType);
	}

	public static Repo getInstance(String type) {
		
		type = makeFullTypeName(type);

		if (localInstances.containsKey(type)) {
			return localInstances.get(type);
		} else {
			try {

				// FIXME - string only specifier - no class import info in this file (or
				// perhaps wrapper is ok?)
				// Maven.getInstance();
				Class<?> theClass = Class.forName(type);

				// getPeers
				Method method = theClass.getMethod("getTypeInstance");
				Repo repo = (Repo) method.invoke(null);
				return repo;
			} catch (Exception e) {
				log.error("instanciating {} failed", type, e);
			}
			return null;
		}
	}

	
	public void addLoggingSink(LoggingSink service) {
		installLoggingSinks.add(service);
	}

	/*
	 * public void publishStatus(Status status) { for (LoggingSink service :
	 * installLoggingSinks) { if (status.isInfo()) { service.info(format, args) }
	 * service.publishStatus(status); } }
	 */

	/**
	 * saves repo to file
	 */
	public void save() {
		try {
			FileOutputStream fos = new FileOutputStream(REPO_STATE_FILE_NAME);
			fos.write(CodecUtils.toJson(this).getBytes());
			fos.close();
		} catch (Exception e) {
			log.error("save threw", e);
		}
	}

	public boolean isInstalled(String string) {
		// TODO Auto-generated method stub
		return false;
	}

}