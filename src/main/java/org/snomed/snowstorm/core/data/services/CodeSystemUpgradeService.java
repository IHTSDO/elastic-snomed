package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.repositories.CodeSystemRepository;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemUpgradeJob;
import org.snomed.snowstorm.dailybuild.DailyBuildService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.snomed.snowstorm.core.data.services.BranchMetadataHelper.INTERNAL_METADATA_KEY;
import static org.snomed.snowstorm.core.data.services.BranchMetadataKeys.*;

@Service
public class CodeSystemUpgradeService {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private DailyBuildService dailyBuildService;

	@Autowired
	private IntegrityService integrityService;

	@Autowired
	private UpgradeInactivationService upgradeInactivationService;

	@Value("${snowstorm.rest-api.readonly}")
	private boolean isReadOnly;

	private final Map<String, CodeSystemUpgradeJob> upgradeJobMap;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public CodeSystemUpgradeService() {
		this.upgradeJobMap = new HashMap<>();
	}

	public String findRunningJob(String codeSystemShortname, Integer newDependantVersion) {
		for(String key : upgradeJobMap.keySet()) {
			CodeSystemUpgradeJob job = upgradeJobMap.get(key);
			if (job.getCodeSystemShortname().equalsIgnoreCase(codeSystemShortname)
				&& job.getNewDependantVersion().compareTo(newDependantVersion) == 0
				&& CodeSystemUpgradeJob.UpgradeStatus.RUNNING.equals(job.getStatus())) {
				return key;
			}
		}
		return null;
	}

	public String createJob(String codeSystemShortname, Integer newDependantVersion) {
		String id = UUID.randomUUID().toString();
		upgradeJobMap.put(id, new CodeSystemUpgradeJob(codeSystemShortname, newDependantVersion));
		return id;
	}

	public CodeSystemUpgradeJob getJob(String jobId) {
		return upgradeJobMap.get(jobId);
	}

