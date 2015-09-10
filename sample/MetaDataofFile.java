import java.io.Serializable;

public class MetaDataofFile implements Serializable{
	long version;
	boolean isDir;
	boolean isExist;
	boolean isHit;
	long filesize;
	boolean isPermitted;
	MetaDataofFile(long version, boolean isDir, boolean isExist, boolean isHit, long filesize){
		this.version = version;
		this.isDir = isDir;
		this.isExist = isExist;
		this.isHit = isHit;
		this.filesize = filesize;
	}
}