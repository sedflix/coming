package fr.inria.coming.core.engine.git;

import java.io.File;

import fr.inria.coming.core.entities.interfaces.Commit;
import fr.inria.coming.core.entities.interfaces.FileCommit;
import fr.inria.coming.main.ComingProperties;

public class FileCommitGit implements FileCommit {

	private Commit commit;
	private String previousFileName;
	private String nextFileName;
	private String previousVersion;
	private String nextVersion;
	private String previousCommitName;

	public FileCommitGit(String previousFileName, String previousVersion, String nextFileName, String nextVersion,
			Commit commit) {
		this.commit = commit;
		this.previousVersion = previousVersion;
		this.nextVersion = nextVersion;
		this.previousFileName = previousFileName;
		this.nextFileName = nextFileName;
	}

	public FileCommitGit(String previousFileName, String previousVersion, String nextFileName, String nextVersion,
			Commit commit, String previousCommitName) {
		this.commit = commit;
		this.previousVersion = previousVersion;
		this.nextVersion = nextVersion;
		this.previousFileName = previousFileName;
		this.nextFileName = nextFileName;
		this.previousCommitName = previousCommitName;
	}

	@Override
	public Commit getCommit() {
		return this.commit;
	}

	@Override
	public String getFileName() {
		if (ComingProperties.getPropertyBoolean("file_complete_name")) {
			return this.nextFileName;
		} else {
			int idx = this.nextFileName.lastIndexOf(File.separator);
			return this.nextFileName.substring(idx + 1);
		}
	}

	@Override
	public String getPreviousFileName() {
		return this.previousFileName;
	}

	@Override
	public String getNextFileName() {
		return this.nextFileName;
	}

	@Override
	public String getNextVersion() {
		return this.nextVersion;
	}

	@Override
	public String getPreviousVersion() {
		return this.previousVersion;
	}

	@Override
	public String getCompletePath() {
		return this.getNextFileName();
	}

	public String getPreviousCommitName() {
		return previousCommitName;
	}

	public String toString() {
		return this.nextFileName;
	}

	@Override
	public String getName() {
		return this.getFileName();
	}

	@Override
	public String getPreviousName() {

		return getPreviousFileName();
	}
}
