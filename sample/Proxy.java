import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

class Proxy {
	public static String cacheDir;
	public static Map<String, Long> mostNewVersion = new HashMap<String, Long>();

	public static Map<String, Long> allVersions = new HashMap<String, Long>();

	public static Map<String, Integer> counter = new HashMap<String, Integer>();

	private static int port;
	private static String ip;
	private static LRUcache lru;
	static int chunkSize = 1025*1025;
	private static IServer server = null;
	
	public static IServer getServerInstance(String ip, int port) {
		String url = String.format("//%s:%d/ServerService", ip, port);
		try {
			return (IServer) Naming.lookup(url);
		} catch (MalformedURLException e) {
			System.err.println("Bad URL" + e);
		} catch (RemoteException e) {
			System.err.println("Remote connection refused to url " + url
					+ " " + e);
		} catch (NotBoundException e) {
			System.err.println("Not bound " + e);
		}
		return null;
	}

	public static IServer initialServer() {
		IServer server = null;
		try {
			server = getServerInstance(ip, port);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (server == null)
			System.exit(1);
		return server;
	}

	
	private static class FileHandler implements FileHandling {
		private int dummyfd = 500;
		private Map<Integer, FileEntity> StreamMap = new HashMap<Integer, FileEntity>();

		public void uploadByChunktoServer(String path, String pathinCache,
				IServer server) {
			int partCounter = 1;// name parts from 001, 002, 003, ...
			File f = new File(cacheDir + pathinCache);
			byte[] buffer = new byte[chunkSize];
			try (BufferedInputStream bis = new BufferedInputStream(
					new FileInputStream(f))) {
				int tmp = 0;
				while ((tmp = bis.read(buffer)) > 0) {
					server.uploadChunk(
							path + "." + String.format("%03d", partCounter),
							buffer, tmp);
					partCounter++;
					buffer = new byte[chunkSize];
				}
				bis.close();
				server.mergeChunktoHugeFile(path, System.currentTimeMillis(),
						partCounter);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	
		public void uploadFiletoServer(String path, String pathinCache,
				IServer server) {
			try {
				File file = new File(cacheDir + pathinCache);
				byte buffer[] = new byte[(int) file.length()];
				FileInputStream input = new FileInputStream(cacheDir
						+ pathinCache);
				input.read(buffer);
				input.close();
				server.uploadFile(path, buffer, System.currentTimeMillis());
			} catch (Exception e) {
				System.err.println("FileServer exception: " + e.getMessage());
				e.printStackTrace();
			}
		}	

		public int open(String path, OpenOption o) {
			// get metadata of file
			MetaDataofFile fileinfo = null;
			FileEntity file_entity = null;
			String pathinCache = null;
			try {
				fileinfo = server.checkMetaDataofFile(path);
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			dummyfd++;
			// the file is outside the servers's root directory
			if (!fileinfo.isPermitted)
				return Errors.EPERM;
			/* Open a file for read/write, create if it does not exist */
			if (o.equals(OpenOption.valueOf("CREATE"))) {

				try {
					if (fileinfo.isDir)
						return Errors.EISDIR;
					try {
						file_entity = new FileEntity("rw", false, path, true);
						pathinCache = lru.pullFiletoCacheifMiss(path, server,
								fileinfo, file_entity);
					} catch (Exception e) {
						e.printStackTrace();
					}
					RandomAccessFile stream = new RandomAccessFile(cacheDir
							+ pathinCache, "rw");
					file_entity.stream = stream;
					file_entity.pathinCache = pathinCache;
					StreamMap.put(dummyfd, file_entity);

					return dummyfd;
				} catch (IllegalArgumentException e) {
					return Errors.EINVAL;
				} catch (FileNotFoundException e) {
					return Errors.ENOENT;
				} catch (SecurityException e) {
					return Errors.EPERM;
				}
			}/*
			 * Create new file for read/write, returning error if it already
			 * exists
			 */
			else if (o.equals(OpenOption.valueOf("CREATE_NEW"))) {
				try {
					if (fileinfo.isExist)
						return Errors.EEXIST;
					if (fileinfo.isDir)
						return Errors.EISDIR;
					try {
						file_entity = new FileEntity("rw", false, path, true);
						pathinCache = lru.pullFiletoCacheifMiss(path, server,
								fileinfo, file_entity);
					} catch (Exception e) {
						e.printStackTrace();
					}
					RandomAccessFile stream = new RandomAccessFile(cacheDir
							+ pathinCache, "rw");
					file_entity.stream = stream;
					file_entity.pathinCache = pathinCache;
					StreamMap.put(dummyfd, file_entity);
					return dummyfd;
				} catch (IllegalArgumentException e) {
					return Errors.EINVAL;
				} catch (FileNotFoundException e) {
					return Errors.ENOENT;
				} catch (SecurityException e) {
					return Errors.EPERM;
				}
			}/* Open existing file or directory for read only */
			else if (o.equals(OpenOption.valueOf("READ"))) {
				try {
					if (!fileinfo.isExist)
						return Errors.ENOENT;
					if (fileinfo.isDir) {
						FileEntity dummy_file = new FileEntity("r", true, path,
								false);
						StreamMap.put(dummyfd, dummy_file);
					} else {
						try {
							file_entity = new FileEntity("r", false, path,
									false);
							pathinCache = lru.pullFiletoCacheifMiss(path, server,
									fileinfo, file_entity);
						} catch (Exception e) {
							e.printStackTrace();
						}
						RandomAccessFile stream = new RandomAccessFile(cacheDir
								+ pathinCache, "r");
						file_entity.stream = stream;
						file_entity.pathinCache = pathinCache;
						StreamMap.put(dummyfd, file_entity);
					}
					return dummyfd;
				} catch (IllegalArgumentException e) {
					return Errors.EINVAL;
				} catch (FileNotFoundException e) {
					return Errors.ENOENT;
				} catch (SecurityException e) {
					return Errors.EPERM;
				}
			}/* Open existing fie for read/write */
			else if (o.equals(OpenOption.valueOf("WRITE"))) {
				try {
					if (!fileinfo.isExist)
						return Errors.ENOENT;
					if (fileinfo.isDir)
						return Errors.EISDIR;
					try {
						file_entity = new FileEntity("rw", false, path, true);
						pathinCache = lru.pullFiletoCacheifMiss(path, server,
								fileinfo, file_entity);
					} catch (Exception e) {
						e.printStackTrace();
					}
					RandomAccessFile stream = new RandomAccessFile(cacheDir
							+ pathinCache, "rw");
					file_entity.stream = stream;
					file_entity.pathinCache = pathinCache;
					StreamMap.put(dummyfd, file_entity);
					return dummyfd;
				} catch (IllegalArgumentException e) {
					return Errors.EINVAL;
				} catch (FileNotFoundException e) {
					return Errors.ENOENT;
				} catch (SecurityException e) {
					return Errors.EPERM;
				}
			}
			return Errors.EINVAL;
		}

		public void deleteAlltheHashmapRecord(long curVersion,
				String pathinCache) {
			allVersions.remove(pathinCache);
			counter.remove(pathinCache);
		}

		public int close(int fd) {
			if (!StreamMap.containsKey(fd))
				return Errors.EBADF;
			FileEntity file_entity = StreamMap.get(fd);
			RandomAccessFile stream = file_entity.stream;
			String pathinCache = file_entity.pathinCache;
			if (file_entity.isDir)
				return Errors.EISDIR;
			File target = new File(cacheDir + pathinCache);
			if (!target.exists())
				return Errors.ENOENT;
			
			int success = 0;
			lru.setAsHeadofLRUlistwhenClose(pathinCache);
			try {
				stream.close();
				StreamMap.remove(fd);
				// if it is read
				if (!file_entity.isWrite) {
					int users = counter.get(pathinCache);
					// users > 1 others still use this file
					if (users > 1)
						counter.put(pathinCache, users - 1);
					// no one is using
					else {
						// if it is not the newest version, then delete
						long curVersion = allVersions.get(pathinCache);
						if (curVersion != mostNewVersion.get(file_entity.path)) {
							File expired = new File(cacheDir + pathinCache);
							lru.deleteFilefromCache(expired.length(),
									pathinCache);
							expired.delete();
							deleteAlltheHashmapRecord(curVersion, pathinCache);
						}
						// if it is newest version
						else
							counter.put(pathinCache, 0);
					}
				}
				// if it is write, always delete the write-copy
				else {
					File expired = new File(cacheDir + pathinCache);
					if (expired.length() > chunkSize)
						uploadByChunktoServer(file_entity.path, pathinCache,
								server);
					else
						uploadFiletoServer(file_entity.path, pathinCache,
								server);
					lru.deleteFilefromCache(expired.length(), pathinCache);
					expired.delete();
				}
				return success;
			} catch (IOException e) {
				return Errors.EPERM;
			}
		}

		public long write(int fd, byte[] buf) {
			if (!StreamMap.containsKey(fd))
				return Errors.EBADF;
			long count = 0;
			FileEntity file_entity = StreamMap.get(fd);
			RandomAccessFile stream = file_entity.stream;
			String pathinCache = file_entity.pathinCache;
			File target = new File(cacheDir + pathinCache);
			if (!target.exists())
				return Errors.ENOENT;
			if (!target.canWrite())
				return Errors.EPERM;
			if (file_entity.isDir)
				return Errors.EISDIR;
			try {
				// in read mode, try to write, return error
				if (file_entity.privilege.equals("r"))
					return Errors.EBADF;
				stream.write(buf);
				count = buf.length;
				return count;
			} catch (IOException e) {
				return Errors.EPERM;
			}
		}

		public long read(int fd, byte[] buf) {
			if (!StreamMap.containsKey(fd))
				return Errors.EBADF;
			long count = 0;
			FileEntity file_entity = StreamMap.get(fd);
			RandomAccessFile stream = file_entity.stream;
			String pathinCache = file_entity.pathinCache;
			File target = new File(cacheDir + pathinCache);
			if (file_entity.isDir)
				return Errors.EISDIR;
			if (!target.exists()) {
				return Errors.ENOENT;
			}
			if (!target.canRead())
				return Errors.EPERM;

			long prePointer = 0;
			long afterPointer = 0;
			long length = 0;
			try {
				prePointer = stream.getFilePointer();
			} catch (IllegalArgumentException e) {
				return Errors.EINVAL;
			} catch (FileNotFoundException e) {
				return Errors.ENOENT;
			} catch (SecurityException e) {
				return Errors.EPERM;
			} catch (IOException e) {
				return Errors.EPERM;
			}
			try {
				count = stream.read(buf);
			} catch (IllegalArgumentException e) {
				return Errors.EINVAL;
			} catch (FileNotFoundException e) {
				return Errors.ENOENT;
			} catch (SecurityException e) {
				return Errors.EPERM;
			} catch (IOException e) {
				return Errors.EPERM;
			}
			try {
				afterPointer = stream.getFilePointer();
			} catch (IllegalArgumentException e) {
				return Errors.EINVAL;
			} catch (FileNotFoundException e) {
				return Errors.ENOENT;
			} catch (SecurityException e) {
				return Errors.EPERM;
			} catch (IOException e) {
				return Errors.EPERM;
			}
			try {
				length = stream.length();
			} catch (IllegalArgumentException e) {
				return Errors.EINVAL;
			} catch (FileNotFoundException e) {
				return Errors.ENOENT;
			} catch (SecurityException e) {
				return Errors.EPERM;
			} catch (IOException e) {
				return Errors.EPERM;
			}
			if (afterPointer == length)
				return (afterPointer - prePointer);
			else
				return count;
		}

		public long lseek(int fd, long pos, LseekOption o) {
			System.out.print("lseek fd:" + fd + " pos:" + pos + " ");
			if (!StreamMap.containsKey(fd))
				return Errors.EBADF;
			FileEntity file_entity = StreamMap.get(fd);
			RandomAccessFile stream = file_entity.stream;
			String pathinCache = file_entity.pathinCache;
			File target = new File(cacheDir + pathinCache);
			if (!target.exists())
				return Errors.ENOENT;

			if (file_entity.isDir)
				return Errors.EISDIR;
			long offsetPointer = 0;
			if (o.equals(LseekOption.valueOf("FROM_START"))) {
				try {
					stream.seek(pos);
					offsetPointer = stream.getFilePointer();
				} catch (IOException e) {
					return Errors.EPERM;
				}
			} else if (o.equals(LseekOption.valueOf("FROM_END"))) {
				try {
					stream.seek(stream.length() - pos);
					offsetPointer = stream.getFilePointer();
				} catch (IOException e) {
					return Errors.EPERM;
				}
			} else if (o.equals(LseekOption.valueOf("FROM_CURRENT"))) {
				long currentPointer = 0;
				try {
					currentPointer = stream.getFilePointer();
					stream.seek(currentPointer + pos);
					offsetPointer = stream.getFilePointer();
				} catch (IOException e) {
					return Errors.EPERM;
				}
			} else {
				return Errors.EINVAL;
			}
			return offsetPointer;
		}

		public int unlink(String path) {
			MetaDataofFile fileinfo = null;
			boolean success = false;
			try {
				fileinfo = server.checkMetaDataofFile(path);
				fileinfo.isHit = lru.isCacheHit(path, fileinfo);
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (!fileinfo.isExist)
				return Errors.ENOENT;
			if (fileinfo.isDir)
				return Errors.EISDIR;
			// unlink file in server
			try {
				success = server.deleteFile(path);
			} catch (Exception e) {
				e.printStackTrace();
			}
			// unlink file in cache
			if (fileinfo.isHit) {
				String pathinCache = String.valueOf(path.hashCode());
				File Garbage = new File(cacheDir + pathinCache);
				Garbage.delete();
			}
			if (success)
				return 0;
			else
				return Errors.EPERM;
		}

		public void clientdone() {
			return;
		}

	}

	private static class FileHandlingFactory implements FileHandlingMaking {
		public FileHandling newclient() {
			return new FileHandler();
		}
	}

	public static void main(String[] args) throws IOException {
		ip = args[0];
		port = Integer.parseInt(args[1]);
		cacheDir = args[2] + "/";
		lru = new LRUcache(Long.parseLong(args[3]));
		server = initialServer();
		System.out.println("Hello World");
		System.out.println("cache size: "+String.valueOf(lru.maxsize));
		(new RPCreceiver(new FileHandlingFactory())).run();
	}
}
