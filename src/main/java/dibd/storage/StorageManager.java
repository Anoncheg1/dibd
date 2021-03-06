/*
 *   SONEWS News Server
 *   see AUTHORS for the list of contributors
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dibd.storage;

/**
 * Provides access to a storage backend.
 * Used for thread pools only.
 * 
 * @author Christian Lins
 * @since sonews/1.0
 */
public final class StorageManager {
	
	private static StorageProvider provider;
	
	//all thread-save
	//immutable objects (replacement for static)
	public static GroupsProvider groups;
	public static SubscriptionsProvider peers;
	public static AttachmentProvider attachments;
	public static NNTPCacheProvider nntpcache;
	//mutable
	public static OfferingHistory offers;
	
    
	

    /**
     * For static daemon threads.
     * 
     * @return interface for NNTP
     * @throws StorageBackendException
     */
    public static StorageNNTP current() throws StorageBackendException {
        synchronized (StorageManager.class) {
            if (provider == null) {
                return null;
            } else {
                return provider.storage(Thread.currentThread());
            }
        }
    }

    /**
     * Create given class name.
     * @return clazz.newInstance()
     */
    //TODO: block changing of loadProvider during work.
    public static StorageProvider loadProvider(String pluginClassName) {
        try {
            Class<?> clazz = Class.forName(pluginClassName);
            Object inst = clazz.newInstance();
            return (StorageProvider) inst;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            // Do not use logging here as the Log class requires a working
            // backend which is in most cases not available at this point
            System.out.println("Could not instantiate StorageProvider: " + ex);
            return null;
        }
    }

    /**
     * Sets the current storage provider.
     * 
     * @param provider
     */
    public static void enableProvider(StorageProvider provider) {
        synchronized (StorageManager.class) {
            if (StorageManager.provider != null) {
                disableProvider();
            }
            StorageManager.provider = provider;
        }
    }
    
    public static void enableGroupsProvider(GroupsProvider g) {
        synchronized (StorageManager.class) {
            StorageManager.groups = g;
        }
    }
    
    public static void enableSubscriptionsProvider(SubscriptionsProvider s) {
        synchronized (StorageManager.class) {
            StorageManager.peers = s;
        }
    }
    
    public static void enableAttachmentProvider(AttachmentProvider a) {
        synchronized (StorageManager.class) {
            StorageManager.attachments = a;
        }
    }
    
    public static void enableNNTPCacheProvider(NNTPCacheProvider c) {
        synchronized (StorageManager.class) {
            StorageManager.nntpcache = c;
        }
    }
    
    public static void enableOfferingHistory(OfferingHistory o) {
        synchronized (StorageManager.class) {
            StorageManager.offers = o;
        }
    }

    /**
     * Disables the current provider.
     */
    public static void disableProvider() {
        synchronized (StorageManager.class) {
            provider = null;
        }
    }
}