	public CodeSystemUpgradeJob getUpgradeJobOrThrow(String jobId) {
		CodeSystemUpgradeJob job = getJob(jobId);
		if (job == null) {
			throw new NotFoundException("Upgrade job not found.");
		}
		return job;
	}

	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath)")
	@Async
	public void upgradeAsync(String id, CodeSystem codeSystem, Integer newDependantVersion, boolean contentAutomations) throws  ServiceException {
		upgrade(id, codeSystem, newDependantVersion, contentAutomations);
	}

	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath)")
	public void upgrade(String id, CodeSystem codeSystem, Integer newDependantVersion, boolean contentAutomations) throws  ServiceException {
		CodeSystemUpgradeJob job = null;
		if (id != null) {
			job = getJob(id);
		}
		// Pre checks
		String branchPath = codeSystem.getBranchPath();
		String parentPath = PathUtil.getParentPath(branchPath);
		if (parentPath == null) {
			String errorMessage = "The root Code System can not be upgraded.";
			if (job != null) {
				job.setStatus(CodeSystemUpgradeJob.UpgradeStatus.FAILED);
				job.setErrorMessage(errorMessage);
			}
			throw new IllegalArgumentException(errorMessage);
		}
		CodeSystem parentCodeSystem = codeSystemService.findOneByBranchPath(parentPath);
		if (parentCodeSystem == null) {
			String errorMessage = String.format("The Code System to be upgraded must be on a branch which is the direct child of another Code System. " +
					"There is no Code System on parent branch '%s'.", parentPath);
			if (job != null) {
				job.setStatus(CodeSystemUpgradeJob.UpgradeStatus.FAILED);
				job.setErrorMessage(errorMessage);
			}
			throw new IllegalStateException(errorMessage);
		}
		CodeSystemVersion newParentVersion = codeSystemService.findVersion(parentCodeSystem.getShortName(), newDependantVersion);
		if (newParentVersion == null) {
			String errorMessage = String.format("Parent Code System %s has no version with effectiveTime '%s'.", parentCodeSystem.getShortName(), newDependantVersion);
			if (job != null) {
				job.setStatus(CodeSystemUpgradeJob.UpgradeStatus.FAILED);
				job.setErrorMessage(errorMessage);
			}
			throw new IllegalArgumentException(errorMessage);
		}
		// Checks complete

		// Disable daily build during upgrade
		boolean dailyBuildAvailable = codeSystem.isDailyBuildAvailable();
		if (dailyBuildAvailable) {
			logger.info("Disabling daily build before upgrade.");
			codeSystem.setDailyBuildAvailable(false);
			codeSystemRepository.save(codeSystem);

			// Rollback daily build content
			logger.info("Rolling back any daily build content before upgrade.");
			dailyBuildService.rollbackDailyBuildContent(codeSystem);
		}
		try {
			Branch newParentVersionBranch = branchService.findLatest(newParentVersion.getBranchPath());
			Date newParentBaseTimepoint = newParentVersionBranch.getBase();
			logger.info("Running upgrade of {} to {} version {}.", codeSystem, parentCodeSystem, newDependantVersion);
			branchMergeService.rebaseToSpecificTimepointAndRemoveDuplicateContent(parentPath, newParentBaseTimepoint, branchPath, String.format("Upgrading extension to %s@%s.", parentPath, newParentVersion.getVersion()));
			logger.info("Completed upgrade of {} to {} version {}.", codeSystem, parentCodeSystem, newDependantVersion);

			if (contentAutomations) {
				logger.info("Running upgrade content automations.");
				upgradeInactivationService.findAndUpdateDescriptionsInactivation(codeSystem);
				upgradeInactivationService.findAndUpdateLanguageRefsets(codeSystem);
				upgradeInactivationService.findAndUpdateAdditionalAxioms(codeSystem);
				logger.info("Completed upgrade content automations.");
			}

			logger.info("Running integrity check on {}", branchPath);
			Branch extensionBranch = branchService.findLatest(branchPath);
			IntegrityIssueReport integrityReport = integrityService.findChangedComponentsWithBadIntegrityNotFixed(extensionBranch);
			logger.info("Completed integrity check on {}", branchPath);

			updateBranchMetaData(branchPath, newParentVersion, extensionBranch, integrityReport.isEmpty());
			logger.info("Upgrade completed on {}", branchPath);
			if (job != null) {
				job.setStatus(CodeSystemUpgradeJob.UpgradeStatus.COMPLETED);
			}
		} catch (ServiceException e) {
			if (job != null) {
				job.setStatus(CodeSystemUpgradeJob.UpgradeStatus.FAILED);
				job.setErrorMessage(e.getMessage());
			}
			throw e;
		} finally {
			// Re-enable daily build
			if (dailyBuildAvailable) {
				logger.info("Re-enabling daily build after upgrade.");
				codeSystem.setDailyBuildAvailable(true);
				codeSystemRepository.save(codeSystem);
			}
		}
	}

	private void updateBranchMetaData(String branchPath, CodeSystemVersion newParentVersion, Branch extensionBranch, boolean isReportEmpty) {
		final Metadata metadata = extensionBranch.getMetadata();
		
		//Store the current dependency package, to move to the previous one once updated.
		final String previousDependencyPackage = metadata.getString(DEPENDENCY_PACKAGE);

		if (newParentVersion.getReleasePackage() != null) {
			metadata.putString(DEPENDENCY_PACKAGE, newParentVersion.getReleasePackage());
			metadata.putString(PREVIOUS_DEPENDENCY_PACKAGE, previousDependencyPackage);
		} else {
			logger.error("No release package is set for version {}", newParentVersion);
		}
		metadata.putString(DEPENDENCY_RELEASE, String.valueOf(newParentVersion.getEffectiveDate()));
		if (!isReportEmpty) {
			logger.warn("Bad integrity found on {}", branchPath);
			metadata.getMapOrCreate(INTERNAL_METADATA_KEY).put(IntegrityService.INTEGRITY_ISSUE_METADATA_KEY, "true");
		} else {
			logger.info("No issues found in the integrity issue report.");
		}

		// update branch metadata
		branchService.updateMetadata(branchPath, metadata);
	}
}
