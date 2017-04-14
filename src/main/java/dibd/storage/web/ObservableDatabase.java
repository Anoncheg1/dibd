package dibd.storage.web;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Observable;

import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.article.Article;
import dibd.util.Log;

/**
 * For now notify observers to create new list of new articles.
 * 
 * 
 * @author user
 *
 */
public class ObservableDatabase extends Observable implements Runnable{

    private static int timeoutMillis = 1000 * 10; //10sec
	
    private static volatile ObservableDatabase _instance; //volatile variable
    
    public static ObservableDatabase inst(){
    	if(_instance == null){
    		synchronized(ObservableDatabase.class){
    			if(_instance == null){
    				_instance = new ObservableDatabase();
    				_instance.setChanged(); //for first run
    				(new Thread(_instance)).start();
    			}
    		}
    	}
    	return _instance;
    }

	private ObservableDatabase() {
		super();
		// TODO Auto-generated constructor stub
	}
	
	synchronized public void signal(){
		setChanged();
	}

	@Override
	public void run() {
		while(true)
			try {
				Thread.sleep(timeoutMillis);
				if (this.hasChanged()){
					List<Article> arts = StorageManager.current().indexLastArts(1, 150); //status 1
					
					this.notifyObservers(arts);
				}
				
			} catch (InterruptedException e) {
				break;
			} catch (StorageBackendException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

}
