import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.Remote;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;

//You should investigate when to use UnicastRemoteObject vs Serializable. This is really important!
public class Server extends UnicastRemoteObject implements IServer {
	private static String rootDir;
	private static int port;
	private static Map<String, Long> versions = new HashMap<String, Long>();
	int chunkSize = 1025*1025;
	public Server() throws RemoteException {
	}

	public String sayHello() throws RemoteException {
		return "Hello :)";
	}

	public MetaDataofFile checkMetaDataofFile(String filepath)
			throws RemoteException {
		System.out.println("Trip to server: check meta data");
		MetaDataofFile fileinfo = new MetaDataofFile(-1, false, false, false, 0);
		File root = new File(rootDir);
		File target = new File(rootDir+filepath);
	
		if (target.exists()) {
			fileinfo.isExist = true;
			// first time to be accessed, create a versionID in server
			if (!versions.containsKey(filepath)) {
				long fileversion = System.currentTimeMillis();
				versions.put(filepath, fileversion);
			}
			fileinfo.version = versions.get(filepath);
		}
		if (target.isDirectory())
			fileinfo.isDir = true;
		fileinfo.filesize = target.length();
		//check the whether the file under the root Directory
		try {
			String absuloteroot = root.getCanonicalPath();
			String absolutetarget = target.getCanonicalPath();
			if (!absolutetarget.startsWith(absuloteroot))
				fileinfo.isPermitted = false;
			else
				fileinfo.isPermitted = true;
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return fileinfo;
	}
	public boolean deleteFile(String path) throws RemoteException {
		System.out.println("Trip to server: delete file");
		try {
			File file = new File(rootDir+path);
			versions.remove(path);
			return file.delete();
		
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	public void createFile(String path) throws RemoteException {
		System.out.println("Trip to server: create file");
		try {
			File file = new File(rootDir+path);
			file.createNewFile();
			// put this new file into hashmap of version
			long fileversion = System.currentTimeMillis();
			versions.put(path, fileversion);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void mergeChunktoHugeFile(String path, long version, int partCounter)
			throws RemoteException {
		System.out.println("Trip to server: merge huge file");
		
		try {
			File ofile = new File(rootDir + path);
			BufferedInputStream fis;
			byte[] buffer;
			int tmp = 0;
			int partID = 1;
			BufferedOutputStream fos = new BufferedOutputStream(
					new FileOutputStream(ofile));
			while (partID < partCounter) {
				File file = new File(rootDir + path + "."
						+ String.format("%03d", partID));
				partID++;
				fis = new BufferedInputStream(
						new FileInputStream(file));
				buffer = new byte[chunkSize];
				tmp = fis.read(buffer);
				fos.write(buffer, 0, tmp);
				buffer = null;
				fis.close();
				fis = null;
				file.delete();
			}
			fos.flush();
			fos.close();
			// update file version
			versions.put(path, version);
		} catch (Exception e) {
			System.out.println("FileImpl: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void uploadChunk(String path, byte[] buffer, int length) throws RemoteException {
		System.out.println("Trip to server: upload chunk");
		
		try {
			File file = new File(rootDir + path);
			BufferedOutputStream output = new BufferedOutputStream(
					new FileOutputStream(file));
			output.write(buffer, 0, length);
			output.flush();
			output.close();
		} catch (Exception e) {
			System.out.println("FileImpl: " + e.getMessage());
			e.printStackTrace();
		}
	}
	public void uploadFile(String path, byte[] buffer, long version) throws RemoteException{
		System.out.println("Trip to server: upload small file");
		
		try {
			File file = new File(rootDir+path);
			BufferedOutputStream output = new BufferedOutputStream(
					new FileOutputStream(file));
			output.write(buffer, 0, buffer.length);
			output.flush();
			output.close();
			//update file version
			versions.put(path,version);
		} catch (Exception e) {
			System.out.println("FileImpl: " + e.getMessage());
			e.printStackTrace();
			
		}
	}
	public byte[] downloadChunk(String path, int partID) throws RemoteException {
		System.out.println("Trip to server: download chunk");
		
		try {
			File file = new File(rootDir + path + "."
					+ String.format("%03d", partID));
			byte buffer[] = new byte[(int) file.length()];
			BufferedInputStream input = new BufferedInputStream(
					new FileInputStream(file));
			input.read(buffer, 0, buffer.length);
			input.close();
			// delete the part-file
			file.delete();
			return (buffer);
		} catch (Exception e) {
			e.printStackTrace();
			return (null);
		}
	}

	public int splitFile(String path) throws RemoteException {
		System.out.println("Trip to server: split file");
		
		int partCounter = 1;// name parts from 001, 002, 003, ...
		File f = new File(rootDir + path);
		byte[] buffer = new byte[chunkSize];

		try (BufferedInputStream bis = new BufferedInputStream(
				new FileInputStream(f))) {
			int tmp = 0;
			while ((tmp = bis.read(buffer)) > 0) {
				// write each chunk of data into separate file with different
				// number in name
				File newFile = new File(rootDir + path + "."
						+ String.format("%03d", partCounter));
				partCounter++;
				try (FileOutputStream out = new FileOutputStream(newFile)) {
					out.write(buffer, 0, tmp);// tmp is chunk size
				}
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
		return partCounter;
	}
	public byte[] downloadFile(String path) throws RemoteException {
		System.out.println("Trip to server: download small file");
		
		try {
			File file = new File(rootDir+path);
			byte buffer[] = new byte[(int) file.length()];
			BufferedInputStream input = new BufferedInputStream(
					new FileInputStream(file));
			input.read(buffer, 0, buffer.length);
			input.close();
			return (buffer);
		} catch (Exception e) {
			System.out.println("FileImpl: " + e.getMessage());
			e.printStackTrace();
			return (null);
		}
	}

	public static void main(String[] args) {

		port = Integer.parseInt(args[0]); // you should get port from args
		rootDir = args[1]+"/";
		
		try {
			// create the RMI registry if it doesn't exist.
			LocateRegistry.createRegistry(port);
		} catch (RemoteException e) {
			System.err.println("Failed to create the RMI registry " + e);
		}

		Server server = null;
		try {
			server = new Server();
		} catch (RemoteException e) {
			System.err.println("Failed to create server " + e);
			System.exit(1);
		}
		try {
			Naming.rebind(String.format("//127.0.0.1:%d/ServerService", port),
					server);
		} catch (RemoteException e) {
			System.err.println(e); 
		} catch (MalformedURLException e) {
			System.err.println(e); 
		}

	}
}
