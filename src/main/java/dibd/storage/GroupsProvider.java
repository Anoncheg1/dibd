/**
 * 
 */
package dibd.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import dibd.storage.SubscriptionsProvider.Subscription;
import dibd.util.Log;
import dibd.util.io.Resource;

/**
 * @author user
 *
 */
public class GroupsProvider {
	
	/**
	 * If this flag is set the Group is no real newsgroup but a mailing list
	 * mirror. In that case every posting and receiving mails must go through
	 * the mailing list gateway.
	 * Legacy not used 
	 */
	public static final int MAILINGLIST = 0x1;

	/**
	 * If this flag is set the Group is marked as readonly and the posting is
	 * prohibited. This can be useful for groups that are synced only in one
	 * direction.
	 * Legacy not used
	 */
	public static final int READONLY = 0x2;

	//TODO:how to delete group?
	/**
	 * If this flag is set the Group is marked as deleted and must not occur in
	 * any output. There is no actual deletion here for now.
	 * Legacy used.
	 */
	public static final int DELETED = 0x80;
	
	public class Group {
		
		// GROUP HERE
		private int id = 0;
		private int flags = -1;
		private String name = null;
		private Set<String> hosts = null; //never null

		/**
		 * Constructor.
		 *
		 * @param name
		 * @param id
		 * @param flags
		 * @param hosts
		 */
		private Group(final String name, final int id, final int flags, final Set<String> hosts) {
			this.id = id;
			this.flags = flags;
			this.name = name;
			this.hosts = hosts; //never null
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Group) {
				return ((Group) obj).id == this.id;
			} else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return (name + id).hashCode();
		}

		/*public List<Pair<Long, ArticleHead>> getArticleHeads(final long first, final long last)
				throws StorageBackendException {
			return StorageManager.current().getArticleHeads(this, first, last);
		}*/

		public List<Integer> getArticleNumbers(int start) throws StorageBackendException {
			return StorageManager.current().getArticleNumbers(id, start);
		}

		public long getLastArticleNumber() throws StorageBackendException {
			return StorageManager.current().getArticleCountGroup(id) + 1;
		}

		public long getFirstArticleNumber() {
			return 1;
		}

		public int getFlags() {
			return this.flags;
		}

		/**
		 * Peers for group.
		 * Return set may be empty but never null.
		 * 
		 * @return
		 */
		public Set<String> getHosts() {
			return Collections.unmodifiableSet(this.hosts);
		}

		/**
		 * @return Internal group id used for referencing in the backend
		 */
		public int getInternalID() {
			assert id > 0;
			return id;
		}

		/**
		 * Used to hide group.
		 * Do not have set method here.
		 * 
		 * @return
		 */
		public boolean isDeleted() {
			return (this.flags & DELETED) != 0;
		}

		public boolean isMailingList() {
			return (this.flags & MAILINGLIST) != 0;
		}

		public boolean isWriteable() {
			return true;
		}

		public String getName() {
			return name;
		}

		/**
		 * Performs this.flags |= flag to set a specified flag and updates the data
		 * in the JDBCDatabase.
		 *
		 * @param flag
		 */
		public void setFlag(final int flag) {
			this.flags |= flag;
		}

		public void unsetFlag(final int flag) {
			this.flags &= ~flag;
		}

		public void setName(final String name) {
			this.name = name;
		}

