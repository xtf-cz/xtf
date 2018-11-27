package cz.xtf.builder.db;

import cz.xtf.builder.builders.pod.PersistentVolumeClaim;

public abstract class DefaultStatefulAuxiliary implements OpenShiftAuxiliary {
	protected final PersistentVolumeClaim persistentVolClaim;
	protected final String dataDir;
	private final String symbolicName;

	protected StoragePartition storagePartition;
	protected boolean isStateful = false;

	public DefaultStatefulAuxiliary(String symbolicName, String dataDir) {
		this(symbolicName, dataDir, null);
	}

	public DefaultStatefulAuxiliary(String symbolicName, String dataDir, PersistentVolumeClaim pvc) {
		this.symbolicName = symbolicName.toLowerCase();
		this.dataDir = dataDir;
		this.persistentVolClaim = pvc;
	}

	public OpenShiftAuxiliary stateful(int partition) {
		isStateful = true;
		storagePartition = new StoragePartition(partition, dataDir);
		return this;
	}
}