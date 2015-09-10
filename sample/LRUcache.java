import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;

	public class LRUcache{
		long maxsize;
		long freesize;
		List<String> mru = new LinkedList<String>();
		LRUcache(long maxsize){
			this.freesize = maxsize;
			this.maxsize = maxsize;
		}
		int chunkSize = 1025*1025;
		synchronized boolean isCacheHit(String path, MetaDataofFile fileinfo) {
			if (Proxy.mostNewVersion.containsKey(path)) {
				long curVersion = Proxy.mostNewVersion.get(path);
				String pathinCache = String.valueOf(path.hashCode())
						+ String.valueOf(curVersion);
				File file = new File(Proxy.cacheDir + pathinCache);
				if (file.exists())
					if (curVersion == fileinfo.version) {
						return true;
					}
			}
			return false;
		}
	synchronized void dowloadByChunktoCache(String path, IServer server,
			MetaDataofFile fileinfo, FileEntity file_entity) {
		byte[] buffer = new byte[chunkSize];
		int partCounter = 0;
		try {
			partCounter = server.splitFile(path);
			int partID = 1;
			String pathinCache = String.valueOf(path.hashCode())
					+ String.valueOf(fileinfo.version);
			FileOutputStream fos = new FileOutputStream(Proxy.cacheDir
					+ pathinCache);
			while (partID < partCounter) {
				buffer = server.downloadChunk(path, partID);
				fos.write(buffer);
				partID++;
			}
			fos.close();
			Proxy.mostNewVersion.put(path, fileinfo.version);
			// put the file to allversions: key is string of pathincache
			Proxy.allVersions.put(pathinCache, fileinfo.version);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
		synchronized void dowloadFiletoCache(String path, IServer server,
				MetaDataofFile fileinfo, FileEntity file_entity) {
			try {
				byte[] filedata = server.downloadFile(path);
				String pathinCache = String.valueOf(path.hashCode())
						+ String.valueOf(fileinfo.version);
				FileOutputStream fos = new FileOutputStream(Proxy.cacheDir
						+ pathinCache);
				fos.write(filedata);
				fos.close();
				// put the file in cache to the mostNewVersion: which
				// key is the raw path
				Proxy.mostNewVersion.put(path, fileinfo.version);
				// put the file to allversions: key is string of pathincache which means containing versionID
				Proxy.allVersions.put(pathinCache, fileinfo.version);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		synchronized void createFileinServer(String path, IServer server) {
			try {
				server.createFile(path);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		synchronized void deletePreviousVersions(String path) {
			if (Proxy.mostNewVersion.containsKey(path)) {
				long curVersion = Proxy.mostNewVersion.get(path);
				String pathinCache = String.valueOf(path.hashCode())
						+ String.valueOf(curVersion);
				File file = new File(Proxy.cacheDir + pathinCache);
				if (file.exists() && Proxy.counter.get(pathinCache) == 0) {
					deleteFilefromCache(file.length(), pathinCache);
					file.delete();
					Proxy.allVersions.remove(pathinCache);
					Proxy.counter.remove(pathinCache);
					
				}
			}
		}
		synchronized String createWriteCopy(String path, String pathinCache)
				throws IOException {
			long version = System.currentTimeMillis() + 1;
			File source = new File(Proxy.cacheDir + pathinCache);
			String writeCopy = String.valueOf(path.hashCode())
					+ String.valueOf(version);
			File dest = new File(Proxy.cacheDir + writeCopy);
			try {
				if (filelessThanCache(source.length())) {
					deleteFileUntilGetEnoughFreeSpace(source.length());
					Files.copy(source.toPath(), dest.toPath());
					// create writecopy
					addFiletoCache(source.length(), writeCopy);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			return writeCopy;
		}
		synchronized String pullFiletoCacheifMiss(String path, IServer server,
				MetaDataofFile fileinfo, FileEntity file_entity)
				throws RemoteException {
			fileinfo.isHit = isCacheHit(path, fileinfo);
			// if cache miss, download file
			String pathinCache = String.valueOf(path.hashCode())
					+ String.valueOf(fileinfo.version);
			setAsHeadofLRUlistwhenOpen(pathinCache);
			if (!fileinfo.isHit) {
				try {
					if (!fileinfo.isExist)
						createFileinServer(path, server);
					// since not hit, means the previous versions is
					// outdated, delete it.
					deletePreviousVersions(path);
					if (filelessThanCache(fileinfo.filesize)) {
						deleteFileUntilGetEnoughFreeSpace(fileinfo.filesize);
						if (fileinfo.filesize < chunkSize)
						dowloadFiletoCache(path, server, fileinfo, file_entity);
					else
						dowloadByChunktoCache(path, server, fileinfo, file_entity);
						// when download from server, cache add item
						addFiletoCache(fileinfo.filesize, pathinCache);
					}
					// counter means the number of current users of specific
					// file version
					if (!file_entity.isWrite)
						Proxy.counter.put(pathinCache, 1);
					else
						Proxy.counter.put(pathinCache, 0);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				// if cache hit, write will still create a copy, but read will
				// share
				if (!file_entity.isWrite) {
					int preCounter = Proxy.counter.get(pathinCache);
					// add a user
					Proxy.counter.put(pathinCache, preCounter + 1);
				}
			}
			if (file_entity.isWrite) {
				// if write, then
				String writeCopy = null;
				try {
					writeCopy = createWriteCopy(path, pathinCache);
				} catch (IOException e) {
					e.printStackTrace();
				}
				return writeCopy;
			}
			return pathinCache;
		}
		
		
		
		
		synchronized boolean filelessThanCache(long filesize){
			if (filesize > (this.maxsize))
				return false;
			return true;
		}		
		synchronized void ensureHaveEnoughSpaceBydeleting(long filesize){
			if (filesize >= this.freesize)
				deleteFileUntilGetEnoughFreeSpace(filesize);
		}
		
		synchronized void addFiletoCache(long filesize, String filepath){
			this.freesize -= filesize;
			if (this.mru.contains(filepath))
				mru.remove(filepath);
			mru.add(0, filepath);
			//this.beingusedsize += filesize;
		}
		synchronized void deleteFilefromCache(long filesize, String filepath){		
			this.freesize += filesize;
			if (this.mru.contains(filepath))
				mru.remove(filepath);
			//this.beingusedsize += filesize;
		}
		synchronized void setAsHeadofLRUlistwhenOpen(String filepath){
			if (this.mru.contains(filepath))
				this.mru.remove(filepath);
			this.mru.add(0, filepath);	
		}
		synchronized void setAsHeadofLRUlistwhenClose(String filepath){
			if (this.mru.contains(filepath))
				this.mru.remove(filepath);
			this.mru.add(0, filepath);	
			
		}
		public void deleteHashmapRecord(String pathinCache){
			Proxy.allVersions.remove(pathinCache);
			Proxy.counter.remove(pathinCache);
			
		}
		
		synchronized void deleteFileUntilGetEnoughFreeSpace(long filesize){
		//	int pointer to the rear of mru list
			int pp = mru.size() - 1;
			
			while (this.freesize <= filesize){
				String deletedfile = (String)this.mru.get(pp);
				if (Proxy.counter.get(deletedfile) == 0){
					
					deleteHashmapRecord(deletedfile);
					File file = new File(Proxy.cacheDir+deletedfile);
					long releaseSpace = file.length(); 
					file.delete();
					System.out.println("delete file from cache: "+deletedfile+" size is: "+String.valueOf(releaseSpace));
					
					this.mru.remove(pp);
					this.freesize += releaseSpace;	
				}
				pp--;
			}
		}
		
	}