		/**
		 * @return Number of posted articles in this group.
		 * @throws StorageBackendException
		 */
		public long getPostingsCount() throws StorageBackendException {
			return StorageManager.current().getArticleCountGroup(id);
		}

	} //THE END of Group
	
	
	
	
	
	
	
	
	
	private final List<Group> allGroups = new ArrayList<Group>();
	private final Map<Integer, String> allGroupId = new HashMap<>();
	private final Map<String, Group> allGroupNames = new HashMap<>();
	private final Map<Subscription, Set<Group>> groupsPerSubscription = new HashMap<Subscription, Set<Group>>();

	public GroupsProvider() {
		/**
		 * Initialization. Reading groups.conf
		 */
		
			// synchronized(allGroups) {
			// if(allGroups.isEmpty()) {
			String groupsStr = Resource.getAsString("groups.conf", true);
			if (groupsStr == null) {
				Log.get().log(Level.SEVERE, "Could not read groups.conf");
				// Fatal exit
				System.exit(1);
			}

			String[] groupLines = groupsStr.split("\n");
			for (String groupLine : groupLines) {
				if (groupLine.startsWith("#")) {
					continue;
				}

				groupLine = groupLine.trim();
				String[] groupLineChunks = groupLine.split("\\s+");
				if (groupLineChunks.length < 3) {
					Log.get().log(Level.WARNING, "Malformed group.conf line: {0}", groupLine);
				} else {
					int id = Integer.parseInt(groupLineChunks[1]);
					int flags = Integer.parseInt(groupLineChunks[2]);
					Set<String> hosts = new HashSet<String>();
					if (groupLineChunks.length == 4)
						hosts.addAll(Arrays.asList(groupLineChunks[3].split("\\|")));
					Group group = new Group(groupLineChunks[0], id, flags, hosts);
					allGroups.add(group);
					allGroupId.put(id, groupLineChunks[0]);
					allGroupNames.put(groupLineChunks[0], group);
				}
			}
		
		/**1
		 *  1.1 we make groupsPerSubscription list
		 *  1.2 subscripted server must be at least in one of the group
		 * 2
		 *  hosts in group must have at least one subscripted server
		 * (peers.conf)
		 */
		
			//groupsPerSubscription = new HashMap<Subscription, Set<Group>>();
			//1
			for (Subscription s : StorageManager.peers.getAll()){
				Set<Group> sgroups = new HashSet<Group>();
				boolean inGroup = false;
				for (Group g : allGroups){
					if(!g.isDeleted()){
						if (!g.hosts.isEmpty())
							if(g.hosts.contains(s.getHost())){
								sgroups.add(g);
								inGroup = true;
							}
					}
				}
				if (inGroup) //1.2
					groupsPerSubscription.put(s, sgroups);
				else{//host must be at least in one of the group;
					//groupsPerSubscription.put(s, null);
					Log.get().log(Level.WARNING, "Host {0} of peers.conf must be in one of the group(groups.conf)", s.getHost());
				}
			}
			
			//2
			
			//for( Group g : allGroups) {
			Iterator<Group> it = allGroups.iterator();
			while(it.hasNext()){
				Group g = it.next();				assert(g != null);
				boolean f = false;
				if(!g.hosts.isEmpty()){
					for (Subscription s : StorageManager.peers.getAll()) {
						if (g.hosts.contains(s.getHost())) {
							f = true;
							break;
						}
					}
					if (!f){ //group hosts do not have itself in peers.conf
						//Disabling
						it.remove();//allGroups.remove(g);
						allGroupNames.remove(g.getName());
						Log.get().log(Level.WARNING,
								"Group {0} is disabled. Peers in groups.conf MUST have at least one in peers.conf: {1}",
								new Object[]{g.name, g.hosts});
					}
				}
			
		}
		
		
	}
	
	/**
	 * 
	 *
	 * @return List of all group names this server handles.
	 */
	public Set<String> getAllNames() {
		return Collections.unmodifiableSet(allGroupNames.keySet());
	}

	/**
	 * @return unmodifiable List
	 */
	public List<Group> getAll() {
		return Collections.unmodifiableList(allGroups);
	}

	/**
	 * Get Group for group name.
	 * 
	 * @param name
	 * @return Group or null
	 */
	public Group get(String name) {
		return allGroupNames.get(name);
	}

	/**
	 * Get group name for internal ID.
	 * 
	 * @param id
	 * @return Name or null
	 */
	public String getName(int id) {
		return allGroupId.get(id);
	}

	/**
	 * Get groups that have such host.
	 * For Subscription class. Condition n. 3.
	 * 
	 * return null if no group found, empty set is not allowed
	 * 
	 * @param host
	 * @return Group or null
	 */
	public Set<Group> groupsPerPeer(Subscription s) {
		return Collections.unmodifiableSet(groupsPerSubscription.get(s)) ;

	}

}
