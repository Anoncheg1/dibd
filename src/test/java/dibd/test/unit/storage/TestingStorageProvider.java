package dibd.test.unit.storage;

import dibd.storage.StorageBackendException;
import dibd.storage.StorageNNTP;
import dibd.storage.StorageProvider;

public class TestingStorageProvider implements StorageProvider {
    
	protected StorageNNTP instance;
	
	public TestingStorageProvider(StorageNNTP s){
		instance = s; 
	}
	
    @Override
    public boolean isSupported(String uri) {
        return true;
    }

    @Override
    public StorageNNTP storage(Thread thread) throws StorageBackendException {
    	return instance;
    }
}
