import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IServer extends Remote {	
	public String sayHello() throws RemoteException;
	public void createFile(String path) throws RemoteException;
	public boolean deleteFile(String path) throws RemoteException;
	public void uploadFile(String path, byte[] buffer, long version) throws RemoteException;
	public byte[] downloadFile(String path) throws RemoteException;
	public MetaDataofFile checkMetaDataofFile(String filepath) throws RemoteException;


	public byte[] downloadChunk(String path, int partID) throws RemoteException;

	public int splitFile(String path) throws RemoteException;

	public void uploadChunk(String path, byte[] buffer, int length) throws RemoteException;
	
	public void mergeChunktoHugeFile(String path, long version, int partCounter)
			throws RemoteException;
}
