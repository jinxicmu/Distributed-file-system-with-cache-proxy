import java.io.RandomAccessFile;

	class FileEntity {
		RandomAccessFile stream;
		String privilege;
		boolean isDir;
		String path;
		String pathinCache;
		boolean isWrite;
		FileEntity(String privilege, boolean isDir,String path,
			 boolean isWrite) {
			this.path = path;
			this.privilege = privilege;
			this.isDir = isDir;
			this.isWrite = isWrite;
		}
	}