package cz.xtf.openshift.storage;

import cz.xtf.openshift.OpenShiftAuxiliary;
import cz.xtf.openshift.builder.pod.PersistentVolumeClaim;

public abstract class DefaultStatefulAuxiliary implements OpenShiftAuxiliary {

	protected boolean isStateful = false;
	protected StoragePartition storagePartition;
	/**
	 * A PersistentVolumeClaim object used to claim a PersistentVolume within OpenShift.
	 * */
	protected final PersistentVolumeClaim persistentVolClaim;
	final protected String dataDir;
	final private String symbolicName;


	public DefaultStatefulAuxiliary(final String symbolicName, final String dataDir) {
		this(symbolicName, dataDir, null);
	}

	public DefaultStatefulAuxiliary(final String symbolicName, final String dataDir, final PersistentVolumeClaim pvc) {
		this.symbolicName = symbolicName.toLowerCase();
		this.dataDir = dataDir;
		this.persistentVolClaim = pvc;
	}

	public OpenShiftAuxiliary stateful(final int partition) {
		isStateful = true;
		storagePartition = new StoragePartition(partition, dataDir);
		return this;
	}
}